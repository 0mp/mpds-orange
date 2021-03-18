package com.mpds.flinkautoscaler.port.adapter.rest.config;

import com.mpds.flinkautoscaler.infrastructure.config.PrometheusProps;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    // Default WebClient to query data from Prometheus
    @Bean
    public WebClient webClient(PrometheusProps prometheusProps){
        return WebClient.builder()
                .baseUrl(prometheusProps.getBaseUrl()).build();
    }
}
