package com.mpds.flinkautoscaler.port.adapter.rest.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@AllArgsConstructor
@Builder
public class FlinkRunJobRequest {

    @JsonProperty("jobId")
    // To be checked through the Flink Web UI
    private String jobId;

    @JsonProperty("programArgs")
    // e.g. --statebackend.default false --checkpoint hdfs://hadoop-hdfs-namenode:8020/flink/checkpoints --checkpoint.interval 300000
    private String programArgs;

    @JsonProperty("parallelism")
    private int parallelism;

    @JsonProperty("savepointPath")
    // e.g.: hdfs://hadoop-hdfs-namenode:8020/flink/savepoints/savepoint-040a83-73e0bac50483
    private String savepointPath;
}
