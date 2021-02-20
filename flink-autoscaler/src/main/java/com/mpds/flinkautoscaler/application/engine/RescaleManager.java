package com.mpds.flinkautoscaler.application.engine;

import com.mpds.flinkautoscaler.domain.model.Metric;
import com.mpds.flinkautoscaler.domain.model.events.DomainEvent;
import com.mpds.flinkautoscaler.domain.model.events.MetricReported;
import com.mpds.flinkautoscaler.infrastructure.config.KafkaPredictionConsumerProps;
import com.mpds.flinkautoscaler.infrastructure.config.KafkaProducerProps;
import com.mpds.flinkautoscaler.port.adapter.kafka.DomainEventSerDes;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.cloud.stream.binder.kafka.properties.KafkaConsumerProperties;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class RescaleManager {

    //TODO implement logic for determining rescale
    // Accepts Metric Report object
    // Activates potential Flink API calls


        private final KafkaPredictionConsumerProps kafkaConsumerProps;

        private final KafkaReceiver<String, DomainEvent> reciever;

        private final SimpleDateFormat dateFormat;

        public RescaleManager(KafkaPredictionConsumerProps kafkaConsumerProps) {
            this.kafkaConsumerProps = kafkaConsumerProps;

            String BOOTSTRAP_SERVERS = this.kafkaConsumerProps.getBootstrapServer();
            String CLIENT_ID_CONFIG = this.kafkaConsumerProps.getClientIdConfig();
            String ACK_CONFIG = this.kafkaConsumerProps.getAcksConfig();

            Map<String, Object> props = new HashMap<>();
            props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, BOOTSTRAP_SERVERS);
            props.put(ProducerConfig.CLIENT_ID_CONFIG, CLIENT_ID_CONFIG);
            props.put(ProducerConfig.ACKS_CONFIG, ACK_CONFIG);
            props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
            props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, DomainEventSerDes.class);
            ReceiverOptions<String, DomainEvent> receiverOptions = ReceiverOptions.create(props);

            reciever = KafkaReceiver.create(receiverOptions);
            dateFormat = new SimpleDateFormat("HH:mm:ss:SSS z dd MMM yyyy");
        }


        public void evaluate(MetricReported metrics) {


            log.info("Evaluate");
            if (metrics.getCpuUtilization() > 0.6 || metrics.getMemoryUsage() > 0.9 || metrics.getKafkaMaxMessageLatency() > 4.0) {
                log.info("Scale Up");
                // Scale Up
            }
            if (metrics.getCpuUtilization() < 0.4 && metrics.getMemoryUsage() < 0.5 && metrics.getKafkaMaxMessageLatency() < 1.0) {
                log.info("Scale Down");
                // Scale Down
            }

        }

}
