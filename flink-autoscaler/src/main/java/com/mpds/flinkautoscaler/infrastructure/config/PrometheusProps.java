package com.mpds.flinkautoscaler.infrastructure.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "metrics-server.prometheus")
@Data
public class PrometheusProps {

    private String baseUrl;

    private String sourceTopic;
}

//    private String url;
//
//    private String queryPath;
//}
