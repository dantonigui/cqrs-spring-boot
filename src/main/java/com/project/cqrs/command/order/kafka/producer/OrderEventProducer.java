package com.project.cqrs.command.order.kafka.producer;

import com.project.cqrs.shared.event.order.OrderCancelledEvent;
import com.project.cqrs.shared.event.order.OrderCreatedEvent;
import com.project.cqrs.shared.event.order.OrderStatusChangedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;


/**
 * Publica eventos de pedido no Kafka.
 *
 * Topics:
 *   order.created        → pedido criado
 *   order.status.changed → status alterado (AWAITING_PAYMENT, PAID, etc.)
 *   order.cancelled      → pedido cancelado (com ou sem estorno)
 *
 * Chave: orderId — garante ordem de processamento por pedido.
 */
@Component
public class OrderEventProducer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventProducer.class);

    public static final String TOPIC_ORDER_CREATED = "order.created";
    public static final String TOPIC_ORDER_UPDATED = "order.updated";
    public static final String TOPIC_ORDER_DELETED = "order.deleted";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public OrderEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishOrderCreated(String orderId, OrderCreatedEvent  orderCreatedEvent) {
        send(TOPIC_ORDER_CREATED, orderId, orderCreatedEvent);
    }

    public void publishOrderStatusChanged(String orderId, OrderStatusChangedEvent orderStatusChangedEvent) {
        send(TOPIC_ORDER_UPDATED, orderId, orderStatusChangedEvent);
    }

    public void publishOrderCancelled(String orderId, OrderCancelledEvent orderCancelledEvent) {
        send(TOPIC_ORDER_DELETED, orderId, orderCancelledEvent);
    }

    private void send(String topic, String key, Object event) {
        kafkaTemplate.send(topic, key, event)
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("Falha ao publicar {}: key={}, erro={}",
                                topic, key, ex.getMessage());
                    } else {
                        log.info("{} publicado: key={}, partition={}, offset={}", topic, key,
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    }
                });
    }
}
