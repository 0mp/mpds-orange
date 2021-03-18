package com.mpds.flinkautoscaler.application.service.impl;

import com.mpds.flinkautoscaler.application.constants.FlinkConstants;
import com.mpds.flinkautoscaler.application.service.CacheService;
import com.mpds.flinkautoscaler.domain.model.MetricTriggerPredictionsSnapshot;
import com.mpds.flinkautoscaler.domain.model.events.DomainEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import static com.mpds.flinkautoscaler.infrastructure.config.CacheCustomizer.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheServiceImpl implements CacheService {

    @Override
    @Cacheable(value = PREDICTION_CACHE)
    public DomainEvent getPredictionFrom(String eventType) {
        log.info("The cache is empty for eventType: " + eventType);
        return null;
    }

    @Override
    @CachePut(value = PREDICTION_CACHE, key = "#domainEvent.eventType()")
    public DomainEvent cacheDomainEvent(DomainEvent domainEvent) {
        return domainEvent;
    }

    @Override
    @Cacheable(value = TRIGGER_PREDICTIONS_SNAPSHOT_CACHE)
    public MetricTriggerPredictionsSnapshot getMetricTriggerPredictionsSnapshot(String snapshotCacheKey) {
        log.info("No snapshot was found in the cache with key: " + snapshotCacheKey);
        return null;
    }

    @Override
    @CachePut(value = TRIGGER_PREDICTIONS_SNAPSHOT_CACHE, key = "#metricTriggerPredictionsSnapshot.snapshotCacheKey")
    public MetricTriggerPredictionsSnapshot cacheSnapshot(MetricTriggerPredictionsSnapshot metricTriggerPredictionsSnapshot) {
        return metricTriggerPredictionsSnapshot;
    }

    @Override
    @Cacheable(value = FLINK_SAVEPOINTS_CACHE, key = "'lastFlinkSavepoint'")
    public String getLastFlinkSavepoint() {
        log.info("No Flink savepoint was found in the cache with key: " + FlinkConstants.LAST_SAVEPOINT_PATH_CACHE_KEY);
        return null;
    }

    @Override
    @CachePut(value = FLINK_SAVEPOINTS_CACHE, key = "'lastFlinkSavepoint'")
    public String cacheFlinkSavepoint(String savepointPath) {
        return savepointPath;
    }

}
