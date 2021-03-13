package com.mpds.flinkautoscaler.application.scheduler;

import com.mpds.flinkautoscaler.application.service.FlinkApiService;
import com.mpds.flinkautoscaler.application.service.CacheService;
import com.mpds.flinkautoscaler.application.service.PrometheusApiService;
import com.mpds.flinkautoscaler.domain.model.ClusterPerformanceBenchmark;
import com.mpds.flinkautoscaler.domain.model.MetricTriggerPredictionsSnapshot;
import com.mpds.flinkautoscaler.domain.model.PrometheusMetric;
import com.mpds.flinkautoscaler.infrastructure.config.PrometheusProps;
import com.mpds.flinkautoscaler.infrastructure.repository.ClusterPerformanceBenchmarkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
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

    private final CacheService cacheService;

    private final ClusterPerformanceBenchmarkRepository clusterPerformanceBenchmarkRepository;

    private final WebClient prometheusWebClient;

    private final PrometheusProps prometheusProps;

    private final PrometheusApiService prometheusApiService;

    private final FlinkApiService flinkApiService;

    public FlinkPerformanceRetrieveScheduler(CacheService cacheService, ClusterPerformanceBenchmarkRepository clusterPerformanceBenchmarkRepository, WebClient prometheusWebClient, PrometheusProps prometheusProps, PrometheusApiService prometheusApiService, FlinkApiService flinkApiService) {
        this.cacheService = cacheService;
        this.clusterPerformanceBenchmarkRepository = clusterPerformanceBenchmarkRepository;
        this.prometheusWebClient = prometheusWebClient;
//        this.flinkProps = flinkProps;
        this.prometheusProps = prometheusProps;
        this.prometheusApiService = prometheusApiService;
        this.flinkApiService = flinkApiService;
    }

    @Scheduled(fixedDelay = 15000)
    public void scheduleFlinkPerformanceRetrieval() {
        LocalDateTime currentDateTime = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        String currentDateTimeString = currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
        log.info("<<< Start creating Flink cluster performance snapshot:" + currentDateTimeString + " >>>");

        MetricTriggerPredictionsSnapshot metricTriggerPredictionsSnapshot = this.cacheService.getMetricTriggerPredictionsSnapshot("MetricTriggerPredictionsSnapshot");

        Mono<Integer> currentFlinkClusterParallelism = this.flinkApiService.getCurrentFlinkClusterParallelism();

//        Mono<PrometheusMetric> prometheusMetric = this.prometheusApiService.getPrometheusMetric(this.prometheusApiService.getFlinkNumRecordsOutPerSecond(currentDateTimeString));
        Mono<PrometheusMetric> prometheusMetric = this.prometheusApiService.getPrometheusMetric(this.prometheusApiService.getFlinkNumRecordsIn(currentDateTimeString));

        Mono.zip(currentFlinkClusterParallelism, prometheusMetric)
                .flatMap(tuple -> {
                    int currentParallelism = tuple.getT1();
                    PrometheusMetric flinkMetric = tuple.getT2();

                    log.debug("__ RECIVED PROMETHEUS metric during benchmarking:  " + flinkMetric.toString());
                    float flinkNumRecordsInPerSecond= 0.0f;
                    if(flinkMetric.getData().getResult() != null && flinkMetric.getData().getResult().size() > 0) {
                        flinkNumRecordsInPerSecond = Float.parseFloat(flinkMetric.getData().getResult().get(0).getValue()[1].toString());
                        // Calculating average throughput of the current Flink cluster
//                        flinkNumRecordsInPerSecond = (float) flinkMetric.getData().getResult().stream().mapToDouble(value -> Double.parseDouble((String) value.getValue()[1])).average().orElse(0);
//                        double ltChoiceTemp = longTermPrediction.getPredictedWorkloads().stream().mapToInt(predictedWorkload -> (int) predictedWorkload.getValue()).average().orElse(0);
                    }

                    log.info("<<<<<<<---------->>>>>>>  EVALUATION RESULTS  <<<<<<<---------->>>>>>>");
                    log.info("Evaluation at: " + currentDateTimeString);
                    log.info("Current Flink parallelism of the cluster: " + currentParallelism);
                    log.info("Current Flink Metric from Prometheus: " + flinkMetric.toString());
                    log.info("Current Flink flinkNumRecordsInPerSecond: " + flinkNumRecordsInPerSecond);
                    if (metricTriggerPredictionsSnapshot != null) {
                        log.info("FlinkJob ID: " + metricTriggerPredictionsSnapshot.getJobId());
                        log.info("Snapshot from:" + metricTriggerPredictionsSnapshot.getSnapshotTime());
                        log.info("Based on Trigger: " + metricTriggerPredictionsSnapshot.getMetricTrigger().toString());
                        if(metricTriggerPredictionsSnapshot.getShorttermPrediction()!=null) log.info("Based on ST Prediction: " + metricTriggerPredictionsSnapshot.getShorttermPrediction().toString());
                        if(metricTriggerPredictionsSnapshot.getLongtermPredictionReported()!=null) log.info("Based on LT Prediction: " + metricTriggerPredictionsSnapshot.getLongtermPredictionReported().getClosestPrediction(currentDateTime));
                        if (currentParallelism != metricTriggerPredictionsSnapshot.getTargetParallelism())
                            log.error("Current parallelism is" + currentParallelism + ", but target parallelism was " + metricTriggerPredictionsSnapshot.getTargetParallelism());
                    }
                    log.info("<<<<<<<---------->>>>>>>  EVALUATION RESULTS  <<<<<<<---------->>>>>>>");

//                    return Mono.empty();
                    ClusterPerformanceBenchmark clusterPerformanceBenchmark = ClusterPerformanceBenchmark.builder()
                            .parallelism(currentParallelism)
                            .createdDate(currentDateTime)
                            .numTaskmanagerPods(currentParallelism)
                            .maxRate((int) (flinkNumRecordsInPerSecond))
//                            .maxRate((int) (flinkNumRecordsOutPerSecond*currentParallelism*0.5))
                            .build();

//                    if(metricTriggerPredictionsSnapshot != null) {}
//                        clusterPerformanceBenchmark.setMaxRate(((int) metricTriggerPredictionsSnapshot.getMetricTrigger().getKafkaMessagesPerSecond()));
//                        clusterPerformanceBenchmark.setMaxRate((int) flinkNumRecordsOutPerSecond);

                    return this.clusterPerformanceBenchmarkRepository.findFirstByParallelism(currentParallelism)
                            .flatMap(clusterPerformanceBenchmark1 -> {
                                clusterPerformanceBenchmark.setId(clusterPerformanceBenchmark1.getId());
                                clusterPerformanceBenchmark.setCreatedAt(LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC));
                                if(clusterPerformanceBenchmark.getMaxRate()<clusterPerformanceBenchmark1.getMaxRate()) {
                                    clusterPerformanceBenchmark.setMaxRate(clusterPerformanceBenchmark1.getMaxRate());
                                    log.debug("---- UPDATING PARALLELISM for  " + clusterPerformanceBenchmark.getParallelism() + " with maxRate: " + clusterPerformanceBenchmark.getMaxRate());
                                }
                                log.debug(clusterPerformanceBenchmark.toString());
                                return this.clusterPerformanceBenchmarkRepository.save(clusterPerformanceBenchmark);
                            })
                            .switchIfEmpty(this.clusterPerformanceBenchmarkRepository.save(clusterPerformanceBenchmark));

                }).subscribe();
    }
}
