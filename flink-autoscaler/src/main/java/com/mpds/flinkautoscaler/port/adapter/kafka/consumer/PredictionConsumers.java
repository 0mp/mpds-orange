package com.mpds.flinkautoscaler.port.adapter.kafka.consumer;

import com.mpds.flinkautoscaler.application.service.CacheService;
import com.mpds.flinkautoscaler.domain.model.events.LongtermPredictionReported;
import com.mpds.flinkautoscaler.domain.model.events.ShorttermPredictionReported;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.function.Consumer;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class PredictionConsumers {

    private final CacheService cacheService;

    @Bean
    public Consumer<Flux<Message<ShorttermPredictionReported>>> shortTermPredictions() {
       return flux -> flux.flatMap(shortternPredictionReportedMessage -> {
           ShorttermPredictionReported shorttermPredictionReported = shortternPredictionReportedMessage.getPayload();
            log.debug("STP - Caching: {}", shorttermPredictionReported.toString());

           this.cacheService.cacheDomainEvent(shorttermPredictionReported);

           return Mono.empty();
       }).doOnError(Throwable::getMessage).subscribe();
    }


    @Bean
    public Consumer<Flux<Message<LongtermPredictionReported>>> longTermPredictions() {
        return flux -> flux.flatMap(longtermPredictionReportedMessage -> {
            LongtermPredictionReported longtermPredictionReported = longtermPredictionReportedMessage.getPayload();
            log.debug("LTP - Caching: {}", longtermPredictionReported.toString());

            this.cacheService.cacheDomainEvent(longtermPredictionReported);
            return Mono.empty();
        }).doOnError(Throwable::getMessage).subscribe();
    }
}
