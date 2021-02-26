package com.mpds.flinkautoscaler.port.adapter.rest.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.mpds.flinkautoscaler.port.adapter.rest.response.flink.Operation;
import com.mpds.flinkautoscaler.port.adapter.rest.response.flink.Status;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class FlinkSavepointInfoResponse {

    @JsonProperty("status")
    private Status status;

    @JsonProperty("operation")
    private Operation operation;
}
