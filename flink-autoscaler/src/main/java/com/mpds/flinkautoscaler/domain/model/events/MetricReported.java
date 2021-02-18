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
@JsonPropertyOrder({"uuid", "eventType", "occurredOn", "kafkaTopic", "kafkaMessagesPerSeconds", "kafkaMaxMessageLatency", "recordsProcessedPerSeconds", "networkInPerSeconds", "networkOutPerSeconds", "sinkHealthy", "cpuUtilization", "memoryUsage"})
@Getter
public class MetricReported extends DomainEvent {

    private final String kafkaTopic;

    private final String flinkTopic;

    private final float kafkaMessagesPerSeconds;

    private final int kafkaMaxMessageLatency;

    private final int recordsProcessedPerSeconds;

    private final float networkInPerSeconds;

    private final float networkOutPerSeconds;

    private final Boolean sinkHealthy;

    private final float cpuUtilization;

    private final float memoryUsage;

    @JsonCreator
    public MetricReported(@JsonProperty("kafkaMessagesInPerSeconds") float kafkaMessagesPerSeconds, @JsonProperty("occurredOn") LocalDateTime occurredOn, @JsonProperty("kafkaTopic") String kafkaTopic, @JsonProperty("flinkTopic") String flinkTopic,
                          @JsonProperty("kafkaMaxMessageLatency") int kafkaMaxMessageLatency, @JsonProperty("recordsProcessedPerSeconds") int recordsProcessedPerSeconds,  @JsonProperty("networkInPerSeconds") float networkInPerSeconds, @JsonProperty("networkOutPerSeconds") float networkOutPerSeconds,
                          @JsonProperty("sinkHealthy") Boolean sinkHealthy, @JsonProperty("cpuUtilization") float cpuUtilization, @JsonProperty("memoryUsage") float memoryUsage) {
        super(UUID.randomUUID(), occurredOn);
        this.kafkaTopic = kafkaTopic;
        this.flinkTopic = flinkTopic;
        this.kafkaMessagesPerSeconds = kafkaMessagesPerSeconds;
        this.kafkaMaxMessageLatency = kafkaMaxMessageLatency;
        this.recordsProcessedPerSeconds = recordsProcessedPerSeconds;
        this.networkInPerSeconds = networkInPerSeconds;
        this.networkOutPerSeconds = networkOutPerSeconds;
        this.sinkHealthy = sinkHealthy;
        this.cpuUtilization = cpuUtilization;
        this.memoryUsage = memoryUsage;
    }

    @Override
    public String eventType() {
        return this.getClass().getSimpleName();
    }
}
