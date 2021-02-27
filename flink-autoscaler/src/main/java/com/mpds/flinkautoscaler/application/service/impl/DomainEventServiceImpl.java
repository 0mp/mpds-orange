package com.mpds.flinkautoscaler.application.service.impl;

import com.mpds.flinkautoscaler.application.constants.PredictionConstants;
import com.mpds.flinkautoscaler.application.service.DomainEventService;
import com.mpds.flinkautoscaler.application.service.PredictionCacheService;
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
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.Duration;

@Service
@Slf4j
public class DomainEventServiceImpl implements DomainEventService {

    private final WebClient webClient;

    private final PredictionCacheService predictionCacheService;

    private final ClusterPerformanceBenchmarkRepository clusterPerformanceBenchmarkRepository;

    private final FlinkProps flinkProps;

    // Flink API paths
    private final String FLINK_SAVEPOINT_PATH = "/jobs/{jobId}/savepoints";
    private final String FLINK_SAVEPOINT_INFO_PATH = "/jobs/{jobId}/savepoints/{triggerId}";
    private final String FLINK_JAR_RUN_PATH = "/jars/{jarId}/run";

    public DomainEventServiceImpl(FlinkProps flinkProps, ClusterPerformanceBenchmarkRepository clusterPerformanceBenchmarkRepository, PredictionCacheService predictionCacheService) {
        this.flinkProps = flinkProps;
        this.clusterPerformanceBenchmarkRepository = clusterPerformanceBenchmarkRepository;
        this.predictionCacheService=predictionCacheService;
        this.webClient = WebClient.builder()
                .baseUrl(flinkProps.getBaseUrl())
                .build();
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
        LongtermPredictionReported longTermPrediction = (LongtermPredictionReported) this.predictionCacheService.getPredictionFrom(PredictionConstants.LONG_TERM_PREDICTION_EVENT_NAME);
        if(longTermPrediction!=null) {
            log.info("Current LT prediction: "+ longTermPrediction.toString());
        } else {
            log.info("No LT prediction found in cache!");
        }

        ShorttermPredictionReported shortTermPrediction = (ShorttermPredictionReported) this.predictionCacheService.getPredictionFrom(PredictionConstants.SHORT_TERM_PREDICTION_EVENT_NAME);

        if(shortTermPrediction!=null) {
            log.info("Current ST prediction: "+ shortTermPrediction.toString());
        } else {
            log.info("No ST prediction found in cache!");
        }


        // 2. Carry out action by using the WebClient to trigger rescaling
        // Target parallelism which has been calculated from previous step (TO BE DONE)
        int targetParallelism = 3;
        boolean rescale = false;
        log.info("<<-- Start triggering Flink rescale  -->>");
        log.info("TargetParallelism: " + targetParallelism);

        // Check application.yml file if the Flink props match to the Flink job deployment
        // Use Postman to get the new values if required
        // 2.1 Create Savepoint for the job
        // TODO: Condition should be adjusted


        log.info("Evaluate");
        // Scale Up
        if (metricReported.getKafkaLag() > metricReported.getKafkaMessagesPerSecond() *2 ||
                metricReported.getCpuUtilization() > 0.6 ||
                metricReported.getMemoryUsage() > 0.9 ||
                metricReported.getMaxJobLatency() > 500) {
            log.info("Scale Up, Get higher parallelism for predicted load : " + shortTermPrediction.toString() );

            //TODO: request from performance table parallelism for predicted load
            targetParallelism = 3; // Response from DB
            rescale = false; // Should be true, left false for testing

        }
        // Scale Down
        if (metricReported.getKafkaLag() < metricReported.getKafkaMessagesPerSecond()  &&
                metricReported.getCpuUtilization() < 0.4 &&
                metricReported.getMemoryUsage() < 0.5 &&
                metricReported.getMaxJobLatency() < 100) {
            log.info("Scale Down, Get lower parallelism for predicted load :" + shortTermPrediction.toString());

            //TODO: request from performance table parallelism for predicted load
            targetParallelism = 3; // Response from DB
            rescale = false; // Should be true, left false for testing

        }

        if(rescale) {
            int finalTargetParallelism = targetParallelism;
            return this.createFlinkSavepoint(this.flinkProps.getJobId(), this.flinkProps.getSavepointDirectory(), true)
                    // 2.2 Get savepoint path using the received request id
                    // Wait with the request for 10 seconds so that the savepoint can complete
                    .delayElement(Duration.ofSeconds(10))
                    .flatMap(flinkSavepointResponse -> {
                        log.info("flinkSavepointResponse: " + flinkSavepointResponse.toString());
                        return getFlinkSavepointInfo(this.flinkProps.getJobId(), flinkSavepointResponse.getRequestId());
                    })
                    // 2.3 Start the job with the new parallelism using the savepoint path created from before
                    .flatMap(flinkSavepointInfoResponse -> {
                        log.info("flinkSavepointInfoResponse: " + flinkSavepointInfoResponse.toString());
                        return runFlinkJob(this.flinkProps.getJarId(), this.flinkProps.getJobId(), this.flinkProps.getProgramArgs(), finalTargetParallelism, flinkSavepointInfoResponse.getOperation().getLocation());
                    })
                    .flatMap(flinkRunJobResponse -> {
                        log.info("The job has been started successfully: " + flinkRunJobResponse.toString());
                        return Mono.empty();
                    })
                    .onErrorResume(throwable -> {
                        log.error("Flink Rescaling has failed: ", throwable);
                        return Mono.error(throwable);
                    }).then();
        } else {
            return Mono.empty();
        }
        // TODO: Save cluster performance to the Postgres DB
        //        ClusterPerformanceBenchmark clusterPerformanceBenchmark = new ClusterPerformanceBenchmark();
        //        Set all the data and save to db
        //        this.clusterPerformanceBenchmarkRepository.save(clusterPerformanceBenchmark);
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
