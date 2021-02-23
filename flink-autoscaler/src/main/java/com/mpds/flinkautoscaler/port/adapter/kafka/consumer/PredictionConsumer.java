package com.mpds.flinkautoscaler.port.adapter.kafka.consumer;

import com.mpds.flinkautoscaler.application.service.DomainEventService;
import com.mpds.flinkautoscaler.domain.model.events.PredictionReported;
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
public class PredictionConsumer {

    private final DomainEventService domainEventService;

    @Bean
    public Consumer<Flux<Message<PredictionReported>>> prediction() {
       return flux -> flux.flatMap(predictionReportedMessage -> {
           PredictionReported predictionReported = predictionReportedMessage.getPayload();
            log.debug("Start processing: {}", predictionReported.toString());
//            Acknowledgment acknowledgment = metricReportedMessage.getHeaders().get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment.class);
//           assert acknowledgment != null;
//           acknowledgment.acknowledge();
            return Mono.empty();
       }).doOnError(Throwable::getMessage).subscribe();
    }
}
