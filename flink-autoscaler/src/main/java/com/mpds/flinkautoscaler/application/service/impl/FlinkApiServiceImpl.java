package com.mpds.flinkautoscaler.application.service.impl;

import com.mpds.flinkautoscaler.application.constants.FlinkConstants;
import com.mpds.flinkautoscaler.application.scheduler.FlinkPerformanceRetrieveScheduler;
import com.mpds.flinkautoscaler.application.scheduler.tasks.MeasureFlinkRestartTimeTask;
import com.mpds.flinkautoscaler.application.service.CacheService;
import com.mpds.flinkautoscaler.application.service.FlinkApiService;
import com.mpds.flinkautoscaler.domain.model.MetricTriggerPredictionsSnapshot;
import com.mpds.flinkautoscaler.domain.model.events.LongtermPredictionReported;
import com.mpds.flinkautoscaler.domain.model.events.MetricReported;
import com.mpds.flinkautoscaler.domain.model.events.ShorttermPredictionReported;
import com.mpds.flinkautoscaler.infrastructure.config.FlinkProps;
import com.mpds.flinkautoscaler.infrastructure.repository.ClusterPerformanceBenchmarkRepository;
import com.mpds.flinkautoscaler.port.adapter.rest.request.FlinkRunJobRequest;
import com.mpds.flinkautoscaler.port.adapter.rest.request.FlinkSavepointRequest;
import com.mpds.flinkautoscaler.port.adapter.rest.response.FlinkRunJobResponse;
import com.mpds.flinkautoscaler.port.adapter.rest.response.FlinkSavepointInfoResponse;
import com.mpds.flinkautoscaler.port.adapter.rest.response.FlinkSavepointResponse;
import com.mpds.flinkautoscaler.util.DateTimeUtil;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.configurationprocessor.json.JSONArray;
import org.springframework.boot.configurationprocessor.json.JSONException;
import org.springframework.boot.configurationprocessor.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class FlinkApiServiceImpl implements FlinkApiService {

    // Flink API paths
    private final String FLINK_SAVEPOINT_PATH = "/jobs/{jobId}/savepoints";
    private final String FLINK_SAVEPOINT_INFO_PATH = "/jobs/{jobId}/savepoints/{triggerId}";
    private final String FLINK_JAR_RUN_PATH = "/jars/{jarId}/run";
    public static final String FLINK_JOB_DETAILS_PATH="/jobs/{jobId}";

    private final FlinkProps flinkProps;

    private final WebClient flinkWebClient;

    private LocalDateTime flinkJobCanceledAt;

    private final ThreadPoolTaskScheduler threadPoolTaskScheduler;

    private final ClusterPerformanceBenchmarkRepository clusterPerformanceBenchmarkRepository;

    private final CacheService cacheService;

    private final FlinkPerformanceRetrieveScheduler flinkPerformanceRetrieveScheduler;

    @Getter
    @Setter
    private int actualParallelism;

    public FlinkApiServiceImpl(FlinkProps flinkProps, ThreadPoolTaskScheduler threadPoolTaskScheduler, ClusterPerformanceBenchmarkRepository clusterPerformanceBenchmarkRepository, CacheService cacheService, FlinkPerformanceRetrieveScheduler flinkPerformanceRetrieveScheduler) {
        this.flinkProps = flinkProps;
        this.threadPoolTaskScheduler = threadPoolTaskScheduler;
        this.clusterPerformanceBenchmarkRepository = clusterPerformanceBenchmarkRepository;
        this.cacheService = cacheService;
        this.flinkPerformanceRetrieveScheduler = flinkPerformanceRetrieveScheduler;
        this.flinkWebClient = WebClient.builder()
                .baseUrl(this.flinkProps.getBaseUrl())
                .build();
    }

    @Override
    public Mono<Integer> getCurrentFlinkClusterParallelism(){
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
        return this.flinkWebClient.get()
                .uri(FLINK_JOB_DETAILS_PATH, this.flinkProps.getJobId())
                .retrieve()
                .bodyToMono(String.class)
                .flatMap(jsonString -> {
                    String currentFlinkState;
                    JSONObject root;
                    try {
                        root = new JSONObject(jsonString);
                        currentFlinkState = root.getString(FlinkConstants.JSON_KEY_STATE);
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

    // Rescaling
    @Override
    public Mono<FlinkSavepointInfoResponse> getFlinkSavepointInfo(String jobId, String triggerId) {
        log.info("Fetching the status of the savepoint request using the triggerId: " + triggerId);
        return this.flinkWebClient.get()
                .uri(FLINK_SAVEPOINT_INFO_PATH, jobId, triggerId)
                .retrieve()
                .bodyToMono(FlinkSavepointInfoResponse.class);
    }


    @Override
    public Mono<FlinkSavepointResponse> createFlinkSavepoint(String jobId, String targetDirectory, Boolean cancelJob) {
        log.info("Creating a new Flink savepoint and canceling the job...");
        FlinkSavepointRequest flinkSavepointRequest = new FlinkSavepointRequest(targetDirectory, cancelJob);
        return this.flinkWebClient.post()
                .uri(FLINK_SAVEPOINT_PATH, jobId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(flinkSavepointRequest), FlinkSavepointRequest.class)
                .retrieve()
                .bodyToMono(FlinkSavepointResponse.class)
                .onErrorResume(Mono::error);
    }

    @Override
    public Mono<FlinkRunJobResponse> runFlinkJob(String jarId, String jobId, String programArgs, int parallelism, String savepointPath) {
        log.info("Starting the Flink job using the savepoint: " + savepointPath);
        FlinkRunJobRequest flinkRunJobRequest;
        if (!StringUtils.isEmpty(savepointPath)) {
            flinkRunJobRequest = new FlinkRunJobRequest(jobId, programArgs, parallelism, savepointPath);
        } else {
            flinkRunJobRequest = FlinkRunJobRequest.builder().jobId(jobId).programArgs(programArgs).parallelism(parallelism).build();
        }
        return this.flinkWebClient.post()
                .uri(FLINK_JAR_RUN_PATH, jarId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(flinkRunJobRequest), FlinkRunJobRequest.class)
                .retrieve()
                .bodyToMono(FlinkRunJobResponse.class)
                .doOnError(throwable -> log.error("Flink job restart has failed: " + throwable.getMessage()))
                .onErrorResume(throwable -> {
                    log.error("Flink job could not be started ", throwable);
                    log.info("Checking if Flink job could be restarted....");
                    return Mono.just(1)
                            .delayElement(Duration.ofSeconds(10))
                            .flatMap(unused -> this.getFlinkState())
                            .flatMap(flinkState -> {
                                if (FlinkConstants.CANCELED_STATE.equals(flinkState)) {
                                    log.info("Job has been canceled. Trying to restart the job...");

                                    return this.flinkWebClient.post()
                                            .uri(FLINK_JAR_RUN_PATH, jarId)
                                            .contentType(MediaType.APPLICATION_JSON)
                                            .body(Mono.just(flinkRunJobRequest), FlinkRunJobRequest.class)
                                            .retrieve()
                                            .bodyToMono(FlinkRunJobResponse.class);
                                }
                                return Mono.empty();
                            });
                });
    }

    @Override
    public Mono<FlinkSavepointInfoResponse> repeatGetFlinkSavePoint(FlinkSavepointResponse flinkSavepointResponse, int targetParallelism) {
        this.flinkJobCanceledAt = DateTimeUtil.getCurrentUTCDateTime();
        this.threadPoolTaskScheduler.schedule(new MeasureFlinkRestartTimeTask(this, this.clusterPerformanceBenchmarkRepository, flinkJobCanceledAt, targetParallelism), Instant.now());
        log.info("Flink Job canceled at: " + flinkJobCanceledAt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'")));
        return Mono.defer(() -> getFlinkSavepointInfo(this.flinkProps.getJobId(), flinkSavepointResponse.getRequestId())
                .flatMap(flinkSavepointInfoResponse -> {
                    log.info("FLINKSAVEPOINT INFO RESPONSE:::::  " + flinkSavepointInfoResponse);
                    if (flinkSavepointInfoResponse.getOperation() == null || flinkSavepointInfoResponse.getOperation().getLocation() == null) {
                        log.error("Flink saveppoint operation is null for Flink Request ID:  " + flinkSavepointResponse.getRequestId());
                        return Mono.empty();
                    }
                    return Mono.just(flinkSavepointInfoResponse);
                }))
                .repeatWhenEmpty(240, longFlux -> longFlux.delayElements(Duration.ofSeconds(1)).doOnNext(it -> log.info("Repeating {}", it)));
    }

    @Override
    public Mono<Void> rescaleFlinkCluster(int targetParallelism, MetricReported metricReported, ShorttermPredictionReported shortTermPrediction, LongtermPredictionReported longTermPrediction, float aggregatePrediction) {
        log.info(" ############################## NOW RESCALING THE JOB with parallelism:  " + targetParallelism + " ##############################");
        return this.createFlinkSavepoint(this.flinkProps.getJobId(), this.flinkProps.getSavepointDirectory(), true)
                .flatMap(flinkSavepointResponse -> repeatGetFlinkSavePoint(flinkSavepointResponse, targetParallelism))
                .flatMap(flinkSavepointInfoResponse -> {
                    log.info("flinkSavepointInfoResponse: " + flinkSavepointInfoResponse.toString());
                    this.cacheService.cacheFlinkSavepoint(flinkSavepointInfoResponse.getOperation().getLocation());
                    return runFlinkJob(this.flinkProps.getJarId(), this.flinkProps.getJobId(), this.flinkProps.getProgramArgs(), targetParallelism, flinkSavepointInfoResponse.getOperation().getLocation());
                })
                // Save last rescale action
                .flatMap(flinkRunJobResponse -> {
                    log.info("The job has been started successfully: " + flinkRunJobResponse.toString());
                    // Change flag so that measurement is only inserted to DB when rules are fulfilled after rescale
                    this.flinkPerformanceRetrieveScheduler.setAlwaysInsertClusterPerformanceToDB(false);
                    setActualParallelism(targetParallelism);
                    LocalDateTime currentDateTime = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
                    MetricTriggerPredictionsSnapshot metricTriggerPredictionsSnapshot = new MetricTriggerPredictionsSnapshot(flinkRunJobResponse.getJobId(), currentDateTime, metricReported, shortTermPrediction, longTermPrediction, targetParallelism, aggregatePrediction);
                    this.cacheService.cacheSnapshot(metricTriggerPredictionsSnapshot);
                    return Mono.empty();
                })
                .onErrorResume(throwable -> {
                    log.error("Flink Rescaling has failed: ", throwable);
                    return Mono.error(throwable);
                }).then();
    }

    @Override
    public Mono<Void> startFlinkCluster(int targetParallelism, MetricReported metricReported, ShorttermPredictionReported shortTermPrediction, LongtermPredictionReported longTermPrediction, String flinkSavepoint, float aggregatePrediction) {
        log.info(" ############################## NOW STARTING THE JOB withour savepoint and with parallelism: " + targetParallelism + " ##############################");
        return runFlinkJob(this.flinkProps.getJarId(), this.flinkProps.getJobId(), this.flinkProps.getProgramArgs(), targetParallelism, flinkSavepoint)
                // Save last rescale action
                .flatMap(flinkRunJobResponse -> {
                    log.info("The job has been started successfully: " + flinkRunJobResponse.toString());
                    setActualParallelism(targetParallelism);
                    LocalDateTime currentDateTime = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
                    MetricTriggerPredictionsSnapshot metricTriggerPredictionsSnapshot = new MetricTriggerPredictionsSnapshot(flinkRunJobResponse.getJobId(), currentDateTime, metricReported, shortTermPrediction, longTermPrediction, targetParallelism, aggregatePrediction);
                    this.cacheService.cacheSnapshot(metricTriggerPredictionsSnapshot);
                    return Mono.empty();
                })
                .onErrorResume(throwable -> {
                    log.error("Flink Rescaling has failed: ", throwable);
                    return Mono.error(throwable);
                }).then();
    }
}
