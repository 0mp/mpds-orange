package com.mpds.flinkautoscaler.domain.model.events;


import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonPropertyOrder({"uuid", "eventType", "occurredOn", "predictedWorkload"})
@Getter
public class PredictionReported extends DomainEvent {

    private final int predictedWorkload;



    @JsonCreator
    public PredictionReported(@JsonProperty("uuid") String uuid, @JsonProperty("occurredOn") LocalDateTime occurredOn, @JsonProperty("predictedWorkload") int predictedWorkload) {
        super(UUID.fromString(uuid), occurredOn);
        this.predictedWorkload=predictedWorkload;
    }

    @Override
    public String eventType() {
        return this.getClass().getSimpleName();
    }
}
