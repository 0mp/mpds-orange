package com.mpds.flinkautoscaler.application.service.impl;

import com.mpds.flinkautoscaler.application.constants.FlinkConstants;
import com.mpds.flinkautoscaler.application.constants.PredictionConstants;
import com.mpds.flinkautoscaler.application.service.DomainEventService;
import com.mpds.flinkautoscaler.application.service.FlinkApiService;
import com.mpds.flinkautoscaler.application.service.PredictionCacheService;
import com.mpds.flinkautoscaler.domain.model.ClusterPerformanceBenchmark;
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
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

@Service
@Slf4j
public class DomainEventServiceImpl implements DomainEventService {

    private final WebClient webClient;

    private final PredictionCacheService predictionCacheService;
    private final FlinkApiService flinkApiService;

    private final ClusterPerformanceBenchmarkRepository clusterPerformanceBenchmarkRepository;

    private final FlinkProps flinkProps;

    // Flink API paths
    private final String FLINK_SAVEPOINT_PATH = "/jobs/{jobId}/savepoints";
    private final String FLINK_SAVEPOINT_INFO_PATH = "/jobs/{jobId}/savepoints/{triggerId}";
    private final String FLINK_JAR_RUN_PATH = "/jars/{jarId}/run";

    public LocalDateTime RESCALE_COOLDOWN;

    @Getter
    @Setter
    private int actualParallelism;



    public DomainEventServiceImpl(FlinkProps flinkProps, ClusterPerformanceBenchmarkRepository clusterPerformanceBenchmarkRepository, PredictionCacheService predictionCacheService, FlinkApiService flinkApiService) {
        this.flinkProps = flinkProps;
        this.clusterPerformanceBenchmarkRepository = clusterPerformanceBenchmarkRepository;
        this.predictionCacheService = predictionCacheService;
        this.webClient = WebClient.builder()
                .baseUrl(flinkProps.getBaseUrl())
                .build();
        this.flinkApiService = flinkApiService;
    }

    @Override
    // Method should process the data from the prediction topic
    public Mono<Void> processDomainEvent(MetricReported metricReported) {
        // 1. TODO: Process prediction data and decide action under certain conditions
        // Note: Redis could be implemented to save the state to decide if a rescale should be triggered or not

        // Get current parallelism and throughput of the current cluster using the data from the cache
//        int Parallelism=1;
//        this.clusterPerformanceBenchmarkRepository.findByParallelism(1)
//                .flatMap(clusterPerformanceBenchmark -> {
//                    // TODO
//                })
        double ltChoice = metricReported.getKafkaMessagesPerSecond();
        LongtermPredictionReported longTermPrediction = (LongtermPredictionReported) this.predictionCacheService.getPredictionFrom(PredictionConstants.LONG_TERM_PREDICTION_EVENT_NAME);
        if (longTermPrediction != null) {
            log.info("Current LT prediction: " + longTermPrediction.toString());
            double ltChoiceTemp = longTermPrediction.getPredictedWorkloads().stream().mapToInt(predictedWorkload -> (int) predictedWorkload.getValue()).average().orElse(0);
            if (ltChoiceTemp < metricReported.getKafkaMessagesPerSecond() * 2 &&
                    ltChoiceTemp > metricReported.getKafkaMessagesPerSecond() / 2) {
                log.info("LT prediction in bounds");
                ltChoice = ltChoiceTemp;
            }
        } else {
            log.info("No LT prediction found in cache!");
        }

        ShorttermPredictionReported shortTermPrediction = (ShorttermPredictionReported) this.predictionCacheService.getPredictionFrom(PredictionConstants.SHORT_TERM_PREDICTION_EVENT_NAME);

        float stChoice = metricReported.getKafkaMessagesPerSecond();
        if (shortTermPrediction != null) {
            log.info("Current ST prediction: " + shortTermPrediction.toString());
            if (shortTermPrediction.getPredictedWorkload() < metricReported.getKafkaMessagesPerSecond() * 2 &&
                    shortTermPrediction.getPredictedWorkload() > metricReported.getKafkaMessagesPerSecond() / 2) {
                log.info("ST prediction in bounds");
                stChoice = shortTermPrediction.getPredictedWorkload();
            }
        } else {
            log.info("No ST prediction found in cache!");
        }

        // Determine Weight prefence of prediction models
        log.info("Aggregate Predictions");
        float aggregatePrediction = (stChoice + (float) ltChoice) / 2;

        // Check application.yml file if the Flink props match to the Flink job deployment
        // Use Postman to get the new values if required
        // 2.1 Create Savepoint for the job

        return this.flinkApiService.getFlinkState().publishOn(Schedulers.boundedElastic())
                .flatMap(flinkState -> {
                    // 2. Carry out action by using the WebClient to trigger rescaling
                    // Target parallelism which has been calculated from previous step (TO BE DONE)
                    int targetParallelism = 1; // default value
                    boolean rescale = false;

                    if (FlinkConstants.RUNNING_STATE.equals(flinkState)) {
                        log.info("Evaluate Metrics");
                        // Scale Up
                        if (metricReported.getKafkaLag() > metricReported.getKafkaMessagesPerSecond() * 2 ||
                                metricReported.getCpuUtilization() > 0.6 ||
                                metricReported.getMemoryUsage() > 0.9 ) {
//                            metricReported.getMaxJobLatency() > 500
                            log.info("Scale Up, Get higher parallelism for predicted load : " + aggregatePrediction);
                            if (metricReported.getKafkaMessagesPerSecond() != 0) {
                                targetParallelism = getTargetParallelism(metricReported, aggregatePrediction, true).block(); // Response from DB
                            }
                            rescale = true; // Should be true, left false for testing
                        }
                        // Scale Down
                        if (metricReported.getKafkaLag() < metricReported.getKafkaMessagesPerSecond() &&
                                metricReported.getCpuUtilization() < 0.4 &&
                                metricReported.getMemoryUsage() < 0.5 ) {
//                            && metricReported.getMaxJobLatency() < 100
                            log.info("Scale Down, Get lower parallelism for predicted load :" + aggregatePrediction);

                            // Request from performance table parallelism for predicted load
                            if (metricReported.getKafkaMessagesPerSecond() != 0) {
                                targetParallelism = getTargetParallelism(metricReported, aggregatePrediction, false).block(); // Response from DB
                            }

                            rescale = true; // Should be true, left false for testing

                        }

                        if (rescale) {
                            log.info("<<-- Start triggering Flink rescale  -->>");
                            log.info("TargetParallelism: " + targetParallelism);
//                            int finalTargetParallelism = targetParallelism;
//                            int finalTargetParallelism1 = targetParallelism;

                            LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
                            log.debug("Now:  "+ now.toString());
//                            int cooldownDuration = 60;
                            if(RESCALE_COOLDOWN==null ||  Duration.between(RESCALE_COOLDOWN, now).abs().getSeconds() > this.flinkProps.getCooldownDuration()) {
                                log.debug("Resetting RESCALE COOLDOW: " + now.toString());
                                RESCALE_COOLDOWN = now;
                            } else {
                                log.info(" ++ + ++ + + +  BLOCKING RESCALE DUE TO COOLDOWN + + + + ++ +");
                                log.info("Rescale Cooldown: " + RESCALE_COOLDOWN.toString());
                                return Mono.empty();
                            }
//                            if(actualParallelism!=finalTargetParallelism) {
                            if(actualParallelism!=targetParallelism) {
                                return rescaleFlinkCluster(targetParallelism, metricReported, shortTermPrediction, longTermPrediction);
                            } else {
                                log.info("No Flink rescaling triggered since current and target parallelism are the same!");
                                return Mono.empty();
                            }

                        } else {
                            return Mono.empty();
                        }
                    }
                    log.info("The Flink was NOT in the state:"  + FlinkConstants.RUNNING_STATE);
                    return Mono.empty();
                });

        // TODO: Save cluster performance to the Postgres DB
        //        ClusterPerformanceBenchmark clusterPerformanceBenchmark = new ClusterPerformanceBenchmark();
        //        Set all the data and save to db
        //        this.clusterPerformanceBenchmarkRepository.save(clusterPerformanceBenchmark);
    }

    public Mono<Void> rescaleFlinkCluster(int targetParallelism, MetricReported metricReported, ShorttermPredictionReported shortTermPrediction, LongtermPredictionReported longTermPrediction){
        return this.createFlinkSavepoint(this.flinkProps.getJobId(), this.flinkProps.getSavepointDirectory(), true)
                // 2.2 Get savepoint path using the received request id
                // Wait with the request for 10 seconds so that the savepoint can complete
                .delayElement(Duration.ofSeconds(5))
                .flatMap(flinkSavepointResponse -> {
                    log.info("flinkSavepointResponse: " + flinkSavepointResponse.toString());
                    return getFlinkSavepointInfo(this.flinkProps.getJobId(), flinkSavepointResponse.getRequestId())
                            .flatMap(flinkSavepointInfoResponse -> {
                                if(flinkSavepointInfoResponse.getOperation().getLocation()==null) {
                                    log.error("Flink saveppoint operation is null for Flink Request ID:  " + flinkSavepointResponse.getRequestId());
                                    log.debug(("Trying to get savepoint again...." ));
                                    return Mono.empty()
                                            .delayElement(Duration.ofSeconds(10))
                                            .flatMap(o -> getFlinkSavepointInfo(this.flinkProps.getJobId(), flinkSavepointResponse.getRequestId())
                                                    .flatMap(flinkSavepointInfoResponse1 -> {
                                                        if(StringUtils.isEmpty(flinkSavepointInfoResponse1.getOperation().getLocation())) {
                                                            log.error("Second call for savepoint path failed: " + flinkSavepointInfoResponse1.toString());
                                                            return Mono.just(flinkSavepointInfoResponse1);
                                                        }
                                                        return Mono.just(flinkSavepointInfoResponse1);
                                                    }));
                                }
                                return Mono.just(flinkSavepointInfoResponse);
                            });
                })
//                                    .delayElement(Duration.ofSeconds(60))
                // 2.3 Start the job with the new parallelism using the savepoint path created from before
                .flatMap(flinkSavepointInfoResponse -> {
                    log.info("flinkSavepointInfoResponse: " + flinkSavepointInfoResponse.toString());
                    return runFlinkJob(this.flinkProps.getJarId(), this.flinkProps.getJobId(), this.flinkProps.getProgramArgs(), targetParallelism, flinkSavepointInfoResponse.getOperation().getLocation());
                })
                .flatMap(flinkRunJobResponse -> {
                    log.info("The job has been started successfully: " + flinkRunJobResponse.toString());
                    setActualParallelism(targetParallelism);
                    LocalDateTime currentDateTime = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
                    MetricTriggerPredictionsSnapshot metricTriggerPredictionsSnapshot = new MetricTriggerPredictionsSnapshot(flinkRunJobResponse.getJobId(), currentDateTime, metricReported, shortTermPrediction, longTermPrediction, targetParallelism);
//                                        this.predictionCacheService.cacheSnapshot(metricTriggerPredictionsSnapshot);

                    ClusterPerformanceBenchmark clusterPerformanceBenchmark = ClusterPerformanceBenchmark.builder()
                            .parallelism(targetParallelism)
                            .createdDate(currentDateTime)
                            .numTaskmanagerPods(targetParallelism)
                            .maxRate((int) metricReported.getKafkaMessagesPerSecond())
                            .build();
                    return this.clusterPerformanceBenchmarkRepository.findFirstByParallelism(targetParallelism)
                            .switchIfEmpty(this.clusterPerformanceBenchmarkRepository.save(clusterPerformanceBenchmark))
                            .flatMap(clusterPerformanceBenchmark1 -> {
                                log.error("Entry for Parallelism already existing in the database. Hence no new entry has been created: " + targetParallelism);
                                return Mono.empty();
                            });
//                        return Mono.empty();
                })
                .onErrorResume(throwable -> {
                    log.error("Flink Rescaling has failed: ", throwable);
                    return Mono.error(throwable);
                }).then();
    }

    public Mono<Integer> getTargetParallelism(MetricReported metricReported, float aggregatePrediction, boolean ScaleUp) {
        log.debug("getTargetParallelism() ");
        log.debug("aggregatePrediction: " + aggregatePrediction);
        //TODO: request from performance table parallelism for predicted load
        // above = SELECT TOP (1) FROM mytable WHERE MaxLoad < aggregatePrediction ORDER BY MaxLoad DESC
        // above = SELECT TOP (1) FROM mytable WHERE MaxLoad > aggregatePrediction ORDER BY MaxLoad ASC
        return this.flinkApiService.getCurrentFlinkClusterParallelism()
                .flatMap(currentParallel -> {
                    setActualParallelism(currentParallel);
                    log.debug("currentParallel: " + currentParallel);
                    return this.clusterPerformanceBenchmarkRepository.findOptimalParallelism(aggregatePrediction)
                            .map(above -> {
                                log.info("Current Flink Parallelism: " + currentParallel);
                                log.info("above from DB: " + above);
                                int newParallel = calculateNewParallelism(currentParallel, metricReported, aggregatePrediction);

                                if (above > currentParallel && ScaleUp) {
                                    newParallel = above;
                                }
                                if (above < currentParallel && !ScaleUp) {
                                    newParallel = above;
                                }
                                return newParallel;
                            })
                            .switchIfEmpty(Mono.just(calculateNewParallelism(currentParallel, metricReported, aggregatePrediction)));
                    // Default to parallelism 1 as the minimum
//                            .switchIfEmpty(Mono.just(1));
                });
//        int currentParallel = 1;


//        int above =3; // Return Value from DB
//        int above = this.clusterPerformanceBenchmarkRepository.findOptimalParallelism(aggregatePrediction);

//        int newParallel= (int) (currentParallel / metricReported.getKafkaMessagesPerSecond() * aggregatePrediction);

        /**
         * Logic notes to consider:
         *  Above will never be smaller than 1, in fact entry 1 will always be present in DB assuming we start with parallelism 1
         *  The new parallel value will only be 10 greater than the current parrallel when the current parallel is 10.
         */

//        if ( above > currentParallel && ScaleUp) {
//            newParallel = above;
//        }
//        if ( above < currentParallel && !ScaleUp) {
//            newParallel = above;
//        }
//        return newParallel;
    }

    public int calculateNewParallelism(int currentParallel, MetricReported metricReported, float aggregatePrediction) {
        log.info("currentParallel: " + currentParallel + " --- Kafka Messages per second: " + metricReported.getKafkaMessagesPerSecond() + " -- aggregatePrediction: "+ aggregatePrediction);
        log.info("--Calculated new parallelism: " +(int) (currentParallel / metricReported.getKafkaMessagesPerSecond() * aggregatePrediction));
        return  (int) Math.ceil((currentParallel / metricReported.getKafkaMessagesPerSecond() * aggregatePrediction));
    }

    public Mono<FlinkSavepointInfoResponse> getFlinkSavepointInfo(String jobId, String triggerId) {
        log.info("Fetching the status of the savepoint request using the triggerId: " + triggerId);
        return this.webClient.get()
                .uri(FLINK_SAVEPOINT_INFO_PATH, jobId, triggerId)
                .retrieve()
                .bodyToMono(FlinkSavepointInfoResponse.class);
    }


    public Mono<FlinkSavepointResponse> createFlinkSavepoint(String jobId, String targetDirectory, Boolean cancelJob) {
        log.info("Creating a new Flink savepoint and canceling the job...");
        FlinkSavepointRequest flinkSavepointRequest = new FlinkSavepointRequest(targetDirectory, cancelJob);
        return this.webClient.post()
                .uri(FLINK_SAVEPOINT_PATH, jobId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(flinkSavepointRequest), FlinkSavepointRequest.class)
                .retrieve()
                .bodyToMono(FlinkSavepointResponse.class)
                .onErrorResume(Mono::error);
    }

    public Mono<FlinkRunJobResponse> runFlinkJob(String jarId, String jobId, String programArgs, int parallelism, String savepointPath) {
        log.info("Starting the Flink job using the savepoint: " + savepointPath);
        FlinkRunJobRequest flinkRunJobRequest = new FlinkRunJobRequest(jobId, programArgs, parallelism, savepointPath);
        return this.webClient.post()
                .uri(FLINK_JAR_RUN_PATH, jarId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Mono.just(flinkRunJobRequest), FlinkRunJobRequest.class)
                .retrieve()
                .bodyToMono(FlinkRunJobResponse.class);
    }


}
// create savepoint response
//{
//        "request-id": "462d31a550aced1d06b70c0ee3ff4118"
//        }


// Trigger ID Response
//{
//        "status": {
//        "id": "COMPLETED"
//        },
//        "operation": {
//        "location": "hdfs://hadoop-hdfs-namenode:8020/flink/savepoints/savepoint-e80fc1-53500f6e9e5c"
//        }
//        }

// Run job response
//{
//        "jobid": "e80fc1bb72501e5e8efa8ccf47e2f3b4"
//        }
