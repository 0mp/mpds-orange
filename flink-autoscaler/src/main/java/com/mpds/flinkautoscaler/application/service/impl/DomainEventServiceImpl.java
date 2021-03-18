package com.mpds.flinkautoscaler.application.service.impl;

import com.mpds.flinkautoscaler.application.constants.FlinkConstants;
import com.mpds.flinkautoscaler.application.constants.PredictionConstants;
import com.mpds.flinkautoscaler.application.service.CacheService;
import com.mpds.flinkautoscaler.application.service.DomainEventService;
import com.mpds.flinkautoscaler.application.service.FlinkApiService;
import com.mpds.flinkautoscaler.application.service.PrometheusApiService;
import com.mpds.flinkautoscaler.domain.model.ClusterPerformanceBenchmark;
import com.mpds.flinkautoscaler.domain.model.events.LongtermPredictionReported;
import com.mpds.flinkautoscaler.domain.model.events.MetricReported;
import com.mpds.flinkautoscaler.domain.model.events.ShorttermPredictionReported;
import com.mpds.flinkautoscaler.domain.model.stats.StatsRecord;
import com.mpds.flinkautoscaler.domain.model.stats.StatsWriter;
import com.mpds.flinkautoscaler.infrastructure.config.FlinkProps;
import com.mpds.flinkautoscaler.infrastructure.repository.ClusterPerformanceBenchmarkRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
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

    private final CacheService cacheService;
    private final FlinkApiService flinkApiService;
    private final PrometheusApiService prometheusApiService;

    private final ClusterPerformanceBenchmarkRepository clusterPerformanceBenchmarkRepository;

    private final FlinkProps flinkProps;

    public LocalDateTime RESCALE_COOLDOWN;

    private final static float UPPERTHRESHOLD = 3;
    private final static float LOWERTHRESHOLD = 0.4f;

    private final static float MAX_SECONDS_TO_PROCESS_LAG = 24;
    private final static float MAX_CPU_UTILIZATION = 60;
    private final static float MAX_MEMORY_USAGE = 0.9f;

    private final static float MIN_SECONDS_TO_PROCESS_LAG = 8;
    private final static float MIN_CPU_UTILIZATION = 0.4f;
    private final static float MIN_MEMORY_USAGE = 0.5f;

    private final static int LT_PREDICT_MINUTES = 4;
    private final static float LT_ERROR_FRACTION_THRESHOLD = 0.8f;
    private final static int STEPS_NO_ERROR_VIOLATION = 3;

    private final static float TARGET_RECORDS_OVERESTIMATION_FACTOR = 1.45f;
    private final static float FLINK_RECORDS_IN_DISCOUNT_FACTOR = 0.55f;

    private final static float EXPECTED_SECONDS_TO_RESCALE = 3;
    private final static float LOWER_LAG_TIME_THRESHOLD = 3;
    private final static int MAX_POSSIBLE_PARALLELISM = 8;

    private final static float FUTURE_THRESHOLD_SECONDS = 180;

    private int noConsecutiveErrorViolation = 0;

    private LongtermPredictionReported lastLongTermPrediction = null;

    private StatsWriter statsWriter = new StatsWriter();
    private StatsRecord statsRecord = new StatsRecord();

    public static long flinkRestartTimeInMillis;

    public DomainEventServiceImpl(CacheService cacheService, FlinkProps flinkProps, ClusterPerformanceBenchmarkRepository clusterPerformanceBenchmarkRepository, FlinkApiService flinkApiService, PrometheusApiService prometheusApiService) {
        this.cacheService = cacheService;
        this.flinkProps = flinkProps;
        this.clusterPerformanceBenchmarkRepository = clusterPerformanceBenchmarkRepository;
        this.flinkApiService = flinkApiService;
        this.prometheusApiService = prometheusApiService;
    }

    private float dampenPredictions(float shortTerm, float longTerm, float kafkaMessages) {
        float prediction;
        if (Float.isNaN(shortTerm) && Float.isNaN(longTerm)) {
            if (Float.isNaN(kafkaMessages)) return 0;
            return kafkaMessages;
        } else if (Float.isNaN(shortTerm)) {
            prediction = longTerm;
        } else if (Float.isNaN(longTerm)) {
            prediction = shortTerm;
        } else {
            prediction = (shortTerm + longTerm) / 2;
        }
        if (prediction > UPPERTHRESHOLD * kafkaMessages) {
            return UPPERTHRESHOLD * kafkaMessages;
        }
        return Math.max(prediction, LOWERTHRESHOLD * kafkaMessages);
    }

    public float getTimeToProcessLag(float kafkaLag, float kafkaMessagesPerSecond, float flinkRecordsInPerSecond) {

        if (Double.isNaN(kafkaLag)) {
            log.info("lag is NaN");
            kafkaLag = 0;
        }

        if (flinkRecordsInPerSecond == 0 || Double.isNaN(flinkRecordsInPerSecond)) {
            log.info("flinkRecordsInPerSecond is 0");
            return 0;
        }

        if (kafkaLag == 0) {
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
        if (longTermPrediction != null)
            log.info("LT error: " + longTermPrediction.calcPredictedMessagesPerSecond(timeWantedPredictionFor));
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

        // Determine Weight preference of prediction models
        log.info("Aggregate Predictions: " + stPrediction + " - " + ltPrediciton + " - " + kafkaMessagesPerSecond);
        float aggregatePrediction = dampenPredictions(stPrediction, ltPrediciton, kafkaMessagesPerSecond);
        statsRecord.setAggregatePrediction(aggregatePrediction);

        return this.flinkApiService.getFlinkState().publishOn(Schedulers.boundedElastic())
                .flatMap(flinkState -> {
                    log.info("Current Flink state: " + flinkState);
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
                        log.info("current time to process Lag: " + processLagSeconds);

                        if (processFutureLagSeconds > MAX_SECONDS_TO_PROCESS_LAG ||
                                cpu > MAX_CPU_UTILIZATION ||
                                memory > MAX_MEMORY_USAGE) {
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
                                        if (flinkApiService.getActualParallelism() != targetParallelism) {
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
                                            return this.flinkApiService.rescaleFlinkCluster(targetParallelism, metricReported, shortTermPrediction, longTermPrediction, aggregatePrediction);
                                        } else {
                                            log.info("Flink rescaling was NOT executed since current and target parallelism are the same!");
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
                            if (this.cacheService.getLastFlinkSavepoint() != null) {
                                return this.flinkApiService.startFlinkCluster(targetParallelism, metricReported, shortTermPrediction, longTermPrediction, cacheService.getLastFlinkSavepoint(), aggregatePrediction);
                            }
                            return this.flinkApiService.startFlinkCluster(targetParallelism, metricReported, shortTermPrediction, longTermPrediction, null, aggregatePrediction);
                        });
                    }
                    log.info("The Flink was NOT in the state: " + FlinkConstants.RUNNING_STATE);
                    statsRecord.setParallelism(-1);
                    statsRecord.setLagLatency(Double.NaN);
                    statsWriter.addRecord(statsRecord);
                    return Mono.empty();
                });
    }

    public double getTargetFlinkRecordsIn(MetricReported metricReported, float aggregatePrediction) {
        float kafkaLag = metricReported.getKafkaLag() + metricReported.getKafkaMessagesPerSecond() * EXPECTED_SECONDS_TO_RESCALE;
        float desiredTimeToProcessLag = (MAX_SECONDS_TO_PROCESS_LAG + MIN_SECONDS_TO_PROCESS_LAG) / 2;
        log.info("calculation of Target FlinkRecordsin: kafkaLag: " + kafkaLag + " - aggregatePrediction: " + aggregatePrediction + " - desiredTime: " + desiredTimeToProcessLag);
        double targetFlinkIn = ((kafkaLag + Math.sqrt(Math.pow(kafkaLag, 2) + 4 * desiredTimeToProcessLag * aggregatePrediction * kafkaLag)) / (2 * desiredTimeToProcessLag));
        if (targetFlinkIn > aggregatePrediction) {
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
        return this.prometheusApiService.getPrometheusMetric(this.prometheusApiService.getFlinkNumOfTaskManagers(currentDateTimeString))
                .map(prometheusMetric -> {
                    log.debug("Prometheus - Flink number of task managers response: " + prometheusMetric.toString());
                    if (prometheusMetric.getData().getResult() != null && prometheusMetric.getData().getResult().size() > 0) {
                        return Integer.parseInt(prometheusMetric.getData().getResult().get(0).getValue()[1].toString());
                    }
                    return 0;
                })
                .flatMap(currentParallel -> {
                    this.flinkApiService.setActualParallelism(currentParallel);
                    if (currentParallel == 0)
                        log.error("No task manager is running on the Flink cluster or the retrieved Prometheus metric is not correct!");
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

                                if (infimumParallelism > 0) {
                                    log.info("infimum from db: " + infimumParallelism);
                                }

                                if (scaleUp) {
                                    if (aboveParallelism > currentParallel) {

                                        if (infimumParallelism > 0) {
                                            return considerInfimumExploration(aboveParallelism, aboveRate, infimumParallelism, infimumRate, targetFlinkRecordsIn);
                                        }
                                    } else {
                                        return linearCalcFallBack(metricReported, aggregatePrediction, currentParallel, scaleUp);
                                    }
                                }
                                if (!scaleUp) {
                                    if (aboveParallelism < currentParallel) {
                                        if (infimumParallelism > 0) {
                                            return considerInfimumExploration(aboveParallelism, aboveRate, infimumParallelism, infimumRate, targetFlinkRecordsIn);
                                        }
                                    } else {
                                        return currentParallel;
                                    }
                                }
                                return aboveParallelism;
                            })
                            .switchIfEmpty(Mono.just(linearCalcFallBack(metricReported, aggregatePrediction, currentParallel, scaleUp)));
                });
    }

    public int considerInfimumExploration(int aboveParallelism, double aboveRate, int infimumParallelism, double infimumRate, double targetFlinkRecordsIn) {
        int dist = aboveParallelism - infimumParallelism;
        if (dist > 1) {
            double estimatedRate = infimumRate + (aboveRate - infimumRate) / dist * (dist - 1.1);
            if (Math.abs(estimatedRate - targetFlinkRecordsIn) < Math.abs(aboveRate - targetFlinkRecordsIn)) {
                log.info("infimum exploration occured: " + (aboveParallelism - 1));
                return aboveParallelism - 1;
            }
        } else if (infimumParallelism > aboveParallelism) {
            log.info("Table inconsistency: Switching to higher parallelism with lower rate for exploration");
            return infimumParallelism;
        }
        return aboveParallelism;
    }

    public int linearCalcFallBack(MetricReported metrics, float aggregatePrediction, int currentParallel, boolean scaleUp) {
        float desiredTimeToProcessLag = (MAX_SECONDS_TO_PROCESS_LAG + MIN_SECONDS_TO_PROCESS_LAG) / 2;
        float kafkaLag = metrics.getKafkaLag() + metrics.getKafkaMessagesPerSecond() * EXPECTED_SECONDS_TO_RESCALE;
        float predictedTimeToProcessLag = getTimeToProcessLag(kafkaLag, aggregatePrediction, metrics.getFlinkNumberRecordsIn());
        int linearScaleParallel = (int) Math.ceil(currentParallel * (predictedTimeToProcessLag / desiredTimeToProcessLag));
        log.info("Linear Scaling - Target Parallelism: " + linearScaleParallel);

        if (linearScaleParallel > MAX_POSSIBLE_PARALLELISM) return MAX_POSSIBLE_PARALLELISM;
        return Math.max(linearScaleParallel, 1);

    }
}
