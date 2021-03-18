package com.mpds.flinkautoscaler.application.scheduler.tasks;

import com.mpds.flinkautoscaler.application.constants.FlinkConstants;
import com.mpds.flinkautoscaler.application.service.FlinkApiService;
import com.mpds.flinkautoscaler.application.service.impl.DomainEventServiceImpl;
import com.mpds.flinkautoscaler.domain.model.ClusterPerformanceBenchmark;
import com.mpds.flinkautoscaler.infrastructure.repository.ClusterPerformanceBenchmarkRepository;
import com.mpds.flinkautoscaler.util.DateTimeUtil;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@Data
@Slf4j
public class MeasureFlinkRestartTimeTask implements Runnable {

    private final FlinkApiService flinkApiService;
    private final ClusterPerformanceBenchmarkRepository clusterPerformanceBenchmarkRepository;
    private final LocalDateTime flinkJobCanceledAt;
    private final int targetParallelism;

    private long flinkRestartTimeInMillis;

    @Override
    public void run() {
        LocalDateTime currentUTCDateTime = DateTimeUtil.getCurrentUTCDateTime();
        ClusterPerformanceBenchmark flinkClusterRestartTime = ClusterPerformanceBenchmark.builder()
                .parallelism(targetParallelism)
                .createdDate(currentUTCDateTime)
                .numTaskmanagerPods(targetParallelism)
                .restartTime(flinkRestartTimeInMillis)
                .build();
        flinkApiService.getFlinkState()
                .flatMap(flinkState -> {
                    log.trace("<MeasureFlinkRestartTimeTask>: " + flinkState);
                    if (FlinkConstants.RUNNING_STATE.equals(flinkState)) {
                        this.flinkRestartTimeInMillis = Duration.between(this.flinkJobCanceledAt, currentUTCDateTime).abs().toMillis();
                        log.info("<MeasureFlinkRestartTimeTask> Flink job is now running again at " + currentUTCDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")));
                        log.info("<MeasureFlinkRestartTimeTask> Restart Duration: " + flinkRestartTimeInMillis + " for targetParallelism " + targetParallelism);
                        DomainEventServiceImpl.flinkRestartTimeInMillis = flinkRestartTimeInMillis;
                        return Mono.just(flinkState);
                    }
                    return Mono.empty();
                })
                .repeatWhenEmpty(20, longFlux -> longFlux.delayElements(Duration.ofSeconds(1)).doOnNext(it -> log.info("Wait until Flink cluster is running again... {}", it)))
                .flatMap(s -> this.clusterPerformanceBenchmarkRepository.findFirstByParallelism(targetParallelism)
                        .flatMap(clusterPerformanceBenchmark -> {
                            if(this.flinkRestartTimeInMillis >clusterPerformanceBenchmark.getRestartTime()) {
                                log.info("<MeasureFlinkRestartTimeTask> Inserting higher restartTime to the performance table: " + this.flinkRestartTimeInMillis + " (millis) for parallelism <" + this.targetParallelism+">");
                                clusterPerformanceBenchmark.setRestartTime(this.flinkRestartTimeInMillis);
                                return this.clusterPerformanceBenchmarkRepository.save(clusterPerformanceBenchmark);
                            }
                            return Mono.just(1);
                        })
                        .switchIfEmpty(persistNewClusterPerformanceBenchmark(flinkClusterRestartTime))
                )
                .subscribe();
    }

    private Mono<ClusterPerformanceBenchmark> persistNewClusterPerformanceBenchmark(ClusterPerformanceBenchmark flinkClusterRestartTime) {
        // Set true to avoid overriding values
        flinkClusterRestartTime.setNewEntry(true);
        return this.clusterPerformanceBenchmarkRepository.save(flinkClusterRestartTime);
    }
}
