package com.mpds.flinkautoscaler.application.service;

import com.mpds.flinkautoscaler.domain.model.events.DomainEvent;

public interface PredictionCacheService {

//    PredictionReported getLatestPredictionFor(LocalDateTime localDateTime);

//    PredictionReported getLatestLtPredictionFor(LocalDateTime localDateTime);
//
//    PredictionReported cachePredictionReported(PredictionReported predictionReported);

    DomainEvent getPredictionFrom(String eventType);

    DomainEvent cacheDomainEvent(DomainEvent domainEvent);
}
