package com.mpds.flinkautoscaler.domain.model.events;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateTimeSerializer;
import lombok.Data;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.PROPERTY,
        property = "eventType",
        defaultImpl = MetricReported.class
)
@JsonSubTypes({
        @JsonSubTypes.Type( value = MetricReported.class, name = "MetricReported" ),
        @JsonSubTypes.Type( value = ShorttermPredictionReported.class, name = "ShorttermPredictionReported" ),
        @JsonSubTypes.Type( value = LongtermPredictionReported.class, name = "LongtermPredictionReported" )
})
@RequiredArgsConstructor
public abstract class DomainEvent {

    private final UUID uuid;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    @JsonSerialize(using = LocalDateTimeSerializer.class)
    private final LocalDateTime occurredOn;

    @JsonProperty("eventType")
    public abstract String eventType();
}
