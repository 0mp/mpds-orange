package com.mpds.flinkautoscaler.application.service;

import reactor.core.publisher.Mono;

public interface FlinkApiService {

    Mono<Integer> getCurrentFlinkClusterParallelism();

    Mono<String> getFlinkState();
}
