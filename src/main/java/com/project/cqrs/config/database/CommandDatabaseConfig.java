package com.project.cqrs.config.database;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.boot.jdbc.DataSourceBuilder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.context.annotation.Primary;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;

import org.springframework.transaction.PlatformTransactionManager;


@Configuration
@EnableJpaRepositories(
        basePackages = "com.project.cqrs.command",
        entityManagerFactoryRef = "commandEntityManagerFactory",
        transactionManagerRef = "commandTransactionManager"
)
public class CommandDatabaseConfig {


    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.command")
    public DataSourceProperties commandDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.command.hikari")
    public DataSource commandDataSource(
            @Qualifier("commandDataSourceProperties")
            DataSourceProperties properties) {

        return properties
                .initializeDataSourceBuilder()
                .type(com.zaxxer.hikari.HikariDataSource.class)
                .build();
    }


    @Bean
    @Primary
    @org.springframework.context.annotation.DependsOn("commandFlyway")
    public LocalContainerEntityManagerFactoryBean commandEntityManagerFactory(
            EntityManagerFactoryBuilder builder,
            @Qualifier("commandDataSource")
            DataSource dataSource
    ) {

        return builder
                .dataSource(dataSource)
                .packages("com.project.cqrs.command")
                .persistenceUnit("command")
                .build();
    }


    @Bean
    @Primary
    public PlatformTransactionManager commandTransactionManager(
            @Qualifier("commandEntityManagerFactory")
            EntityManagerFactory entityManagerFactory
    ) {

        return new JpaTransactionManager(entityManagerFactory);
    }
}