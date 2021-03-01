package com.mpds.flinkautoscaler.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "autoscaler.flink")
@Data
public class FlinkProps {

    private String baseUrl;

    private String jobId;

    private String jarId;

    private String programArgs;

    private String savepointDirectory;

    private int cooldownDuration;
}
