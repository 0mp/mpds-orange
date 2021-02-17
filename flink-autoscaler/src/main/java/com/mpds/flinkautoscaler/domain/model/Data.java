package com.mpds.flinkautoscaler.domain.model;

import java.util.List;

@lombok.Data
public class Data {

    private String resultType;

    private List<Result> result;
}
