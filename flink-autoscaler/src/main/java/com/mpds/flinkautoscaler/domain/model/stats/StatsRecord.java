package com.mpds.flinkautoscaler.domain.model.stats;

import com.mpds.flinkautoscaler.domain.model.events.MetricReported;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
public class StatsRecord {
    LocalDateTime timestamp;
    int kafkaLag;
    double kafkaMessageRate;
    double flinkRecordsIn;
    double cpu;
    double mem;
    double ltPrediction;
    double stPrediction;
    double aggregatePrediction;
    int parallelism;
    double lagLatency;

    public StatsRecord() {

    }

    public void setWithMetricReported(MetricReported metrics){
        timestamp = metrics.getOccurredOn();
        kafkaLag = (int) metrics.getKafkaLag();
        kafkaMessageRate = metrics.getKafkaMessagesPerSecond();
        cpu = metrics.getCpuUtilization();
        mem = metrics.getMemoryUsage();
        flinkRecordsIn = metrics.getFlinkNumberRecordsIn();
    }

    public String toCSVString(){
        return String.join(",", String.valueOf(timestamp),
                String.valueOf(kafkaLag),
                String.valueOf(kafkaMessageRate),
                String.valueOf(flinkRecordsIn),
                String.valueOf(cpu),
                String.valueOf(mem),
                String.valueOf(ltPrediction),
                String.valueOf(stPrediction),
                String.valueOf(aggregatePrediction),
                String.valueOf(parallelism),
                String.valueOf(lagLatency));
    }
}
