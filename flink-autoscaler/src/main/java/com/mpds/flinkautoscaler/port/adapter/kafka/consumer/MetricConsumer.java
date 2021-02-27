package com.mpds.flinkautoscaler.port.adapter.kafka.consumer;

import com.mpds.flinkautoscaler.application.service.DomainEventService;
import com.mpds.flinkautoscaler.application.service.PredictionCacheService;
import com.mpds.flinkautoscaler.domain.model.events.MetricReported;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import reactor.core.publisher.Flux;

import java.util.function.Consumer;

@Configuration
@Slf4j
@RequiredArgsConstructor
public class MetricConsumer {
    private final DomainEventService domainEventService;

    private final PredictionCacheService predictionCacheService;

    @Bean
    public Consumer<Flux<Message<MetricReported>>> metrics() {
        return flux -> flux.flatMap(metricReportedMessage -> {
            MetricReported metricReported = metricReportedMessage.getPayload();
            log.debug("M - Start processing: {}", metricReportedMessage.toString());
//            Acknowledgment acknowledgment = metricReportedMessage.getHeaders().get(KafkaHeaders.ACKNOWLEDGMENT, Acknowledgment.class);
//           assert acknowledgment != null;
//           acknowledgment.acknowledge();
            return this.domainEventService.processDomainEvent(metricReported);
        }).doOnError(Throwable::getMessage).subscribe();
    }

}
