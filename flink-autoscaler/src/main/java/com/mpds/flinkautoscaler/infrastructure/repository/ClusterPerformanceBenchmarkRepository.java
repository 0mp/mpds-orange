package com.mpds.flinkautoscaler.infrastructure.repository;

import com.mpds.flinkautoscaler.domain.model.ClusterPerformanceBenchmark;
import org.springframework.data.r2dbc.repository.Query;
import org.springframework.data.repository.reactive.ReactiveCrudRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

@Repository
public interface ClusterPerformanceBenchmarkRepository extends ReactiveCrudRepository<ClusterPerformanceBenchmark, Long> {

//    Mono<ClusterPerformanceBenchmark> findByParallelism(int parallelism);

    Mono<ClusterPerformanceBenchmark> findFirstByParallelism(int parallelism);

//    SELECT TOP (1) FROM cluster_performance_benchmark WHERE max_rate < 21000 ORDER BY max_rate DESC
    @Query("SELECT parallelism FROM cluster_performance_benchmark WHERE max_rate > :aggregatePrediction ORDER BY max_rate ASC LIMIT 1")
    Mono<Integer> findOptimalParallelism(float aggregatePrediction);
}
