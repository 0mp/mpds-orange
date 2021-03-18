package com.mpds.flinkautoscaler.application.scheduler;

import com.mpds.flinkautoscaler.application.service.CacheService;
import com.mpds.flinkautoscaler.application.service.PrometheusApiService;
import com.mpds.flinkautoscaler.domain.model.ClusterPerformanceBenchmark;
import com.mpds.flinkautoscaler.domain.model.MetricTriggerPredictionsSnapshot;
import com.mpds.flinkautoscaler.domain.model.PrometheusMetric;
import com.mpds.flinkautoscaler.domain.model.Result;
import com.mpds.flinkautoscaler.infrastructure.config.PrometheusProps;
import com.mpds.flinkautoscaler.infrastructure.repository.ClusterPerformanceBenchmarkRepository;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class FlinkPerformanceRetrieveScheduler {

    private final CacheService cacheService;

    private final ClusterPerformanceBenchmarkRepository clusterPerformanceBenchmarkRepository;

    private final PrometheusProps prometheusProps;

    private final PrometheusApiService prometheusApiService;

    // Flag is changed when the first rescale has been carried out
    @Getter
    @Setter
    private Boolean alwaysInsertClusterPerformanceToDB = true;

    public FlinkPerformanceRetrieveScheduler(CacheService cacheService, ClusterPerformanceBenchmarkRepository clusterPerformanceBenchmarkRepository, PrometheusProps prometheusProps, PrometheusApiService prometheusApiService) {
        this.cacheService = cacheService;
        this.clusterPerformanceBenchmarkRepository = clusterPerformanceBenchmarkRepository;
        this.prometheusProps = prometheusProps;
        this.prometheusApiService = prometheusApiService;
    }

    //    @Scheduled(fixedDelay = 15000)
    @Scheduled(fixedDelay = 2000)
    public void scheduleFlinkPerformanceRetrieval() {
        LocalDateTime currentDateTime = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        String currentDateTimeString = currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
        log.info("<<< Start creating Flink cluster performance snapshot:" + currentDateTimeString + " >>>");

        MetricTriggerPredictionsSnapshot metricTriggerPredictionsSnapshot = this.cacheService.getMetricTriggerPredictionsSnapshot("MetricTriggerPredictionsSnapshot");

        Mono<Integer> currentFlinkClusterParallelism = this.prometheusApiService.getPrometheusMetric(this.prometheusApiService.getFlinkNumOfTaskManagers(currentDateTimeString))
                .map(prometheusMetric -> {
                    log.debug("[FlinkPerformanceRetrieveScheduler] Prometheus number of task managers response at <" + currentDateTimeString + "> : " + prometheusMetric.toString());
                    if (prometheusMetric.getData().getResult() != null && prometheusMetric.getData().getResult().size() > 0) {
                        return Integer.parseInt(prometheusMetric.getData().getResult().get(0).getValue()[1].toString());
                    }
                    log.error("No Flink task managers were active reported from Prometheus at: " + currentDateTimeString);
                    return 0;
                });

        Mono<PrometheusMetric> prometheusMetric = this.prometheusApiService.getPrometheusMetric(this.prometheusApiService.getFlinkNumRecordsIn(currentDateTimeString));

        Mono<PrometheusMetric> kafkaLoadMsg = prometheusApiService.getPrometheusMetric(prometheusApiService.getKafkaMessagesPerSecond(currentDateTimeString));

        Mono.zip(currentFlinkClusterParallelism, prometheusMetric, kafkaLoadMsg)
                .flatMap(tuple -> {
                    int currentParallelism = tuple.getT1();
                    PrometheusMetric flinkMetric = tuple.getT2();

                    float kafkaLoad = 0.0f;
                    if (tuple.getT3().getData().getResult().size() > 0) {
                        for (Result result : tuple.getT3().getData().getResult()) {
                            if (this.prometheusProps.getSourceTopic().equalsIgnoreCase(result.getMetric().getTopic())) {
                                kafkaLoad = Float.parseFloat(result.getValue()[1].toString());
                            }
                        }
                    }
                    float flinkNumRecordsInPerSecond = 0.0f;
                    if (flinkMetric.getData().getResult() != null && flinkMetric.getData().getResult().size() > 0) {
                        flinkNumRecordsInPerSecond = Float.parseFloat(flinkMetric.getData().getResult().get(0).getValue()[1].toString());
                    }

                    log.info("<<<<<<<---------->>>>>>> [START] *** EVALUATION RESULTS *** [START]  <<<<<<<---------->>>>>>>---<<<<<<<---------->>>>>>>---<<<<<<<---------->>>>>>>---<<<<<<<---------->>>>>>>---<<<<<<---------->>>>>>>---<<<<<<<---------->>>>>>>---<<<<<<<---------->>>>>>>---<<<<<<<---------->>>>>>>---<<<<<<<---------->>>>>>>---<<<<<<<---------->>>>>>>---<<<<<<<---------->>>>>>>");
                    log.info("Evaluation at: " + currentDateTimeString);
                    log.info("Flink parallelism of the cluster: " + currentParallelism);
                    log.info("Kafka Load: " + kafkaLoad);
                    log.info("Flink flinkNumRecordsInPerSecond: " + flinkNumRecordsInPerSecond);
                    if (metricTriggerPredictionsSnapshot != null) {
                        log.info("Flink Job ID: " + metricTriggerPredictionsSnapshot.getJobId());
                        log.info("Snapshot from: " + metricTriggerPredictionsSnapshot.getSnapshotTime().format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")));
                        log.info("Based on Trigger: " + metricTriggerPredictionsSnapshot.getMetricTrigger().toString());
                        log.info("Based on Aggregate Prediction: " + metricTriggerPredictionsSnapshot.getAggregatePrediction());
                        if (metricTriggerPredictionsSnapshot.getShorttermPrediction() != null)
                            log.info("Based on ST Prediction: " + metricTriggerPredictionsSnapshot.getShorttermPrediction().toString());
                        if (metricTriggerPredictionsSnapshot.getLongtermPredictionReported() != null)
                            log.info("Based on LT Prediction: " + metricTriggerPredictionsSnapshot.getLongtermPredictionReported().getClosestPrediction(currentDateTime));
                        if (currentParallelism != metricTriggerPredictionsSnapshot.getTargetParallelism())
                            log.warn("Current parallelism is " + currentParallelism + ", but target parallelism was " + metricTriggerPredictionsSnapshot.getTargetParallelism());
                    }
                    log.info("<<<<<<<---------->>>>>>> [ENDING] *** EVALUATION RESULTS *** [ENDING] <<<<<<<---------->>>>>>>---<<<<<<<---------->>>>>>>---<<<<<<<---------->>>>>>>---<<<<<<<---------->>>>>>>---<<<<<<<---------->>>>>>>---<<<<<<<---------->>>>>>>---<<<<<<<---------->>>>>>>---<<<<<<<---------->>>>>>>---<<<<<<<---------->>>>>>>---<<<<<<<---------->>>>>>>---<<<<<<<---------->>>>>>>");

                    ClusterPerformanceBenchmark clusterPerformanceBenchmark = ClusterPerformanceBenchmark.builder()
                            .parallelism(currentParallelism)
                            .createdDate(currentDateTime)
                            .numTaskmanagerPods(currentParallelism)
                            .maxRate((int) (flinkNumRecordsInPerSecond))
                            .build();

                    if ((alwaysInsertClusterPerformanceToDB)) {
                        return this.clusterPerformanceBenchmarkRepository.findFirstByParallelism(currentParallelism)
                                .flatMap(foundClusterPerformanceBenchmarkFromDB -> {
                                    clusterPerformanceBenchmark.setId(foundClusterPerformanceBenchmarkFromDB.getId());
                                    clusterPerformanceBenchmark.setCreatedAt(LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
                                    clusterPerformanceBenchmark.setRestartTime(foundClusterPerformanceBenchmarkFromDB.getRestartTime());
                                    if (clusterPerformanceBenchmark.getMaxRate() > foundClusterPerformanceBenchmarkFromDB.getMaxRate()) {
                                        return this.clusterPerformanceBenchmarkRepository.updateMaxRateForParallelism(clusterPerformanceBenchmark.getMaxRate(), clusterPerformanceBenchmark.getParallelism());
                                    }
                                    return Mono.just(clusterPerformanceBenchmark);
                                })
                                .switchIfEmpty(persistNewClusterPerformanceBenchmark(clusterPerformanceBenchmark));
                    } else if (metricTriggerPredictionsSnapshot != null && currentParallelism == metricTriggerPredictionsSnapshot.getTargetParallelism() && Duration.between(metricTriggerPredictionsSnapshot.getSnapshotTime(), LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC)).abs().toSeconds() > 30) {
                        return this.clusterPerformanceBenchmarkRepository.findFirstByParallelism(currentParallelism)
                                .flatMap(foundClusterPerformanceBenchmarkFromDB -> {
                                    if (clusterPerformanceBenchmark.getMaxRate() > foundClusterPerformanceBenchmarkFromDB.getMaxRate()) {
                                        return this.clusterPerformanceBenchmarkRepository.updateMaxRateForParallelism(clusterPerformanceBenchmark.getMaxRate(), foundClusterPerformanceBenchmarkFromDB.getParallelism())
                                                .flatMap(numUpdatedEntries -> {
                                                    if (numUpdatedEntries > 0) {
                                                        log.info("Updated record (" + numUpdatedEntries + ") for parallelism " + clusterPerformanceBenchmark.getParallelism() + "and max rate " + clusterPerformanceBenchmark.getMaxRate());
                                                    } else {
                                                        log.warn("No entries were updated in the performance table!");
                                                    }
                                                    return Mono.just(clusterPerformanceBenchmark);
                                                });
                                    }
                                    return Mono.just(clusterPerformanceBenchmark);
                                })
                                .switchIfEmpty(persistNewClusterPerformanceBenchmark(clusterPerformanceBenchmark));
                    }
                    return Mono.empty();
                }).subscribe();
    }

    private Mono<ClusterPerformanceBenchmark> persistNewClusterPerformanceBenchmark(ClusterPerformanceBenchmark clusterPerformanceBenchmark) {
        clusterPerformanceBenchmark.setNewEntry(true);
        return this.clusterPerformanceBenchmarkRepository.save(clusterPerformanceBenchmark);
    }
}
