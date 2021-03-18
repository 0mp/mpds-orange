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
@JsonPropertyOrder({"uuid", "eventType", "occurredOn", "kafkaTopic", "kafkaMessagesPerSecond", "kafkaMaxMessageLatency", "flinkNumberRecordsIn", "sinkHealthy", "cpuUtilization", "memoryUsage"})
@Getter
public class MetricReported extends DomainEvent {

    private final String kafkaTopic;

    private final float kafkaMessagesPerSecond;

    private final float maxJobLatency;

    private final float kafkaLag;

    private final float flinkNumberRecordsIn;

    private final Boolean sinkHealthy;

    private final float cpuUtilization;

    private final float memoryUsage;

    @JsonCreator
    public MetricReported(@JsonProperty("uuid") UUID uuid,
                          @JsonProperty("kafkaMessagesPerSecond") float kafkaMessagesPerSecond,
                          @JsonProperty("occurredOn") LocalDateTime occurredOn,
                          @JsonProperty("kafkaTopic") String kafkaTopic,
                          @JsonProperty("maxJobLatency") float maxJobLatency,
                          @JsonProperty("flinkNumberRecordsIn") float flinkNumberRecordsIn,
                          @JsonProperty("sinkHealthy") Boolean sinkHealthy,
                          @JsonProperty("cpuUtilization") float cpuUtilization,
                          @JsonProperty("memoryUsage") float memoryUsage,
                          @JsonProperty("kafkaLag") float kafkaLag) {
        super(uuid, occurredOn);
        this.kafkaTopic = kafkaTopic;
        this.kafkaMessagesPerSecond = kafkaMessagesPerSecond;
        this.maxJobLatency = maxJobLatency;
        this.flinkNumberRecordsIn = flinkNumberRecordsIn;
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
