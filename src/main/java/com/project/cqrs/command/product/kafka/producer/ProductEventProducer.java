package com.project.cqrs.command.product.kafka.producer;

import com.project.cqrs.command.product.event.ProductCreateEvent;
import com.project.cqrs.command.product.event.ProductDeleteEvent;
import com.project.cqrs.command.product.event.ProductUpdateEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class ProductEventProducer {

    private static final String CREATED = "products-created";
    private static final String DELETED = "products-deleted";
    private static final String UPDATED = "products-updated";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public ProductEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendProductCreated(String productId, ProductCreateEvent event) {
        kafkaTemplate.send(CREATED, productId, event);
    }

    public void sendProductDeleted(String productId, ProductDeleteEvent event) {
        kafkaTemplate.send(DELETED, productId, event);
    }

    public void sendProductUpdated(String productId, ProductUpdateEvent event) {
        kafkaTemplate.send(UPDATED, productId, event);
    }
}