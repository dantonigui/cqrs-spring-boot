package com.project.cqrs.command.category.kafka.producer;

import com.project.cqrs.shared.event.category.CategoryCreateEvent;
import com.project.cqrs.shared.event.category.CategoryDeleteEvent;
import com.project.cqrs.shared.event.category.CategoryUpdateEvent;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class CategoryEventProducer {

    private static final String CREATE = "category-created";
    private static final String UPDATE = "category-updated";
    private static final String DELETE = "category-deleted";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public CategoryEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendCategoryCreated(String categoryId, CategoryCreateEvent event) {
        send(CREATE, categoryId, event);
    }

    public void sendCategoryDeleted(String categoryId, CategoryDeleteEvent event) {
        send(DELETE, categoryId, event);
    }

    public void sendCategoryUpdated(String categoryId, CategoryUpdateEvent event) {
        send(UPDATE, categoryId, event);
    }

    private void send(String topic, String key, Object event) {
        kafkaTemplate.send(topic, key, event);
    }
}