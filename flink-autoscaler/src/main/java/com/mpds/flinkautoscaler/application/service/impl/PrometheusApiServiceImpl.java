package com.mpds.flinkautoscaler.application.service.impl;

import com.mpds.flinkautoscaler.application.service.PrometheusApiService;
import com.mpds.flinkautoscaler.domain.model.PrometheusMetric;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Slf4j
@Service
@RequiredArgsConstructor
public class PrometheusApiServiceImpl implements PrometheusApiService {

    public static final String PROMETHEUS_QUERY_PATH="/api/v1/query";

    private final WebClient webClient;

    @Override
    public Mono<PrometheusMetric> getPrometheusMetric(MultiValueMap<String, String> message) {

        return this.webClient.post()
                .uri(PROMETHEUS_QUERY_PATH)
                .body(BodyInserters.fromFormData(message))
                .retrieve()
                .bodyToMono(PrometheusMetric.class);
    }

    @Override
    public MultiValueMap<String, String> getCpuUsage(String dateTime) {
        log.debug("get CPU usage for dateTime: " + dateTime);
        LinkedMultiValueMap<String, String> lmvn = new LinkedMultiValueMap<>();
        final String PROMETHEUS_QUERY = "sum(flink_taskmanager_Status_JVM_CPU_Load) / sum(flink_jobmanager_numRegisteredTaskManagers)";
        lmvn.add("query", PROMETHEUS_QUERY);
        lmvn.add("time", dateTime);
        return lmvn;
    }

    @Override
    public MultiValueMap<String, String> getKafkaLag(String dateTime) {
        log.debug("get Kafka lag for dateTime: " + dateTime);
        LinkedMultiValueMap<String, String> lmvn = new LinkedMultiValueMap<>();
        final String PROMETHEUS_QUERY = "sum(flink_taskmanager_job_task_operator_KafkaConsumer_records_lag_max) / count(flink_taskmanager_job_task_operator_KafkaConsumer_records_lag_max)";
        lmvn.add("query", PROMETHEUS_QUERY);
        lmvn.add("time", dateTime);
        return lmvn;
    }

    @Override
    public MultiValueMap<String, String> getMaxJobLatency(String dateTime) {

        log.debug("get max job latency for dateTime: " + dateTime);
        LinkedMultiValueMap<String, String> lmvn = new LinkedMultiValueMap<>();
        final String PROMETHEUS_QUERY = "max(flink_taskmanager_job_latency_source_id_operator_id_operator_subtask_index_latency)";
        lmvn.add("query", PROMETHEUS_QUERY);
        lmvn.add("time", dateTime);
        return lmvn;
    }

    @Override
    public MultiValueMap<String, String> getMemUsage(String dateTime) {

        log.debug("get Memory usage for dateTime: " + dateTime);
        LinkedMultiValueMap<String, String> lmvn = new LinkedMultiValueMap<>();
        final String PROMETHEUS_QUERY = "sum(flink_taskmanager_Status_JVM_Memory_Heap_Used / flink_taskmanager_Status_JVM_Memory_Heap_Committed) / sum(flink_jobmanager_numRegisteredTaskManagers)";
        lmvn.add("query", PROMETHEUS_QUERY);
        lmvn.add("time", dateTime);
        return lmvn;
    }

    @Override
    public MultiValueMap<String, String> getFlinkNumRecordsOutPerSecond(String dateTime) {
        log.debug("getFlinkNumRecordsOutPerSecond for dateTime: " + dateTime);
        LinkedMultiValueMap<String, String> lmvn = new LinkedMultiValueMap<>();
        final String PROMETHEUS_QUERY = "flink_taskmanager_job_task_numRecordsOutPerSecond";
        lmvn.add("query", PROMETHEUS_QUERY);
        lmvn.add("time", dateTime);
        return lmvn;
    }

    @Override
    public MultiValueMap<String, String> getKafkaMessagesPerSecond(String dateTime) {
        log.debug("getKafkaMessagesPerSecond for dateTime: " + dateTime);
        LinkedMultiValueMap<String, String> lmvn = new LinkedMultiValueMap<>();
        final String PROMETHEUS_QUERY = "sum by (topic) (rate(kafka_server_brokertopicmetrics_messagesinpersec_count[2m]))";
        lmvn.add("query", PROMETHEUS_QUERY);
        lmvn.add("time", dateTime);
        return lmvn;
    }

    @Override
    public MultiValueMap<String, String> getFlinkNumOfTaskManagers(String dateTime) {
        log.debug("getFlinkNumOfTaskManagers for dateTime: " + dateTime);
        LinkedMultiValueMap<String, String> lmvn = new LinkedMultiValueMap<>();
        final String PROMETHEUS_QUERY = "flink_jobmanager_numRegisteredTaskManagers";
        lmvn.add("query", PROMETHEUS_QUERY);
        lmvn.add("time", dateTime);
        return lmvn;
    }

    @Override
    public MultiValueMap<String, String> getFlinkNumRecordsIn(String dateTime) {
        log.debug("getFlinkNumRecordsIn for dateTime: " + dateTime);
        LinkedMultiValueMap<String, String> lmvn = new LinkedMultiValueMap<>();
        final String PROMETHEUS_QUERY = "sum by (job_name) (rate(flink_taskmanager_job_task_numRecordsIn[2m]))";
        lmvn.add("query", PROMETHEUS_QUERY);
        lmvn.add("time", dateTime);
        return lmvn;
    }
}
