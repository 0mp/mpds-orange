package com.mpds.flinkautoscaler.infrastructure.config;

import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "r2dbc.postgres")
@RequiredArgsConstructor
@Data
public class R2DBCConfigurationProperties {

    private String hostname;

    private String port;

    private String database;

    private String username;

    private String password;
}
