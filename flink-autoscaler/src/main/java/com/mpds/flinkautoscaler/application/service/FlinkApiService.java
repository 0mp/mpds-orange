package com.mpds.flinkautoscaler.application.service;

import com.mpds.flinkautoscaler.domain.model.events.LongtermPredictionReported;
import com.mpds.flinkautoscaler.domain.model.events.MetricReported;
import com.mpds.flinkautoscaler.domain.model.events.ShorttermPredictionReported;
import com.mpds.flinkautoscaler.port.adapter.rest.response.FlinkRunJobResponse;
import com.mpds.flinkautoscaler.port.adapter.rest.response.FlinkSavepointInfoResponse;
import com.mpds.flinkautoscaler.port.adapter.rest.response.FlinkSavepointResponse;
import reactor.core.publisher.Mono;

public interface FlinkApiService {

    void setActualParallelism(int currentParallel);

    int getActualParallelism();

    Mono<Integer> getCurrentFlinkClusterParallelism();

    Mono<String> getFlinkState();

    Mono<FlinkSavepointInfoResponse> getFlinkSavepointInfo(String jobId, String triggerId);

    Mono<FlinkSavepointResponse> createFlinkSavepoint(String jobId, String targetDirectory, Boolean cancelJob);

    Mono<FlinkRunJobResponse> runFlinkJob(String jarId, String jobId, String programArgs, int parallelism, String savepointPath);

    Mono<FlinkSavepointInfoResponse> repeatGetFlinkSavePoint(FlinkSavepointResponse flinkSavepointResponse, int targetParallelism);
    Mono<Void> rescaleFlinkCluster(int targetParallelism, MetricReported metricReported, ShorttermPredictionReported shortTermPrediction, LongtermPredictionReported longTermPrediction, float aggregatePrediction);

    Mono<Void> startFlinkCluster(int targetParallelism, MetricReported metricReported, ShorttermPredictionReported shortTermPrediction, LongtermPredictionReported longTermPrediction, String flinkSavepoint, float aggregatePrediction);
}
