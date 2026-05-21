package com.project.cqrs.config.kafka;

import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

@Configuration
public class KafkaProducerConfig {

    @Bean
    public ProducerFactory<String, Object> producerFactory() {

        Map<String, Object> config = new HashMap<>();

        config.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        config.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);

        // Adicione estas duas linhas:
        config.put(JsonSerializer.ADD_TYPE_INFO_HEADERS, true);
        config.put(JsonSerializer.TYPE_MAPPINGS,
                "categoryCreate:com.project.cqrs.shared.event.category.CategoryCreateEvent," +
                        "categoryUpdate:com.project.cqrs.shared.event.category.CategoryUpdateEvent," +
                        "categoryDelete:com.project.cqrs.shared.event.category.CategoryDeleteEvent," +
                        "productCreate:com.project.cqrs.shared.event.product.ProductCreateEvent," +
                        "productUpdate:com.project.cqrs.shared.event.product.ProductUpdateEvent," +
                        "productDelete:com.project.cqrs.shared.event.product.ProductDeleteEvent,"+
                        "userCreated:com.project.cqrs.shared.event.user.UserCreatedEvent," +
                        "userUpdated:com.project.cqrs.shared.event.user.UserUpdatedEvent," +
                        "userLogout:com.project.cqrs.shared.event.user.UserLogoutEvent"
        );

        return new DefaultKafkaProducerFactory<>(config);
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}