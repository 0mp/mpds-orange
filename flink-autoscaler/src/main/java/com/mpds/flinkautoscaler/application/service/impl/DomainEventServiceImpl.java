package com.mpds.flinkautoscaler.application.service.impl;

import com.mpds.flinkautoscaler.application.service.DomainEventService;
import com.mpds.flinkautoscaler.domain.model.events.DomainEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class DomainEventServiceImpl implements DomainEventService {

    @Override
    // Method should process the data from the prediction topic
    public Mono<Void> processDomainEvent(DomainEvent domainEvent) {

        // TODO: Process prediction data
        return Mono.empty();
    }
}
