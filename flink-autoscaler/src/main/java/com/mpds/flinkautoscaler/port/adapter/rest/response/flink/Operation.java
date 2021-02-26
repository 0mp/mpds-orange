package com.mpds.flinkautoscaler.port.adapter.rest.response.flink;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class Operation {

    @JsonProperty("location")
    private String location;
}
