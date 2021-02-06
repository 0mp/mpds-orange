package com.mpds.flinkautoscaler.application.service;

import com.mpds.flinkautoscaler.domain.model.events.DomainEvent;
import reactor.core.publisher.Mono;

public interface DomainEventService {

    Mono<Void> processDomainEvent(DomainEvent domainEvent);
}
