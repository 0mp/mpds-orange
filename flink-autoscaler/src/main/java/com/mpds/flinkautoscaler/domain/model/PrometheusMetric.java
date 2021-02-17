package com.mpds.flinkautoscaler.domain.model;

import lombok.Data;

@Data
public class PrometheusMetric {

    private String status;

    private com.mpds.flinkautoscaler.domain.model.Data data;

}
