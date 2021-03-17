package com.mpds.flinkautoscaler.application.scheduler.tasks;

import com.mpds.flinkautoscaler.application.constants.FlinkConstants;
import com.mpds.flinkautoscaler.application.service.FlinkApiService;
import com.mpds.flinkautoscaler.application.service.impl.DomainEventServiceImpl;
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

    long flinkRestartTimeInSecs;

    @Override
    public void run() {
        flinkApiService.getFlinkState()
                .flatMap(flinkState -> {
                    log.trace("<MeasureFlinkRestartTimeTask>: " + flinkState);
                    if (FlinkConstants.RUNNING_STATE.equals(flinkState)) {
                        LocalDateTime currentUTCDateTime = DateTimeUtil.getCurrentUTCDateTime();
                        this.flinkRestartTimeInSecs = Duration.between(this.flinkJobCanceledAt, currentUTCDateTime).abs().toMillis();
                        log.info("<MeasureFlinkRestartTimeTask> Flink job is now running again at " + currentUTCDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")));
                        log.info("<MeasureFlinkRestartTimeTask> Restart Duration: " + flinkRestartTimeInSecs);
                        DomainEventServiceImpl.flinkRestartTimeInSec = flinkRestartTimeInSecs;

                        return Mono.just(flinkState);
                    }
                    return Mono.empty();
                })
                .repeatWhenEmpty(20, longFlux -> longFlux.delayElements(Duration.ofSeconds(1)).doOnNext(it -> log.info("Repeating {}", it)))
                .flatMap(s -> this.clusterPerformanceBenchmarkRepository.findFirstByParallelism(targetParallelism)
                        .flatMap(clusterPerformanceBenchmark -> {
                            if(this.flinkRestartTimeInSecs>clusterPerformanceBenchmark.getRestartTime()) {
                                log.info("<MeasureFlinkRestartTimeTask> Inserting higher restartTime to the performance table: " + this.flinkRestartTimeInSecs + "(secs) for parallelism <" + this.targetParallelism+">");
                                clusterPerformanceBenchmark.setRestartTime(this.flinkRestartTimeInSecs);
                                return this.clusterPerformanceBenchmarkRepository.save(clusterPerformanceBenchmark);
                            }
                            return Mono.empty();
                        }))
                .subscribe();
    }
}
