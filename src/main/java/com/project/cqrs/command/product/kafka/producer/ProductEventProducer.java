package com.project.cqrs.command.product.kafka.producer;

import com.project.cqrs.shared.event.product.ProductCreateEvent;
import com.project.cqrs.shared.event.product.ProductDeleteEvent;
import com.project.cqrs.shared.event.product.ProductUpdateEvent;
import com.project.cqrs.shared.kafka.topics.ProductTopics;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProductEventProducer {


    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ProductEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendProductCreated(String productId, ProductCreateEvent event) {
        kafkaTemplate.send(ProductTopics.PRODUCTS_CREATED, productId, event);
    }

    public void sendProductDeleted(String productId, ProductDeleteEvent event) {
        kafkaTemplate.send(ProductTopics.PRODUCTS_DELETED, productId, event);
    }

    public void sendProductUpdated(String productId, ProductUpdateEvent event) {
        kafkaTemplate.send(ProductTopics.PRODUCTS_UPDATED, productId, event);
    }
}