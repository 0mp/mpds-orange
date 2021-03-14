package com.mpds.flinkautoscaler.domain.model;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mpds.flinkautoscaler.domain.model.events.LongtermPredictionReported;
import com.mpds.flinkautoscaler.domain.model.events.MetricReported;
import com.mpds.flinkautoscaler.domain.model.events.ShorttermPredictionReported;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;


//@Table("metric_trigger_predictions_snapshot")
@NoArgsConstructor
@AllArgsConstructor
@Data
public class MetricTriggerPredictionsSnapshot {

    private String jobId;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime snapshotTime;

    private MetricReported metricTrigger;

    private ShorttermPredictionReported shorttermPrediction;

    private LongtermPredictionReported longtermPredictionReported;

    private int targetParallelism;

    private float aggregatePrediction;

    public String snapshotCacheKey(){
        return "MetricTriggerPredictionsSnapshot";
//        return this.getClass().getSimpleName();
    }
}
