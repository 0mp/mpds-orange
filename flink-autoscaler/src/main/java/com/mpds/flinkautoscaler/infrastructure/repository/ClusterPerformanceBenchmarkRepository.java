package com.mpds.flinkautoscaler.infrastructure.repository;

import com.mpds.flinkautoscaler.domain.model.ClusterPerformanceBenchmark;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ClusterPerformanceBenchmarkRepository extends ReactiveCrudRepository<ClusterPerformanceBenchmark, Long> {

    Mono<ClusterPerformanceBenchmark> findByParallelism(int parallelism);
}
