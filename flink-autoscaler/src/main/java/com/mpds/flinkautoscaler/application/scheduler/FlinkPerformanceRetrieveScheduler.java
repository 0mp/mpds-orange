package com.mpds.flinkautoscaler.application.scheduler;

import com.mpds.flinkautoscaler.application.service.FlinkApiService;
import com.mpds.flinkautoscaler.application.service.PredictionCacheService;
import com.mpds.flinkautoscaler.application.service.PrometheusApiService;
import com.mpds.flinkautoscaler.domain.model.MetricTriggerPredictionsSnapshot;
import com.mpds.flinkautoscaler.domain.model.PrometheusMetric;
import com.mpds.flinkautoscaler.infrastructure.config.PrometheusProps;
import com.mpds.flinkautoscaler.infrastructure.repository.ClusterPerformanceBenchmarkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Component
@Slf4j
public class FlinkPerformanceRetrieveScheduler {

    private final PredictionCacheService predictionCacheService;

    private final ClusterPerformanceBenchmarkRepository clusterPerformanceBenchmarkRepository;

    private final WebClient prometheusWebClient;

    private final PrometheusProps prometheusProps;

    private final PrometheusApiService prometheusApiService;

    private final FlinkApiService flinkApiService;

    public FlinkPerformanceRetrieveScheduler(PredictionCacheService predictionCacheService, ClusterPerformanceBenchmarkRepository clusterPerformanceBenchmarkRepository, WebClient prometheusWebClient, PrometheusProps prometheusProps, PrometheusApiService prometheusApiService, FlinkApiService flinkApiService) {
        this.predictionCacheService = predictionCacheService;
        this.clusterPerformanceBenchmarkRepository = clusterPerformanceBenchmarkRepository;
        this.prometheusWebClient = prometheusWebClient;
//        this.flinkProps = flinkProps;
        this.prometheusProps = prometheusProps;
        this.prometheusApiService = prometheusApiService;
        this.flinkApiService = flinkApiService;
    }

//    @Scheduled(fixedDelay = 15000)
    public void scheduleFlinkPerformanceRetrieval() {
        LocalDateTime currentDateTime = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        String currentDateTimeString = currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
        log.info("<<< Start creating Flink cluster performance snapshot:" + currentDateTimeString + " >>>");

        MetricTriggerPredictionsSnapshot metricTriggerPredictionsSnapshot = this.predictionCacheService.getMetricTriggerPredictionsSnapshot(MetricTriggerPredictionsSnapshot.class.getSimpleName());

        Mono<Integer> currentFlinkClusterParallelism = this.flinkApiService.getCurrentFlinkClusterParallelism();
        Mono<PrometheusMetric> prometheusMetric = this.prometheusApiService.getPrometheusMetric(this.prometheusApiService.getFlinkNumRecordsOutPerSecond(currentDateTimeString));

        Mono.zip(currentFlinkClusterParallelism, prometheusMetric)
                .flatMap(tuple -> {
                    Integer currentParallelism = tuple.getT1();
                    PrometheusMetric flinkMetric = tuple.getT2();

                    float flinkNumRecordsOutPerSecond = Float.parseFloat(flinkMetric.getData().getResult().get(0).getValue()[1].toString());

                    log.info("<<<<<<<---------->>>>>>>  EVALUATION RESULTS  <<<<<<<---------->>>>>>>");
                    log.info("Evaluation at: " + currentDateTimeString);
                    log.info("Current Flink parallelism of the cluster: " + currentParallelism);
                    log.info("Current Flink Metric from Prometheus: " + flinkMetric.toString());
                    log.info("Current Flink numRecordsOutPerSecond: " + flinkNumRecordsOutPerSecond);
                    if (metricTriggerPredictionsSnapshot != null) {
                        log.info("FlinkJob ID: " + metricTriggerPredictionsSnapshot.getJobId());
                        log.info("Snapshot from:" + metricTriggerPredictionsSnapshot.getSnapshotTime());
                        log.info("Based on Trigger: " + metricTriggerPredictionsSnapshot.getMetricTrigger().toString());
                        log.info("Based on ST Prediction: " + metricTriggerPredictionsSnapshot.getShorttermPrediction().toString());
                        log.info("Based on LT Prediction: " + metricTriggerPredictionsSnapshot.getLongtermPredictionReported().toString());
                        if (currentParallelism != metricTriggerPredictionsSnapshot.getTargetParallelism())
                            log.error("Current parallelism is" + currentParallelism + ", but target parallelism was " + metricTriggerPredictionsSnapshot.getTargetParallelism());
                    }
                    log.info("<<<<<<<---------->>>>>>>  EVALUATION RESULTS  <<<<<<<---------->>>>>>>");

                    return Mono.empty();
//                    ClusterPerformanceBenchmark clusterPerformanceBenchmark = ClusterPerformanceBenchmark.builder()
//                            .parallelism(currentParallelism)
//                            .createdDate(currentDateTime)
//                            .numTaskmanagerPods(currentParallelism)
//                            .maxRate((int) metricTriggerPredictionsSnapshot.getMetricTrigger().getKafkaMessagesPerSecond())
//                            .build();
//
//                    // Insert only a new entry only if there is not an entry for the current cluster performance yet
//                    return this.clusterPerformanceBenchmarkRepository.findFirstByParallelism(currentParallelism)
//                            .switchIfEmpty(this.clusterPerformanceBenchmarkRepository.save(clusterPerformanceBenchmark));

                }).subscribe();
    }
}
