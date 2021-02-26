package com.mpds.flinkautoscaler.application.service.impl;

import com.mpds.flinkautoscaler.application.service.PredictionCacheService;
import com.mpds.flinkautoscaler.domain.model.events.DomainEvent;
import com.mpds.flinkautoscaler.domain.model.events.ShorttermPredictionReported;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import static com.mpds.flinkautoscaler.infrastructure.config.CacheCustomizer.PREDICTION_CACHE;

@Service
@RequiredArgsConstructor
@Slf4j
public class PredictionCacheServiceImpl implements PredictionCacheService {

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

//    @Override
//    @CachePut(value = "predictions", key = "#predictionReported.occurredOn")
//    public PredictionReported cachePredictionReported(PredictionReported predictionReported) {
//        return predictionReported;
//    }
}
