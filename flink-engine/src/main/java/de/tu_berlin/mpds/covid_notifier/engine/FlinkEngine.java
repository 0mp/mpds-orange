package de.tu_berlin.mpds.covid_notifier.engine;


import com.fasterxml.jackson.databind.ObjectMapper;
import de.tu_berlin.mpds.covid_notifier.model.DomainEvent;
import de.tu_berlin.mpds.covid_notifier.model.DomainEventSchema;
import de.tu_berlin.mpds.covid_notifier.model.InfectionReported;
import de.tu_berlin.mpds.covid_notifier.model.PersonContact;
import org.apache.flink.api.common.functions.RichFlatMapFunction;
import org.apache.flink.api.common.restartstrategy.RestartStrategies;
import org.apache.flink.api.common.serialization.SimpleStringSchema;
import org.apache.flink.api.common.time.Time;
import org.apache.flink.api.java.utils.MultipleParameterTool;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.metrics.Counter;
import org.apache.flink.runtime.state.filesystem.FsStateBackend;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.CheckpointConfig;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.ProcessFunction;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaConsumer;
import org.apache.flink.streaming.connectors.kafka.FlinkKafkaProducer;
import org.apache.flink.util.Collector;
import org.apache.flink.util.OutputTag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.TimeUnit;


@Service
public class FlinkEngine {

    private static final Logger LOG = LoggerFactory.getLogger(FlinkEngine.class);

    private static final ObjectMapper objectMapper = new ObjectMapper();


    @Autowired
    private StreamExecutionEnvironment env;

    @Autowired
    private Properties properties;

    private String checkpointPath;

    private long checkpointInterval;

    private Boolean defaultStateBackend;

    final OutputTag<InfectionReported> outputTag = new OutputTag<InfectionReported>("InfectionReported") {
    };

    /**
     * StreamExecutionEnvironment
     *
     * @return StreamExecutionEnvironment
     * Initialize streaming environment config:
     * - Fail Rate policy
     * - HDFS Checkpointing
     * - Disabling Chain Operations
     */
    @Bean
    public StreamExecutionEnvironment env(ApplicationArguments progArgs) {
        LOG.info("--PROVIDED ARGS: " + Arrays.toString(progArgs.getSourceArgs()));
        final MultipleParameterTool params = MultipleParameterTool.fromArgs(progArgs.getSourceArgs());

        if (params.has("checkpoint")) {
            LOG.info("Provided checkpoint path for Flink: " + params.get("checkpoint"));
            this.checkpointPath = params.get("checkpoint");
        }

        if (params.has("checkpoint.interval")) {
            LOG.info("Provided checkpoint interval for Flink: " + params.get("checkpoint.interval"));
            this.checkpointInterval = Long.parseLong(params.get("checkpoint.interval"));
        }

        if (params.has("statebackend.default")) {
            LOG.info("Default state backend: " + params.get("statebackend.default"));
            this.defaultStateBackend = Boolean.valueOf(params.get("statebackend.default"));
        }

        env = StreamExecutionEnvironment.getExecutionEnvironment();
        // make parameters available in the web interface
        env.getConfig().setGlobalJobParameters(params);

        env.disableOperatorChaining();
//                1000000000L
        env.enableCheckpointing(checkpointInterval);
        CheckpointConfig config = env.getCheckpointConfig();
        config.enableExternalizedCheckpoints(CheckpointConfig.ExternalizedCheckpointCleanup.RETAIN_ON_CANCELLATION);
        env.setRestartStrategy(RestartStrategies.failureRateRestart(
                3, // max failures per interval
                Time.of(5, TimeUnit.MINUTES), //time interval for measuring failure rate
                Time.of(10, TimeUnit.SECONDS) // delay
        ));

        if(!this.defaultStateBackend) {
            LOG.info("Setting FsStateBackend...");
            env.setStateBackend(new FsStateBackend(this.checkpointPath, true));
        }

        return env;
    }

    /**
     * Properties
     *
     * @return properties
     * Kafka Configuration
     */
    @Bean
    public Properties Properties() {
        properties.setProperty("bootstrap.servers", "kafka.default.svc.cluster.local:9092");
        properties.setProperty("group.id", "covidAnalyser");
        return properties;
    }

    public static class InfectionRedisMapper extends RichFlatMapFunction<InfectionReported, String> {

        private transient Counter infectionCounter;
        private transient Counter highRiskCounter;

        @Override
        public void open(Configuration config) {
            this.highRiskCounter = getRuntimeContext()
                    .getMetricGroup()
                    .counter("highRiskCounter");
            this.infectionCounter = getRuntimeContext()
                    .getMetricGroup()
                    .counter("infectionCounter");
        }

        /**
         * Infection Mapper
         *
         * @param data
         * @return String
         * <p>
         * Request contacts from Redis of set of contacts for personId from param
         */
        @Override
        public void flatMap(InfectionReported data, Collector<String> out) throws Exception {
            try {
                infectionCounter.inc();
                Set<String> contacts = RedisReadContacts.getContacts(Long.toString(data.getPersonId()));
                if (contacts != null) {
                    contacts.stream().forEach(contact -> {
                        highRiskCounter.inc();
                        out.collect(contact);
                    });
                }
            } catch (Exception e) {
                System.out.println("Infection exception reading data: " + data);
                e.printStackTrace();
                System.exit(0);
            }
        }
    }


    public static class DomainEventSplitter extends ProcessFunction<DomainEvent, PersonContact> {

        final OutputTag<InfectionReported> outputTag = new OutputTag<InfectionReported>("InfectionReported") {
        };

        /**
         * DomainEventSplitter
         *
         * @param data
         * @return PersonContact
         * <p>
         * Gathers PersonContact events into outgoing collector, places InfectionReported in context with tag
         */

        @Override
        public void processElement(DomainEvent data, Context ctx, Collector<PersonContact> out) throws Exception {

            // emit Contacts to regular output
            if (data instanceof PersonContact) {
                out.collect((PersonContact) data);
            }
            // emit Infections to side output
            if (data instanceof InfectionReported) {
                ctx.output(this.outputTag, (InfectionReported) data);
            }
        }
    }

    /**
     * highRiskContactProducer
     *
     * @throws Exception 1. Initialize Kafka Consumer to topic 'covid' from earliest start. Desierialize to DomainEvent
     *                   2. Add Kafka Consumer as environment source
     *                   3. Split the Stream into PersonContact and InfectionReported streams
     *                   4. Process Contact Stream: write to Redis
     *                   5. Process Infection Stream: Request Contact set from Redis, sink to Kafka topic 'highrisk'
     */
    @Bean
    public void highRiskContactProducer() throws Exception {

        // 1. Initialize Kafka Consumer to topic 'covid' from earliest start. Desierialize to DomainEvent
        FlinkKafkaConsumer<DomainEvent> covidSource = new FlinkKafkaConsumer<>(
                "covid",
                new DomainEventSchema(),
                properties);
        // covidSource.setStartFromEarliest();

        // 2. Add Kafka Consumer as environment source
        DataStream<DomainEvent> covidStream = env.addSource(covidSource)
                .name("Covid Data");

        // 3. Split the Stream into PersonContact and InfectionReported streams
        SingleOutputStreamOperator<PersonContact> contactStream = covidStream
                .process(new DomainEventSplitter());

        DataStream<InfectionReported> infectionsStream = contactStream
                .getSideOutput(outputTag);

        // 4. Process Contact Stream: write to Redis
        contactStream
                .map(data -> {
                    return RedisWriteContacts.writeContact(
                            Long.toString(data.getPerson1()),
                            Long.toString(data.getPerson2()));
                })
                .name("Contacts");

        // 5. Process Infection Stream: Request Contact set from Redis, sink to Kafka topic 'highrisk'
        infectionsStream
                .map(data -> {
                    return Long.toString(data.getPersonId());
                })
                .addSink(new FlinkKafkaProducer<String>(
                        "highrisk", new SimpleStringSchema(), properties
                ))
                .name("HighRisk");

        // Execute
        env.execute("Covid Engine");
    }

}
