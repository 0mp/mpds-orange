package com.mpds.flinkautoscaler.application.service;

import com.mpds.flinkautoscaler.domain.model.MetricTriggerPredictionsSnapshot;
import com.mpds.flinkautoscaler.domain.model.events.DomainEvent;

public interface CacheService {

//    PredictionReported getLatestPredictionFor(LocalDateTime localDateTime);

//    PredictionReported getLatestLtPredictionFor(LocalDateTime localDateTime);
//
//    PredictionReported cachePredictionReported(PredictionReported predictionReported);

    DomainEvent getPredictionFrom(String eventType);

    DomainEvent cacheDomainEvent(DomainEvent domainEvent);

    MetricTriggerPredictionsSnapshot getMetricTriggerPredictionsSnapshot(String snapshotCacheKey);

    MetricTriggerPredictionsSnapshot cacheSnapshot(MetricTriggerPredictionsSnapshot metricTriggerPredictionsSnapshot);

    String getLastFlinkSavepoint();

    String cacheFlinkSavepoint(String savepointPath);
}
