package com.mpds.flinkautoscaler.application.service.impl;

import com.mpds.flinkautoscaler.application.service.DomainEventService;
import com.mpds.flinkautoscaler.domain.model.events.DomainEvent;
import com.mpds.flinkautoscaler.infrastructure.config.FlinkProps;
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

    private final FlinkProps flinkProps;

    // Flink API paths
    private final String FLINK_SAVEPOINT_PATH = "/jobs/{jobId}/savepoints";
    private final String FLINK_SAVEPOINT_INFO_PATH = "/jobs/{jobId}/savepoints/{triggerId}";
    private final String FLINK_JAR_RUN_PATH = "/jars/{jarId}/run";

    public DomainEventServiceImpl(FlinkProps flinkProps) {
        this.flinkProps = flinkProps;
        this.webClient = WebClient.builder()
                .baseUrl(flinkProps.getBaseUrl())
                .build();
    }

    @Override
    // Method should process the data from the prediction topic
    public Mono<Void> processDomainEvent(DomainEvent domainEvent) {
        // 1. TODO: Process prediction data and decide action under certain conditions
        // Note: Redis could be implemented to save the state to decide if a rescale should be triggered or not

        // 2. Carry out action by using the WebClient to trigger rescaling
        // Target parallelism which has been calculated from previous step (TO BE DONE)
        int targetParallelism = 2;
        log.info("<<-- Start triggering Flink rescale  -->>");
        log.info("TargetParallelism: " + targetParallelism);

        // Check application.yml file if the Flink props match to the Flink job deployment
        // Use Postman to get the new values if required
        // 2.1 Create Savepoint for the job
        return this.createFlinkSavepoint(this.flinkProps.getJobId(), this.flinkProps.getSavepointDirectory(), true)
                // 2.2 Get savepoint path using the received request id
                // Wait with the request for 10 seconds so that the savepoint can complate
                .delayElement(Duration.ofSeconds(10))
                .flatMap(flinkSavepointResponse -> {
                    log.info("flinkSavepointResponse: " + flinkSavepointResponse.toString());
                    return getFlinkSavepointInfo(this.flinkProps.getJobId(), flinkSavepointResponse.getRequestId());
                })
                // 2.3 Start the job with the new parallelism using the savepoint path created from before
                .flatMap(flinkSavepointInfoResponse -> {
                    log.info("flinkSavepointInfoResponse: " + flinkSavepointInfoResponse.toString());
                    return runFlinkJob(this.flinkProps.getJarId(), this.flinkProps.getJobId(), this.flinkProps.getProgramArgs(), targetParallelism, flinkSavepointInfoResponse.getOperation().getLocation());
                })
                .flatMap(flinkRunJobResponse -> {
                    log.info("The job has been started successfully: " + flinkRunJobResponse.toString());
                    return Mono.empty();
                })
                .onErrorResume(throwable -> {
                    log.error("Flink Rescaling has failed: ", throwable);
                    return Mono.error(throwable);
                }).then();
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
                .onErrorResume(throwable -> Mono.error(throwable));
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
