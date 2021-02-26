//package com.mpds.flinkautoscaler.port.adapter.kafka.consumer;
//
//import com.mpds.flinkautoscaler.application.service.PredictionCacheService;
//import com.mpds.flinkautoscaler.domain.model.events.PredictionReported;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.messaging.Message;
//import reactor.core.publisher.Flux;
//import reactor.core.publisher.Mono;
//
//import java.util.function.Consumer;
//
//@Configuration
//@Slf4j
//@RequiredArgsConstructor
//public class PredictionLTConsumer {
//
//    private final PredictionCacheService predictionCacheService;
//
//    @Bean
//    public Consumer<Flux<Message<PredictionReported>>> abc() {
//        return flux -> flux.flatMap(predictionReportedMessage -> {
//            PredictionReported predictionReported = predictionReportedMessage.getPayload();
//            log.debug("LTP - Start processing: {}", predictionReported.toString());
//
////           return this.domainEventService.processDomainEvent(predictionReported);
//            this.predictionCacheService.cachePredictionReported(predictionReported);
//            return Mono.empty();
//        }).doOnError(Throwable::getMessage).subscribe();
//    }
//}
