package com.mpds.flinkautoscaler.infrastructure.config;

import org.springframework.boot.autoconfigure.cache.CacheManagerCustomizer;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.stereotype.Component;

import java.util.Collections;

@Component
public class CacheCustomizer implements CacheManagerCustomizer<ConcurrentMapCacheManager> {

    public static final String PREDICTION_CACHE="predictions";
    public static final String TRIGGER_PREDICTIONS_SNAPSHOT_CACHE="predictions-snapshot";

    @Override
    public void customize(ConcurrentMapCacheManager cacheManager) {
        cacheManager.setCacheNames(Collections.singletonList(PREDICTION_CACHE));
    }
}
