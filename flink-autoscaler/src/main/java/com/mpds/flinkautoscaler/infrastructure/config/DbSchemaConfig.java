package com.mpds.flinkautoscaler.infrastructure.config;

import io.r2dbc.spi.ConnectionFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ClassPathResource;
import org.springframework.r2dbc.connection.init.CompositeDatabasePopulator;
import org.springframework.r2dbc.connection.init.ConnectionFactoryInitializer;
import org.springframework.r2dbc.connection.init.ResourceDatabasePopulator;

@Configuration
public class DbSchemaConfig {

    @Bean
    public ConnectionFactoryInitializer initializer(@Qualifier("connectionFactory") ConnectionFactory connectionFactory){
        ConnectionFactoryInitializer connectionFactoryInitializer = new ConnectionFactoryInitializer();
        connectionFactoryInitializer.setConnectionFactory(connectionFactory);

        CompositeDatabasePopulator compositeDatabasePopulator = new CompositeDatabasePopulator();
        compositeDatabasePopulator.addPopulators(new ResourceDatabasePopulator(new ClassPathResource("schema.sql")));

        connectionFactoryInitializer.setDatabasePopulator(compositeDatabasePopulator);

        return connectionFactoryInitializer;
    }
}
