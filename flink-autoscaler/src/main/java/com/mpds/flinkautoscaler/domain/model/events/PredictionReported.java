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
@JsonPropertyOrder({"uuid", "eventType", "occurredOn", "predictedWorkload", "predictionBasedOnDateTime", "eventTriggerUuid"})
@Getter
public class PredictionReported extends DomainEvent {

    private int predictedWorkload;

    private String predictionBasedOnDateTime;

    private String eventTriggerUuid;

    @JsonCreator
    public PredictionReported(@JsonProperty("uuid") String uuid, @JsonProperty("occurredOn") LocalDateTime occurredOn, @JsonProperty("predictedWorkload") int predictedWorkload, @JsonProperty("predictionBaseOnDateTime") String predictionBasedOnDateTime, @JsonProperty("eventTriggerUuid") String eventTriggerUuid) {
        super(UUID.fromString(uuid), occurredOn);
        this.predictedWorkload=predictedWorkload;
        this.predictionBasedOnDateTime=predictionBasedOnDateTime;
        this.eventTriggerUuid=eventTriggerUuid;
    }

    @Override
    public String eventType() {
        return this.getClass().getSimpleName();
    }
}
