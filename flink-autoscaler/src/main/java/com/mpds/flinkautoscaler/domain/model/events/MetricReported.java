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
@JsonPropertyOrder({"uuid", "eventType", "occurredOn", "kafkaTopic", "kafkaMessagesPerSecond", "kafkaMaxMessageLatency", "recordsProcessedPerSecond", "networkInPerSecond", "networkOutPerSecond", "sinkHealthy", "cpuUtilization", "memoryUsage"})
@Getter
public class MetricReported extends DomainEvent {

    private final String kafkaTopic;

    private final String flinkTopic;

    private final float kafkaMessagesPerSecond;

    private final int kafkaMaxMessageLatency;

    private final float kafkaLag;

    private final int recordsProcessedPerSecond;

    private final float networkInPerSecond;

    private final float networkOutPerSecond;

    private final Boolean sinkHealthy;

    private final float cpuUtilization;

    private final float memoryUsage;

    @JsonCreator
    public MetricReported(@JsonProperty("kafkaMessagesInPerSecond") float kafkaMessagesPerSecond,
                          @JsonProperty("occurredOn") LocalDateTime occurredOn,
                          @JsonProperty("kafkaTopic") String kafkaTopic,
                          @JsonProperty("flinkTopic") String flinkTopic,
                          @JsonProperty("kafkaMaxMessageLatency") int kafkaMaxMessageLatency,
                          @JsonProperty("recordsProcessedPerSecond") int recordsProcessedPerSecond,
                          @JsonProperty("networkInPerSecond") float networkInPerSecond,
                          @JsonProperty("networkOutPerSecond") float networkOutPerSecond,
                          @JsonProperty("sinkHealthy") Boolean sinkHealthy,
                          @JsonProperty("cpuUtilization") float cpuUtilization,
                          @JsonProperty("memoryUsage") float memoryUsage,
                          @JsonProperty("kafkaLag") float kafkaLag) {
        super(UUID.randomUUID(), occurredOn);
        this.kafkaTopic = kafkaTopic;
        this.flinkTopic = flinkTopic;
        this.kafkaMessagesPerSecond = kafkaMessagesPerSecond;
        this.kafkaMaxMessageLatency = kafkaMaxMessageLatency;
        this.recordsProcessedPerSecond = recordsProcessedPerSecond;
        this.networkInPerSecond = networkInPerSecond;
        this.networkOutPerSecond = networkOutPerSecond;
        this.sinkHealthy = sinkHealthy;
        this.cpuUtilization = cpuUtilization;
        this.memoryUsage = memoryUsage;
        this.kafkaLag = kafkaLag;
    }

    @Override
    public String eventType() {
        return this.getClass().getSimpleName();
    }
}
