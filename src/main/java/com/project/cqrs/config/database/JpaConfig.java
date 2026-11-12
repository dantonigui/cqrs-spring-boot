package com.project.cqrs.config.database;

import java.util.HashMap;
import java.util.Map;

import org.springframework.boot.autoconfigure.orm.jpa.JpaProperties;
import org.springframework.boot.orm.jpa.EntityManagerFactoryBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;

@Configuration
public class JpaConfig {

    @Bean
    public EntityManagerFactoryBuilder entityManagerFactoryBuilder(
            JpaProperties jpaProperties
    ) {

        Map<String, Object> properties = new HashMap<>(
                jpaProperties.getProperties()
        );

        properties.put(
                "hibernate.hbm2ddl.auto",
                "validate"
        );

        properties.put("hibernate.physical_naming_strategy", "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy");
        properties.put("hibernate.implicit_naming_strategy", "org.springframework.boot.orm.jpa.hibernate.SpringImplicitNamingStrategy");
        return new EntityManagerFactoryBuilder(
                new HibernateJpaVendorAdapter(),
                properties,
                null
        );
    }
}