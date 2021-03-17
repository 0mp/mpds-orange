//package com.mpds.flinkautoscaler.application.scheduler.tasks;
//
//import com.mpds.flinkautoscaler.application.constants.FlinkConstants;
//import com.mpds.flinkautoscaler.application.service.PrometheusApiService;
//import com.mpds.flinkautoscaler.application.service.impl.DomainEventServiceImpl;
//import com.mpds.flinkautoscaler.infrastructure.repository.ClusterPerformanceBenchmarkRepository;
//import com.mpds.flinkautoscaler.util.DateTimeUtil;
//import lombok.Data;
//import lombok.extern.slf4j.Slf4j;
//import reactor.core.publisher.Mono;
//
//import java.time.Duration;
//import java.time.LocalDateTime;
//import java.time.format.DateTimeFormatter;
//
//@Data
//@Slf4j
//public class MeasureFlinkTargetParallelismTimeTask implements Runnable {
//
//    private final PrometheusApiService prometheusApiService;
//    private final ClusterPerformanceBenchmarkRepository clusterPerformanceBenchmarkRepository;
//    private final LocalDateTime flinkJobCanceledAt;
//    private final int targetParallelism;
//
//    long flinkRestartTimeInMillis;
//
//    @Override
//    public void run() {
//        Mono<Integer> currentFlinkClusterParallelism = this.prometheusApiService.getPrometheusMetric(this.prometheusApiService.getFlinkNumOfTaskManagers(currentDateTimeString))
//                .map(prometheusMetric -> {
//                    log.debug("[FlinkPerformanceRetrieveScheduler] Prometheus number of task managers response at <"+ currentDateTimeString +"> : " + prometheusMetric.toString());
//                    if(prometheusMetric.getData().getResult() != null && prometheusMetric.getData().getResult().size() > 0) {
//                        return Integer.parseInt(prometheusMetric.getData().getResult().get(0).getValue()[1].toString());
//                    }
//                    log.error("No Flink task managers were active reported from Prometheus at: " + currentDateTimeString);
//                    return 0;
//                });
//        flinkApiService.getFlinkState()
//                .flatMap(flinkState -> {
//                    log.trace("<MeasureFlinkRestartTimeTask>: " + flinkState);
//                    if (FlinkConstants.RUNNING_STATE.equals(flinkState)) {
//                        LocalDateTime currentUTCDateTime = DateTimeUtil.getCurrentUTCDateTime();
//                        this.flinkRestartTimeInMillis = Duration.between(this.flinkJobCanceledAt, currentUTCDateTime).abs().toMillis();
//                        log.info("<MeasureFlinkRestartTimeTask> Flink job is now running again at " + currentUTCDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")));
//                        log.info("<MeasureFlinkRestartTimeTask> Restart Duration: " + flinkRestartTimeInMillis);
//                        DomainEventServiceImpl.flinkRestartTimeInSec = flinkRestartTimeInMillis;
//
//                        return Mono.just(flinkState);
//                    }
//                    return Mono.empty();
//                })
//                .repeatWhenEmpty(20, longFlux -> longFlux.delayElements(Duration.ofSeconds(1)).doOnNext(it -> log.info("Repeating {}", it)))
//                .flatMap(s -> this.clusterPerformanceBenchmarkRepository.findFirstByParallelism(targetParallelism)
//                        .flatMap(clusterPerformanceBenchmark -> {
//                            if(this.flinkRestartTimeInMillis >clusterPerformanceBenchmark.getRestartTime()) {
//                                log.info("<MeasureFlinkRestartTimeTask> Inserting higher restartTime to the performance table: " + this.flinkRestartTimeInMillis + "(secs) for parallelism <" + this.targetParallelism+">");
//                                clusterPerformanceBenchmark.setRestartTime(this.flinkRestartTimeInMillis);
//                                return this.clusterPerformanceBenchmarkRepository.save(clusterPerformanceBenchmark);
//                            }
//                            return Mono.empty();
//                        }))
//                .subscribe();
//    }
//}
