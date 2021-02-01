package com.mpds.flinkautoscaler.infrastructure.config;

import io.r2dbc.postgresql.PostgresqlConnectionConfiguration;
import io.r2dbc.postgresql.PostgresqlConnectionFactory;
import io.r2dbc.spi.ConnectionFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.r2dbc.config.AbstractR2dbcConfiguration;

@Configuration
@RequiredArgsConstructor
public class R2DBCConfiguration extends AbstractR2dbcConfiguration {

    private final R2DBCConfigurationProperties r2DBCConfigurationProperties;

    @Override
    @Bean
    public ConnectionFactory connectionFactory() {
        return new PostgresqlConnectionFactory(PostgresqlConnectionConfiguration.builder()
                .host(this.r2DBCConfigurationProperties.getHostname())
                .database(this.r2DBCConfigurationProperties.getDatabase())
                .username(this.r2DBCConfigurationProperties.getUsername())
                .password(this.r2DBCConfigurationProperties.getPassword()).build());
    }
}
