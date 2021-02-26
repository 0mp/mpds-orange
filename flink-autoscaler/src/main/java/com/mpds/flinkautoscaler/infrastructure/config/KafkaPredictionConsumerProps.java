package com.mpds.flinkautoscaler.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;



@Configuration
@ConfigurationProperties(prefix = "kafka")
@Data
public class KafkaPredictionConsumerProps {

    private String bootstrapServer;

    private String stPredictionTopic;
    private String ltPredictionTopic;

    private String clientIdConfig;

    private String acksConfig;
}
