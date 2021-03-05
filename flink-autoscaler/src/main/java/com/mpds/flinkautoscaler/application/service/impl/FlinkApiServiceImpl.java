package com.mpds.flinkautoscaler.application.service.impl;

import com.mpds.flinkautoscaler.application.constants.FlinkConstants;
import com.mpds.flinkautoscaler.application.service.FlinkApiService;
import com.mpds.flinkautoscaler.infrastructure.config.FlinkProps;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class FlinkApiServiceImpl implements FlinkApiService {

    public static final String FLINK_JOB_DETAILS_PATH="/jobs/{jobId}";

    private final FlinkProps flinkProps;

    private final WebClient flinkWebClient;

    public FlinkApiServiceImpl(FlinkProps flinkProps) {
        this.flinkProps = flinkProps;
        this.flinkWebClient = WebClient.builder()
                .baseUrl(this.flinkProps.getBaseUrl())
                .build();
    }

    @Override
    public Mono<Integer>
    getCurrentFlinkClusterParallelism(){
        return this.flinkWebClient.get()
                .uri(FLINK_JOB_DETAILS_PATH, this.flinkProps.getJobId())
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(this::checkFlinkState)
                .flatMap(this::getFlinkParallelism);
    }

    private Mono<JSONObject> checkFlinkState(String jsonString){
        log.debug("Received Flink response: ");
        log.debug(jsonString);
        String currentFlinkState;
        JSONObject root;
        try {
            root = new JSONObject(jsonString);
            currentFlinkState = root.getString(FlinkConstants.JSON_KEY_STATE);
            if(!FlinkConstants.RUNNING_STATE.equals(currentFlinkState)) log.info("The Flink job is currently not running!");
            return Mono.just(root);
        } catch (JSONException e) {
            log.error("JSON object could not be parsed ", e);
            return Mono.error(e);
        }
    }

    @Override
    public Mono<String> getFlinkState(){
//        log.debug("Getting current Flink state...");
        return this.flinkWebClient.get()
                .uri(FLINK_JOB_DETAILS_PATH, this.flinkProps.getJobId())
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(jsonString -> {
//                    log.debug(jsonString);
                    String currentFlinkState;
                    JSONObject root;
                    try {
                        root = new JSONObject(jsonString);
                        currentFlinkState = root.getString(FlinkConstants.JSON_KEY_STATE);
//            if(!FlinkConstants.RUNNING_STATE.equals(currentFlinkState)) return Mono.error(new IllegalStateException("The Flink job is currently not running!"));
                        return Mono.just(currentFlinkState);
                    } catch (JSONException e) {
                        log.error("JSON object could not be parsed ", e);
                        return Mono.error(e);
                    }
                });


    }

    private Mono<Integer> getFlinkParallelism(JSONObject jsonObject) {
        log.debug("getFlinkParallelism for: "+ jsonObject.toString());
        int parallelism;
        try {
            JSONArray jsonArray = jsonObject.getJSONArray(FlinkConstants.JSON_KEY_VERTICES);
            JSONObject vertice = jsonArray.getJSONObject(0);
            parallelism = vertice.getInt(FlinkConstants.JSON_KEY_PARALLELISM);
            return Mono.just(parallelism);
        } catch (JSONException e) {
            log.error("JSON Array could not be parsed ", e);
            return Mono.error(e);
        }
    }
}
