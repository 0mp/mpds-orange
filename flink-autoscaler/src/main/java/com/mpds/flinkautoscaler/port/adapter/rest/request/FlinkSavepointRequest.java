package com.mpds.flinkautoscaler.port.adapter.rest.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FlinkSavepointRequest {

    @JsonProperty("target-directory")
    private String targetDirectory;

    @JsonProperty("cancel-job")
    private Boolean cancelJob;

}
