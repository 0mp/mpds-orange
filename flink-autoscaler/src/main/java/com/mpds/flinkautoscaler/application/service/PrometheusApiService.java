package com.mpds.flinkautoscaler.application.service;

import com.mpds.flinkautoscaler.domain.model.PrometheusMetric;
import org.springframework.util.MultiValueMap;
import reactor.core.publisher.Mono;

public interface PrometheusApiService {

    Mono<PrometheusMetric> getPrometheusMetric(MultiValueMap<String, String> message);

    MultiValueMap<String, String> getCpuUsage(String dateTime);

    MultiValueMap<String, String> getKafkaLag(String dateTime);

    MultiValueMap<String, String> getMaxJobLatency(String dateTime);

    MultiValueMap<String, String> getMemUsage(String dateTime);

    MultiValueMap<String, String> getKafkaMessagesPerSecond(String dateTime, String sourceTopic);

    MultiValueMap<String, String> getFlinkNumRecordsOutPerSecond(String dateTime);
}