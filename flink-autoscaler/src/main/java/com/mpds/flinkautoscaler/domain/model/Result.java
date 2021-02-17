package com.mpds.flinkautoscaler.domain.model;

import lombok.Data;

@Data
public class Result {

    private Metric metric;

    private Object[] value;
}
