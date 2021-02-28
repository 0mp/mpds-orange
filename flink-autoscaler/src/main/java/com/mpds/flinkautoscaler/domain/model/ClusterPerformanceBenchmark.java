package com.mpds.flinkautoscaler.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Column;
import org.springframework.data.relational.core.mapping.Table;

import java.time.LocalDateTime;


@Table("cluster_performance_benchmark")
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Data
public class ClusterPerformanceBenchmark {

    @Id
    private Long id;

    @Column("num_taskmanager_pods")
    private int numTaskmanagerPods;

    @Column("max_rate")
    private int maxRate;

    @Column("parallelism")
    private int parallelism;

    @Column("restart_time")
    private int restartTime;

    @Column("recovery_time")
    private int recoveryTime;

    @Column("created_date")
    private LocalDateTime createdDate;

    @Column("created_at")
    private LocalDateTime createdAt;

}
