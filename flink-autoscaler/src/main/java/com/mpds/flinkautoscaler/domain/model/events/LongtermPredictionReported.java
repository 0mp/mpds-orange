package com.mpds.flinkautoscaler.domain.model.events;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.mpds.flinkautoscaler.domain.model.PredictedWorkload;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
@JsonPropertyOrder({"uuid", "eventType", "occurredOn", "predictedWorkloads", "predictionBasedOnDateTime", "eventTriggerUuid"})
@Getter
public class LongtermPredictionReported extends DomainEvent {

    private final List<PredictedWorkload> predictedWorkloads;

    private final String predictionBasedOnDateTime;

    private final String eventTriggerUuid;


    @JsonCreator
    public LongtermPredictionReported(@JsonProperty("uuid") String uuid, @JsonProperty("occurredOn") LocalDateTime occurredOn, @JsonProperty("predictedWorkloads") List<PredictedWorkload> predictedWorkloads, @JsonProperty("predictionBasedOnDateTime") String predictionBasedOnDateTime, @JsonProperty("eventTriggerUuid") String eventTriggerUuid) {
        super(UUID.fromString(uuid), occurredOn);
        this.predictedWorkloads = predictedWorkloads;
        this.predictionBasedOnDateTime=predictionBasedOnDateTime;
        this.eventTriggerUuid=eventTriggerUuid;

    }

    @Override
    public String eventType() {
        return this.getClass().getSimpleName();
    }
}
