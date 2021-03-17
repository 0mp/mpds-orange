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

    @Query("SELECT * FROM cluster_performance_benchmark WHERE max_rate > :aggregatePrediction ORDER BY max_rate ASC LIMIT 1")
    Mono<ClusterPerformanceBenchmark> findOptimalParallelismWithMaxRate(float aggregatePrediction);

    @Query("SELECT * FROM cluster_performance_benchmark WHERE max_rate < :aggregatePrediction ORDER BY max_rate DESC LIMIT 1")
    Mono<ClusterPerformanceBenchmark> findInfimumParallelismWithMaxRate(float aggregatePrediction);

    @Query("SELECT max_rate FROM cluster_performance_benchmark WHERE parallelism = :aggregatePrediction")
    Mono<Integer> getMaxRateOfParallelism(int parallelism);
}
