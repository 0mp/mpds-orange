package com.mpds.flinkautoscaler;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.r2dbc.repository.config.EnableR2dbcRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableR2dbcRepositories
@EnableScheduling
@EnableCaching
public class FlinkAutoscalerApplication {

    public static void main(String[] args) {
        SpringApplication.run(FlinkAutoscalerApplication.class, args);
    }

}
