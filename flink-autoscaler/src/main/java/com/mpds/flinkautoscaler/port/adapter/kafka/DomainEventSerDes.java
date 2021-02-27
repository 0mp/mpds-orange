package com.mpds.flinkautoscaler.port.adapter.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mpds.flinkautoscaler.domain.model.events.DomainEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.common.serialization.Serializer;

@Slf4j
public class DomainEventSerDes implements Serializer<DomainEvent> {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public byte[] serialize(String topic, DomainEvent data) {
        byte[] retVal = null;
//        objectMapper = new ObjectMapper();

        try {
            retVal = objectMapper.writeValueAsString(data).getBytes();
        } catch (Exception e) {
            log.error("Could not publish the event to Kafka!", e);
        }
        return retVal;
    }
}
