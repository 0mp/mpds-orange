package com.mpds.flinkautoscaler.port.adapter.rest.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FlinkSavepointResponse {

    @JsonProperty("request-id")
    private String requestId;

}
