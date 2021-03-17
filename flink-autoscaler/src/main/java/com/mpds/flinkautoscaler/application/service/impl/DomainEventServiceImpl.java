package com.mpds.flinkautoscaler.application.service.impl;

import com.mpds.flinkautoscaler.application.constants.FlinkConstants;
import com.mpds.flinkautoscaler.application.constants.PredictionConstants;
import com.mpds.flinkautoscaler.application.service.CacheService;
import com.mpds.flinkautoscaler.application.service.DomainEventService;
import com.mpds.flinkautoscaler.application.service.FlinkApiService;
import com.mpds.flinkautoscaler.application.service.PrometheusApiService;
import com.mpds.flinkautoscaler.domain.model.ClusterPerformanceBenchmark;
import com.mpds.flinkautoscaler.domain.model.MetricTriggerPredictionsSnapshot;
import com.mpds.flinkautoscaler.domain.model.events.LongtermPredictionReported;
import com.mpds.flinkautoscaler.domain.model.events.MetricReported;
import com.mpds.flinkautoscaler.domain.model.events.ShorttermPredictionReported;
import com.mpds.flinkautoscaler.domain.model.stats.StatsRecord;
import com.mpds.flinkautoscaler.domain.model.stats.StatsWriter;
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
import org.springframework.data.relational.core.sql.In;
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
import java.time.format.DateTimeFormatter;

@Service
@Slf4j
public class DomainEventServiceImpl implements DomainEventService {

    private final WebClient webClient;

    private final CacheService cacheService;
    private final FlinkApiService flinkApiService;
    private final PrometheusApiService prometheusApiService;

    private final ClusterPerformanceBenchmarkRepository clusterPerformanceBenchmarkRepository;

    private final FlinkProps flinkProps;

    // Flink API paths
    private final String FLINK_SAVEPOINT_PATH = "/jobs/{jobId}/savepoints";
    private final String FLINK_SAVEPOINT_INFO_PATH = "/jobs/{jobId}/savepoints/{triggerId}";
    private final String FLINK_JAR_RUN_PATH = "/jars/{jarId}/run";

    public LocalDateTime RESCALE_COOLDOWN;

    private final static float UPPERTHRESHOLD = 3;
    private final static float LOWERTHRESHOLD = 0.4f;

    private final static float MAX_SECONDS_TO_PROCESS_LAG = 24;
    private final static float MAX_CPU_UTILIZATION = 60;
    private final static float MAX_MEMORY_USAGE = 0.9f;

    private final static float MIN_SECONDS_TO_PROCESS_LAG = 7;
    private final static float MIN_CPU_UTILIZATION = 0.4f;
    private final static float MIN_MEMORY_USAGE = 0.5f;

    private final static int LT_PREDICT_MINUTES = 4;
    private final static float LT_ERROR_FRACTION_THRESHOLD = 0.8f;
    private final static int STEPS_NO_ERROR_VIOLATION = 3;

    private final static float TARGET_RECORDS_OVERESTIMATION_FACTOR = 1.45f;
    private final static float FLINK_RECORDS_IN_DISCOUNT_FACTOR = 0.55f;

    // TODO Add Rescale time to table
    private final static float EXPECTED_SECONDS_TO_RESCALE = 10;
    private final static float LOWER_LAG_TIME_THRESHOLD = 4;
    private final static int MAX_POSSIBLE_PARALLELISM = 8;

    private final static float FUTURE_THRESHOLD_SECONDS = 180;

    private int noConsecutiveErrorViolation = 0;

    private LongtermPredictionReported lastLongTermPrediction = null;

    private StatsWriter statsWriter = new StatsWriter();
    private StatsRecord statsRecord = new StatsRecord();

    @Getter
    @Setter
    private int actualParallelism;


    public DomainEventServiceImpl(FlinkProps flinkProps, ClusterPerformanceBenchmarkRepository clusterPerformanceBenchmarkRepository, CacheService cacheService, FlinkApiService flinkApiService, PrometheusApiService prometheusApiService) {
        this.flinkProps = flinkProps;
        this.clusterPerformanceBenchmarkRepository = clusterPerformanceBenchmarkRepository;
        this.cacheService = cacheService;
        this.webClient = WebClient.builder()
                .baseUrl(flinkProps.getBaseUrl())
                .build();
        this.flinkApiService = flinkApiService;
        this.prometheusApiService = prometheusApiService;
    }

    private float dampenPredictions(float shortTerm, float longTerm, float kafkaMessages) {
        float prediction;
        if(Float.isNaN(shortTerm) && Float.isNaN(longTerm)){
            if(Float.isNaN(kafkaMessages)) return 0;
            return kafkaMessages;
        } else if(Float.isNaN(shortTerm)) {
            prediction = longTerm;
        } else if(Float.isNaN(longTerm)){
            prediction = shortTerm;
        } else {
            prediction = (shortTerm + longTerm) / 2;
        }
        if (prediction > UPPERTHRESHOLD * kafkaMessages) {
            return UPPERTHRESHOLD * kafkaMessages;
        }
        return Math.max(prediction, LOWERTHRESHOLD * kafkaMessages);
    }

    public float getTimeToProcessLag(float kafkaLag, float kafkaMessagesPerSecond, float flinkRecordsInPerSecond){

        if (Double.isNaN(kafkaLag)) {
            log.info("lag is NaN");
            kafkaLag = 0;
        }

        if(flinkRecordsInPerSecond == 0 || Double.isNaN(flinkRecordsInPerSecond)){
            log.info("flinkRecordsInPerSecond is 0");
            return 0;
        }

        if (kafkaLag == 0){
            Math.max(0, LOWER_LAG_TIME_THRESHOLD * (kafkaMessagesPerSecond - flinkRecordsInPerSecond));
        }

        float kafkaCurrentLagLatency = kafkaLag / flinkRecordsInPerSecond;

        return (kafkaMessagesPerSecond * kafkaCurrentLagLatency) / flinkRecordsInPerSecond + kafkaCurrentLagLatency;
    }

    @Override
    // Method should process the data from the prediction topic
    public Mono<Void> processDomainEvent(MetricReported metricReported) {
        float kafkaMessagesPerSecond = metricReported.getKafkaMessagesPerSecond();
        log.info("KAFAKA MESSAGES PER SECOND: " + kafkaMessagesPerSecond);
        statsRecord.setWithMetricReported(metricReported);
        LongtermPredictionReported longTermPrediction = (LongtermPredictionReported) this.cacheService.getPredictionFrom(PredictionConstants.LONG_TERM_PREDICTION_EVENT_NAME);

        LocalDateTime timeWantedPredictionFor = LocalDateTime.now().plusMinutes(LT_PREDICT_MINUTES);

        if (lastLongTermPrediction != null) {
            float oldPrediction = lastLongTermPrediction.calcPredictedMessagesPerSecond(metricReported.getOccurredOn());
            if (Math.abs(oldPrediction - kafkaMessagesPerSecond) < LT_ERROR_FRACTION_THRESHOLD * kafkaMessagesPerSecond) {
                noConsecutiveErrorViolation++;
            } else {
                noConsecutiveErrorViolation = 0;
            }
        }

        float ltPrediciton = Float.NaN;
        log.info("LT erro: " + longTermPrediction.calcPredictedMessagesPerSecond(timeWantedPredictionFor));
        if (longTermPrediction != null && noConsecutiveErrorViolation >= STEPS_NO_ERROR_VIOLATION) {
            ltPrediciton = longTermPrediction.calcPredictedMessagesPerSecond(timeWantedPredictionFor);
        } else if (longTermPrediction == null) {
            log.info("No LT prediction found in cache!");
        } else {
            log.info("LT consecutive no error violation: " + noConsecutiveErrorViolation);
        }
        statsRecord.setLtPrediction(ltPrediciton);

        lastLongTermPrediction = longTermPrediction;

        ShorttermPredictionReported shortTermPrediction = (ShorttermPredictionReported) this.cacheService.getPredictionFrom(PredictionConstants.SHORT_TERM_PREDICTION_EVENT_NAME);

        float stPrediction = Float.NaN;
        if (shortTermPrediction != null) {
            log.info("Current ST prediction: " + shortTermPrediction.toString());
            stPrediction = shortTermPrediction.getPredictedWorkload();
        } else {
            log.info("No ST prediction found in cache!");
        }
        statsRecord.setStPrediction(stPrediction);

        // Determine Weight prefence of prediction models
        log.info("Aggregate Predictions: " + stPrediction + " - " + ltPrediciton + " - " + kafkaMessagesPerSecond);
        float aggregatePrediction = dampenPredictions(stPrediction, ltPrediciton, kafkaMessagesPerSecond);
        statsRecord.setAggregatePrediction(aggregatePrediction);

        return this.flinkApiService.getFlinkState().publishOn(Schedulers.boundedElastic())
                .flatMap(flinkState -> {
                    log.info("Current Flink state: " + flinkState);
                    // 2. Carry out action by using the WebClient to trigger rescaling
                    // Target parallelism which has been calculated from previous step (TO BE DONE)
//                    int targetParallelism = 1; // default value
                    boolean rescale = false;
                    Mono<Integer> targetParallelismMono = Mono.just(1);

                    float cpu = metricReported.getCpuUtilization();
                    float memory = metricReported.getMemoryUsage();
                    float flinkRecordsIn = metricReported.getFlinkNumberRecordsIn() * FLINK_RECORDS_IN_DISCOUNT_FACTOR;
                    float lag = metricReported.getKafkaLag();
                    float processLagSeconds = getTimeToProcessLag(lag, kafkaMessagesPerSecond, flinkRecordsIn);
                    float averageRate = (aggregatePrediction + kafkaMessagesPerSecond) / 2;
                    float estimateLag = Math.max(FUTURE_THRESHOLD_SECONDS * (averageRate - flinkRecordsIn) + lag, 0);
                    float processFutureLagSeconds = getTimeToProcessLag(estimateLag, aggregatePrediction, flinkRecordsIn);

                    statsRecord.setLagLatency(processLagSeconds);

                    if (FlinkConstants.RUNNING_STATE.equals(flinkState)) {
                        log.info("Evaluate Metrics");
                        // Scale Up

                        log.info("current time to process Lag: " + processLagSeconds);

                        if (processFutureLagSeconds > MAX_SECONDS_TO_PROCESS_LAG ||
                                cpu > MAX_CPU_UTILIZATION ||
                                memory > MAX_MEMORY_USAGE) {
//                            metricReported.getMaxJobLatency() > 500
                            log.info("---->>>>>> SCALE UP, Get higher parallelism for predicted load : " + aggregatePrediction);
                            if (kafkaMessagesPerSecond != 0) {
                                targetParallelismMono = getTargetParallelism(metricReported, aggregatePrediction, true); // Response from DB
                            }
                            rescale = true; // Should be true, left false for testing
                        }
                        // Scale Down
                        if (processFutureLagSeconds < MIN_SECONDS_TO_PROCESS_LAG &&
                                metricReported.getCpuUtilization() < MIN_CPU_UTILIZATION &&
                                metricReported.getMemoryUsage() < MIN_MEMORY_USAGE) {
//                            && metricReported.getMaxJobLatency() < 100
                            log.info("<<<<<---- SCALE DOWN, Get lower parallelism for predicted load :" + aggregatePrediction);

                            // Request from performance table parallelism for predicted load
                            if (metricReported.getKafkaMessagesPerSecond() != 0) {
                                targetParallelismMono = getTargetParallelism(metricReported, aggregatePrediction, false); // Response from DB
                            }

                            rescale = true; // Should be true, left false for testing

                        }
                        statsWriter.addRecord(statsRecord);
                        if (rescale) {
                            log.info("<<-- Start triggering Flink rescale  -->>");
                            return targetParallelismMono
                                    // Default to paralleism 1 if no value is returned
                                    .switchIfEmpty(Mono.just(1))
                                    .flatMap(targetParallelism -> {
                                        log.info("TargetParallelism: " + targetParallelism);
                                        if (actualParallelism != targetParallelism) {
                                            LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
                                            log.debug("Now:  " + now.toString());
                                            if (RESCALE_COOLDOWN == null || Duration.between(RESCALE_COOLDOWN, now).abs().getSeconds() > this.flinkProps.getCooldownDuration()) {
                                                log.info("Resetting RESCALE COOLDOW: " + now.toString());
                                                RESCALE_COOLDOWN = now;
                                            } else {
                                                log.info(" ++ + ++ + + +  BLOCKING RESCALE DUE TO COOLDOWN + + + + ++ +");
                                                log.info("Rescale Cooldown: " + RESCALE_COOLDOWN.toString());
                                                return Mono.empty();
                                            }
                                            return rescaleFlinkCluster(targetParallelism, metricReported, shortTermPrediction, longTermPrediction, aggregatePrediction);
                                        } else {
                                            log.info("No Flink rescaling triggered since current and target parallelism are the same!");
                                            return Mono.empty();
                                        }
                                    });
                        } else {
                            return Mono.empty();
                        }
                    } else if (FlinkConstants.CANCELED_STATE.equals(flinkState)) {
                        return targetParallelismMono.flatMap(targetParallelism -> {
                            log.info("Flink Cluster is in the canceled state!");
                            log.info("Trying to restart the Flink job with the last successful parallelism...");
                            LocalDateTime now = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
                            log.debug("Now:  " + now.toString());

                            if (RESCALE_COOLDOWN == null || Duration.between(RESCALE_COOLDOWN, now).abs().getSeconds() > 15) {
                                log.info("Flink was in the state " + FlinkConstants.CANCELED_STATE + ": Resetting RESCALE COOLDOWN: " + now.toString());
                                RESCALE_COOLDOWN = now;
                            } else {
                                log.info("Flink was in the state " + FlinkConstants.CANCELED_STATE + ": ++ + ++ + + +  BLOCKING RESCALE DUE TO COOLDOWN + + + + ++ +");
                                log.info("Flink was in the state " + FlinkConstants.CANCELED_STATE + ": Rescale Cooldown: " + RESCALE_COOLDOWN.toString());
                                return Mono.empty();
                            }
                            if(this.cacheService.getLastFlinkSavepoint()!=null) {
                                return startFlinkCluster(targetParallelism, metricReported, shortTermPrediction, longTermPrediction, cacheService.getLastFlinkSavepoint(), aggregatePrediction);
                            }
                            return startFlinkCluster(targetParallelism, metricReported, shortTermPrediction, longTermPrediction, null, aggregatePrediction);
                        });
                    }
                    log.info("The Flink was NOT in the state: " + FlinkConstants.RUNNING_STATE);
                    statsRecord.setParallelism(-1);
                    statsRecord.setLagLatency(Double.NaN);
                    statsWriter.addRecord(statsRecord);
                    return Mono.empty();
                });
    }

    public Mono<Void> rescaleFlinkCluster(int targetParallelism, MetricReported metricReported, ShorttermPredictionReported shortTermPrediction, LongtermPredictionReported longTermPrediction, float aggregatePrediction) {
        log.info(" ############################## NOW RESCALING THE JOB with parallelism:  " + targetParallelism + " ##############################");
        return this.createFlinkSavepoint(this.flinkProps.getJobId(), this.flinkProps.getSavepointDirectory(), true)
                // Get savepoint path using the received request id
                // Wait with the request for 7 seconds so that the savepoint can complete
                .delayElement(Duration.ofSeconds(7))
                .flatMap(flinkSavepointResponse -> {
                    log.info("flinkSavepointResponse: " + flinkSavepointResponse.toString());
                    return getFlinkSavepointInfo(this.flinkProps.getJobId(), flinkSavepointResponse.getRequestId())
                            .flatMap(flinkSavepointInfoResponse -> {
                                log.info("FLINKSAVEPOINT INFO RESPONSE:::::  " + flinkSavepointInfoResponse);
                                if (flinkSavepointInfoResponse.getOperation() == null || flinkSavepointInfoResponse.getOperation().getLocation() == null) {
                                    log.error("Flink saveppoint operation is null for Flink Request ID:  " + flinkSavepointResponse.getRequestId());
                                    log.debug(("Trying to get savepoint again...."));
                                    return Mono.empty()
                                            .delayElement(Duration.ofSeconds(5))
                                            .flatMap(o -> getFlinkSavepointInfo(this.flinkProps.getJobId(), flinkSavepointResponse.getRequestId())
                                                    .flatMap(flinkSavepointInfoResponse1 -> {
                                                        if (StringUtils.isEmpty(flinkSavepointInfoResponse1.getOperation().getLocation())) {
                                                            log.error("Second call for savepoint path failed: " + flinkSavepointInfoResponse1.toString());
                                                            return Mono.just(flinkSavepointInfoResponse1);
                                                        }
                                                        return Mono.just(flinkSavepointInfoResponse1);
                                                    }));
                                }
                                return Mono.just(flinkSavepointInfoResponse);
                            });
                })
//                                    .delayElement(Duration.ofSeconds(5))
                // Start the job with the new parallelism using the savepoint path created from before
                .flatMap(flinkSavepointInfoResponse -> {
                    log.info("flinkSavepointInfoResponse: " + flinkSavepointInfoResponse.toString());
                    this.cacheService.cacheFlinkSavepoint(flinkSavepointInfoResponse.getOperation().getLocation());
                    return runFlinkJob(this.flinkProps.getJarId(), this.flinkProps.getJobId(), this.flinkProps.getProgramArgs(), targetParallelism, flinkSavepointInfoResponse.getOperation().getLocation());
                })
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

    public double getTargetFlinkRecordsIn(MetricReported metricReported, float aggregatePrediction){
        float kafkaLag = metricReported.getKafkaLag() + metricReported.getKafkaMessagesPerSecond() * EXPECTED_SECONDS_TO_RESCALE;
        float desiredTimeToProcessLag = (MAX_SECONDS_TO_PROCESS_LAG + MIN_SECONDS_TO_PROCESS_LAG) / 2;
        log.info("calculation of Target FlinkRecordsin: kafkaLag: " + kafkaLag + " - aggregatePrediction: " + aggregatePrediction + " - desiredTime: " + desiredTimeToProcessLag);
        double targetFlinkIn =  ((kafkaLag + Math.sqrt(Math.pow(kafkaLag, 2) + 4 * desiredTimeToProcessLag * aggregatePrediction * kafkaLag)) / (2 * desiredTimeToProcessLag));
        if(targetFlinkIn > aggregatePrediction){
            return targetFlinkIn;
        }
        return aggregatePrediction;
    }


    public Mono<Integer> getTargetParallelism(MetricReported metricReported, float aggregatePrediction, boolean scaleUp) {
        log.debug("aggregatePrediction: " + aggregatePrediction);
        LocalDateTime currentDateTime = LocalDateTime.ofInstant(Instant.now(), ZoneOffset.UTC);
        String currentDateTimeString = currentDateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'"));
        float targetFlinkRecordsIn = (float) getTargetFlinkRecordsIn(metricReported, aggregatePrediction) * TARGET_RECORDS_OVERESTIMATION_FACTOR;
        log.info("Target FlinkRecordsIn: " + targetFlinkRecordsIn);
//        return this.flinkApiService.getCurrentFlinkClusterParallelism()
        return this.prometheusApiService.getPrometheusMetric(this.prometheusApiService.getFlinkNumOfTaskManagers(currentDateTimeString))
                .map(prometheusMetric -> {
                    log.debug("Prometheus - Flink number of task managers response: " + prometheusMetric.toString());
                    if(prometheusMetric.getData().getResult() != null && prometheusMetric.getData().getResult().size() > 0) {
                        return Integer.parseInt(prometheusMetric.getData().getResult().get(0).getValue()[1].toString());
                    }
                    return 0;
                })
                .flatMap(currentParallel -> {
                    setActualParallelism(currentParallel);
                    if(currentParallel==0) log.error("No task manager is running on the Flink cluster or the retrieved Prometheus metric is not correct!");
                    log.debug("currentParallel: " + currentParallel);
                    statsRecord.setParallelism(currentParallel);
                    Mono<ClusterPerformanceBenchmark> infimumParallelismMaxRate = this.clusterPerformanceBenchmarkRepository.findInfimumParallelismWithMaxRate(targetFlinkRecordsIn).defaultIfEmpty(new ClusterPerformanceBenchmark());//.switchIfEmpty(this.clusterPerformanceBenchmarkRepository.findOptimalParallelismWithMaxRate(targetFlinkRecordsIn));
                    return this.clusterPerformanceBenchmarkRepository.findOptimalParallelismWithMaxRate(targetFlinkRecordsIn)
                            .zipWith(infimumParallelismMaxRate)
                            .map(lookupResult -> {
                                log.info("Current Flink Parallelism: " + currentParallel);
                                log.info("above from DB: " + lookupResult.getT1().getParallelism());
                                int aboveParallelism = lookupResult.getT1().getParallelism();
                                double aboveRate = lookupResult.getT1().getMaxRate();
                                int infimumParallelism = lookupResult.getT2().getParallelism();
                                double infimumRate = lookupResult.getT2().getMaxRate();

                                if(infimumParallelism > 0){
                                    log.info("infimum from db: " + infimumParallelism);
                                }

                                if (scaleUp) {
                                    if(aboveParallelism > currentParallel){

                                        if(infimumParallelism > 0){
                                            return considerInfimumExploration(aboveParallelism, aboveRate, infimumParallelism, infimumRate, targetFlinkRecordsIn);
                                        }
                                    } else {
                                        return linearCalcFallBack(metricReported, aggregatePrediction, currentParallel, scaleUp);
                                    }
                                }
                                if (!scaleUp) {

                                    if(aboveParallelism < currentParallel){
                                        if(infimumParallelism > 0) {
                                            return considerInfimumExploration(aboveParallelism, aboveRate, infimumParallelism, infimumRate, targetFlinkRecordsIn);
                                        }
                                    } else {
                                        return currentParallel;
                                    }
                                }
                                return aboveParallelism;
                            })
                            .switchIfEmpty(Mono.just(linearCalcFallBack(metricReported, aggregatePrediction, currentParallel, scaleUp)));
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

    public int considerInfimumExploration(int aboveParallelism, double aboveRate, int infimumParallelism, double infimumRate, double targetFlinkRecordsIn){
        int dist = aboveParallelism - infimumParallelism;
        if(dist > 1){
            double estimatedRate =  infimumRate + (aboveRate - infimumRate)/dist * (dist-1.1);
            if(Math.abs(estimatedRate - targetFlinkRecordsIn) < Math.abs(aboveRate - targetFlinkRecordsIn)){
                log.info("infimum exploration occured: " + (aboveParallelism - 1));
                return aboveParallelism - 1;
            }
        } else if (infimumParallelism > aboveParallelism){
            log.info("Table inconsistency: Switching to higher parallelism with lower rate for exploration");
            return infimumParallelism;
        }
        return aboveParallelism;
    }

    public int calculateNewParallelism(int currentParallel, MetricReported metricReported, float aggregatePrediction) {
        log.info("currentParallel: " + currentParallel + " --- Kafka Messages per second: " + metricReported.getKafkaMessagesPerSecond() + " -- aggregatePrediction: " + aggregatePrediction);
        log.info("--Calculated new parallelism: " + (int) (currentParallel / metricReported.getKafkaMessagesPerSecond() * aggregatePrediction));
        return (int) Math.ceil((currentParallel / metricReported.getKafkaMessagesPerSecond() * aggregatePrediction));
    }

    public int linearCalcFallBack(MetricReported metrics, float aggregatePrediction, int currentParallel, boolean scaleUp) {

        float desiredTimeToProcessLag = (MAX_SECONDS_TO_PROCESS_LAG + MIN_SECONDS_TO_PROCESS_LAG) / 2;
        float kafkaLag = metrics.getKafkaLag() + metrics.getKafkaMessagesPerSecond() * EXPECTED_SECONDS_TO_RESCALE;
        float predictedTimeToProcessLag = getTimeToProcessLag(kafkaLag, aggregatePrediction, metrics.getFlinkNumberRecordsIn());
        int linearScaleParallel = (int) Math.ceil(currentParallel * (predictedTimeToProcessLag/desiredTimeToProcessLag));
        log.info("Linear Scaling - Target Parallelism: " + linearScaleParallel);

        if(linearScaleParallel > MAX_POSSIBLE_PARALLELISM) return MAX_POSSIBLE_PARALLELISM;
        if(linearScaleParallel < 1) return 1;

        return linearScaleParallel;

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
        FlinkRunJobRequest flinkRunJobRequest;
        if(!StringUtils.isEmpty(savepointPath)) {
            flinkRunJobRequest = new FlinkRunJobRequest(jobId, programArgs, parallelism, savepointPath);
        } else {
            flinkRunJobRequest = FlinkRunJobRequest.builder().jobId(jobId).programArgs(programArgs).parallelism(parallelism).build();
        }
        return this.webClient.post()
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
                            .flatMap(unused -> this.flinkApiService.getFlinkState())
                            .flatMap(flinkState -> {
                                if (FlinkConstants.CANCELED_STATE.equals(flinkState)) {
                                    log.info("Job has been canceled. Trying to restart the job...");

                                    return this.webClient.post()
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
