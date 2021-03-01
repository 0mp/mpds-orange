package com.mpds.flinkautoscaler.application.scheduler;

import com.mpds.flinkautoscaler.application.service.PrometheusApiService;
import com.mpds.flinkautoscaler.domain.model.PrometheusMetric;
import com.mpds.flinkautoscaler.domain.model.Result;
import com.mpds.flinkautoscaler.domain.model.events.DomainEvent;
import com.mpds.flinkautoscaler.domain.model.events.MetricReported;
import com.mpds.flinkautoscaler.infrastructure.config.PrometheusProps;
import com.mpds.flinkautoscaler.port.adapter.kafka.DomainEventPublisherReactive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
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
//    private final WebClient webClient;

    private final PrometheusProps prometheusProps;

    private final DomainEventPublisherReactive domainEventPublisherReactive;

    private final PrometheusApiService prometheusApiService;




    // Every 5 seconds
//    @Scheduled(fixedDelay = 5000)
    // Every 10 seconds
    @Scheduled(fixedDelay = 20000)
    public void scheduleMetricRetrieval() {
        log.info("Start retrieving metrics...");

        allPrometheusRequests().flatMap(this.domainEventPublisherReactive::sendMessages).subscribe();

    }

    private Mono<DomainEvent> allPrometheusRequests() {
        LocalDateTime currentDateTime = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        String currentDateTimeString = currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));

        Mono<PrometheusMetric> kafkaLoadMsg = prometheusApiService.getPrometheusMetric(prometheusApiService.getKafkaMessagesPerSecond(currentDateTimeString)).subscribeOn(Schedulers.boundedElastic());
        Mono<PrometheusMetric> kafkaLagMsg = prometheusApiService.getPrometheusMetric(prometheusApiService.getKafkaLag(currentDateTimeString)).subscribeOn(Schedulers.boundedElastic());
        Mono<PrometheusMetric> cpuMsg = prometheusApiService.getPrometheusMetric(prometheusApiService.getCpuUsage(currentDateTimeString)).subscribeOn(Schedulers.boundedElastic());
        Mono<PrometheusMetric> maxJobLatencyMsg = prometheusApiService.getPrometheusMetric(prometheusApiService.getMaxJobLatency(currentDateTimeString)).subscribeOn(Schedulers.boundedElastic());
        Mono<PrometheusMetric> memMsg = prometheusApiService.getPrometheusMetric(prometheusApiService.getMemUsage(currentDateTimeString)).subscribeOn(Schedulers.boundedElastic());


        return Mono.zip(cpuMsg, kafkaLagMsg, kafkaLoadMsg, maxJobLatencyMsg, memMsg).map(tuple -> {
            float cpu=0.0f;
            if(tuple.getT1().getData().getResult().size()>0) {
                cpu = Float.parseFloat(tuple.getT1().getData().getResult().get(0).getValue()[1].toString());
                log.info("current cpu: " + cpu);
            }

            float kafkaLag=0.0f;
            if(tuple.getT2().getData().getResult().size()>0) {
                kafkaLag = Float.parseFloat(tuple.getT2().getData().getResult().get(0).getValue()[1].toString());
                log.info("current lag: " + kafkaLag);
            }
            float kafkaLoad=0.0f;
            if(tuple.getT3().getData().getResult().size()>0) {
//                kafkaLoad = Float.parseFloat(tuple.getT3().getData().getResult().get(0).getValue()[1].toString());

                for(Result result : tuple.getT3().getData().getResult()) {
                    if(this.prometheusProps.getSourceTopic().equalsIgnoreCase(result.getMetric().getTopic())) {
                        kafkaLoad = Float.parseFloat(result.getValue()[1].toString());
                        log.info("current load: " + kafkaLoad);
                    }
                }
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

}
