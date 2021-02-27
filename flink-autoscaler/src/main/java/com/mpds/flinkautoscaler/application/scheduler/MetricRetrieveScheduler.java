package com.mpds.flinkautoscaler.application.scheduler;

import com.mpds.flinkautoscaler.domain.model.PrometheusMetric;
import com.mpds.flinkautoscaler.domain.model.events.DomainEvent;
import com.mpds.flinkautoscaler.domain.model.events.MetricReported;
import com.mpds.flinkautoscaler.infrastructure.config.PrometheusProps;
import com.mpds.flinkautoscaler.port.adapter.kafka.DomainEventPublisherReactive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class MetricRetrieveScheduler {

    // Used to query the metrics via REST
    private final WebClient webClient;

    private final PrometheusProps prometheusProps;

    private final DomainEventPublisherReactive domainEventPublisherReactive;

//    private static final String KAFKA_METRIC_TOPIC ="covid";

    // Every 5 seconds
//    @Scheduled(fixedDelay = 5000)
    @Scheduled(fixedDelay = 10000)
    public void scheduleMetricRetrieval() {
        log.info("Start retrieving metrics...");

        allPrometheusRequests().flatMap(this.domainEventPublisherReactive::sendMessages).subscribe();

    }

    private Mono<DomainEvent> allPrometheusRequests() {
        LocalDateTime currentDateTime = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        String currentDateTimeString = currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));

        Mono<PrometheusMetric> kafkaLoadMsg = getPrometheusMetric(getKafkaMessagesPerSecond(currentDateTimeString)).subscribeOn(Schedulers.boundedElastic());
        Mono<PrometheusMetric> kafkaLagMsg = getPrometheusMetric(getKafkaLag(currentDateTimeString)).subscribeOn(Schedulers.boundedElastic());
        Mono<PrometheusMetric> cpuMsg = getPrometheusMetric(getCpuUsage(currentDateTimeString)).subscribeOn(Schedulers.boundedElastic());
        Mono<PrometheusMetric> maxJobLatencyMsg = getPrometheusMetric(getMaxJobLatency(currentDateTimeString)).subscribeOn(Schedulers.boundedElastic());
        Mono<PrometheusMetric> memMsg = getPrometheusMetric(getMemUsage(currentDateTimeString)).subscribeOn(Schedulers.boundedElastic());


        return Mono.zip(cpuMsg, kafkaLagMsg, kafkaLoadMsg, maxJobLatencyMsg, memMsg).map(tuple -> {
            float cpu=0.0f;
            if(tuple.getT1().getData().getResult().size()>0) {
                cpu = Float.parseFloat(tuple.getT1().getData().getResult().get(0).getValue()[1].toString());
            }

            float kafkaLag=0.0f;
            if(tuple.getT2().getData().getResult().size()>0) {
                kafkaLag = Float.parseFloat(tuple.getT2().getData().getResult().get(0).getValue()[1].toString());
            }
            float kafkaLoad=0.0f;
            if(tuple.getT3().getData().getResult().size()>0) {
                kafkaLoad = Float.parseFloat(tuple.getT3().getData().getResult().get(0).getValue()[1].toString());
            }

            float maxJobLatency=0.0f;
            if(tuple.getT4().getData().getResult().size()>0) {
                maxJobLatency = Float.parseFloat(tuple.getT4().getData().getResult().get(0).getValue()[1].toString());
            }

            float mem=0.0f;
            if(tuple.getT5().getData().getResult().size()>0) {
                mem = Float.parseFloat(tuple.getT5().getData().getResult().get(0).getValue()[1].toString());
            }
            MetricReported domainEvent = new MetricReported(
                    UUID.randomUUID(),
                    kafkaLoad,
                    currentDateTime,
                    this.prometheusProps.getSourceTopic(),
                    "",
                    maxJobLatency,
                    0,
                    0,
                    0,
                    true,
                    cpu,
                    mem,
                    kafkaLag);
//            PredictionReported domainEvent = new PredictionReported(
//                    UUID.randomUUID().toString(),
//                    LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC),
//                    1,
//                    LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC).toString(),
//                    UUID.randomUUID().toString()
//                    );
            log.info(domainEvent.toString());

            return domainEvent;
        });

    }

    private Mono<PrometheusMetric> getPrometheusMetric(MultiValueMap<String, String> message) {

        return this.webClient.post()
                .uri(this.prometheusProps.getQueryUri())
                .body(BodyInserters.fromFormData(message))
                .retrieve()
                .bodyToMono(PrometheusMetric.class);
    }

    private MultiValueMap<String, String> getCpuUsage(String dateTime) {

        log.info("get CPU usage for dateTime: " + dateTime);
        LinkedMultiValueMap<String, String> lmvn = new LinkedMultiValueMap<>();
        final String PROMETHEUS_QUERY = "sum(flink_taskmanager_Status_JVM_CPU_Load) / sum(flink_jobmanager_numRegisteredTaskManagers)";
        lmvn.add("query", PROMETHEUS_QUERY);
        lmvn.add("time", dateTime);
        return lmvn;
    }

    private MultiValueMap<String, String> getKafkaLag(String dateTime) {

        log.info("get Kafka lag for dateTime: " + dateTime);
        LinkedMultiValueMap<String, String> lmvn = new LinkedMultiValueMap<>();
        final String PROMETHEUS_QUERY = "sum(flink_taskmanager_job_task_operator_KafkaConsumer_records_lag_max) / count(flink_taskmanager_job_task_operator_KafkaConsumer_records_lag_max)";
        lmvn.add("query", PROMETHEUS_QUERY);
        lmvn.add("time", dateTime);
        return lmvn;
    }

    private MultiValueMap<String, String> getMaxJobLatency(String dateTime) {

        log.info("get max job latency for dateTime: " + dateTime);
        LinkedMultiValueMap<String, String> lmvn = new LinkedMultiValueMap<>();
        final String PROMETHEUS_QUERY = "max(flink_taskmanager_job_latency_source_id_operator_id_operator_subtask_index_latency)";
        lmvn.add("query", PROMETHEUS_QUERY);
        lmvn.add("time", dateTime);
        return lmvn;
    }

    private MultiValueMap<String, String> getMemUsage(String dateTime) {

        log.info("get Memory usage for dateTime: " + dateTime);
        LinkedMultiValueMap<String, String> lmvn = new LinkedMultiValueMap<>();
        final String PROMETHEUS_QUERY = "sum(flink_taskmanager_Status_JVM_Memory_Heap_Used / flink_taskmanager_Status_JVM_Memory_Heap_Committed) / sum(flink_jobmanager_numRegisteredTaskManagers)";
        lmvn.add("query", PROMETHEUS_QUERY);
        lmvn.add("time", dateTime);
        return lmvn;
    }

    private MultiValueMap<String, String> getKafkaMessagesPerSecond(String dateTime) {

        log.info("getKafkaMessagesPerSecond for dateTime: " + dateTime);
        LinkedMultiValueMap<String, String> lmvn = new LinkedMultiValueMap<>();
        final String PROMETHEUS_QUERY = "sum by ("+this.prometheusProps.getSourceTopic()+") (rate(kafka_server_brokertopicmetrics_messagesinpersec_count[2m]))";
        lmvn.add("query", PROMETHEUS_QUERY);
        lmvn.add("time", dateTime);
        return lmvn;
    }
}
