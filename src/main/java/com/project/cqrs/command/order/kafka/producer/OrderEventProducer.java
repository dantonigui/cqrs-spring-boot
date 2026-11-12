package com.project.cqrs.command.order.kafka.producer;

import com.project.cqrs.shared.event.order.OrderCancelledEvent;
import com.project.cqrs.shared.event.order.OrderCreatedEvent;
import com.project.cqrs.shared.event.order.OrderStatusChangedEvent;
import com.project.cqrs.shared.kafka.topics.OrderTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.project.cqrs.command.outbox.OutboxPublisher;
import org.springframework.stereotype.Component;


@Component
public class OrderEventProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventProducer.class);

    private final OutboxPublisher kafkaTemplate;

    public OrderEventProducer(OutboxPublisher kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderCreated(String orderId, OrderCreatedEvent  orderCreatedEvent) {
        send(OrderTopics.ORDER_CREATED, orderId, orderCreatedEvent);
    }

    public void publishOrderStatusChanged(String orderId, OrderStatusChangedEvent orderStatusChangedEvent) {
        send(OrderTopics.ORDER_UPDATED, orderId, orderStatusChangedEvent);
    }

    public void publishOrderCancelled(String orderId, OrderCancelledEvent orderCancelledEvent) {
        send(OrderTopics.ORDER_DELETED, orderId, orderCancelledEvent);
    }

    private void send(String topic, String key, Object event) {
        kafkaTemplate.publish(topic, key, event);
    }
}
