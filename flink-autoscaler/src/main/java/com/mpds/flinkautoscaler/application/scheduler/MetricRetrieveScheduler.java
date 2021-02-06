package com.mpds.flinkautoscaler.application.scheduler;

import com.mpds.flinkautoscaler.port.adapter.kafka.DomainEventPublisherReactive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
@Slf4j
@RequiredArgsConstructor
public class MetricRetrieveScheduler {

    // Used to query the metrics via REST
    private final WebClient webClient;

    private final DomainEventPublisherReactive domainEventPublisherReactive;

    // Will be used for later use cases
    @Scheduled(fixedDelay = 10000)
    public void scheduleMetricRetrieval(){
        log.info("Start retrieving metrics...");

        // TODO 1: Retrieve metrics via webClient

        // TODO 2: Parse metric to DomainEvent (MetricReported)

        // TODO 3: Publish the domain event to Kafkausing the domainEventPublisherReactive instance
    }
}
