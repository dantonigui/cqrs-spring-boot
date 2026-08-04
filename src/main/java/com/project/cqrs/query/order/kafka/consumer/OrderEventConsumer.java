package com.project.cqrs.query.order.kafka.consumer;

import com.project.cqrs.admin.idempotency.entity.ProcessedEventEntity;
import com.project.cqrs.admin.idempotency.service.IdempotencyService;
import com.project.cqrs.query.order.model.OrderItemQueryEntity;
import com.project.cqrs.query.order.model.OrderQueryEntity;
import com.project.cqrs.query.order.repository.OrderQueryRepository;
import com.project.cqrs.query.order.service.OrderQueryService;
import com.project.cqrs.shared.enums.OrderStatus;
import com.project.cqrs.shared.event.order.OrderCancelledEvent;
import com.project.cqrs.shared.event.order.OrderCreatedEvent;
import com.project.cqrs.shared.event.order.OrderStatusChangedEvent;
import com.project.cqrs.shared.kafka.factory.KafkaContainerFactories;
import com.project.cqrs.shared.kafka.groupId.KafkaConsumerGroups;
import com.project.cqrs.shared.kafka.topics.OrderTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final OrderQueryRepository orderQueryRepository;
    private final OrderQueryService orderQueryService;
    private final IdempotencyService idempotencyService;

    public OrderEventConsumer(OrderQueryRepository orderQueryRepository,
                              OrderQueryService orderQueryService,
                              IdempotencyService idempotencyService) {
        this.orderQueryRepository = orderQueryRepository;
        this.orderQueryService = orderQueryService;
        this.idempotencyService = idempotencyService;
    }

    // ── order.created ─────────────────────────────────────────────────────────

    @Transactional
    @KafkaListener(topics = OrderTopics.ORDER_CREATED, groupId = KafkaConsumerGroups.QUERY, containerFactory = KafkaContainerFactories.RESILIENT)
    public void onOrderCreated(OrderCreatedEvent orderCreatedEvent, @Header(value = KafkaHeaders.DELIVERY_ATTEMPT, required = false) Integer deliveryAttempt) {

       ProcessedEventEntity processed = idempotencyService.tryClaim(orderCreatedEvent.eventId(), OrderTopics.ORDER_CREATED, deliveryAttempt);

        if (processed == null) {
            return;
        }

        try {
            log.info("Processando order.created: orderId={}", orderCreatedEvent.orderId());
            // Guarda extra: não cria duplicata se o orderId já existir
            if (orderQueryRepository.existsByOrderId(orderCreatedEvent.orderId())) {
                log.warn("order.created ignorado: orderId={}", orderCreatedEvent.orderId());

                idempotencyService.markCompleted(processed);

                return;
            }

            OrderQueryEntity order = OrderQueryEntity.fromCreatedEvent(
                    orderCreatedEvent.orderId(),
                    orderCreatedEvent.userId(),
                    orderCreatedEvent.status(),
                    orderCreatedEvent.totalAmount(),
                    orderCreatedEvent.createdAt()
            );

            // Monta os itens a partir do evento — preço vem do evento (já validado
            // no Command Side), não consulta o banco de produtos aqui
            orderCreatedEvent.items().forEach(item -> {
                OrderItemQueryEntity orderItem = OrderItemQueryEntity.of(
                        order,
                        item.productId(),
                        item.productName(),
                        item.unitPrice(),
                        item.quantity()
                );
                order.getItems().add(orderItem);
            });

            orderQueryRepository.save(order);

            orderQueryService.evictUserOrdersCache();

            idempotencyService.markCompleted(processed);

            log.info(
                    "Order created successfully: eventId={}, orderId={}",
                    orderCreatedEvent.eventId(),
                    orderCreatedEvent.orderId()
            );
        } catch (Exception e) {
            idempotencyService.markFailed(processed, e);

            throw e;
        }
    }

    // ── order.status.changed ──────────────────────────────────────────────────

    @Transactional
    @KafkaListener(topics = OrderTopics.ORDER_UPDATED, groupId = KafkaConsumerGroups.QUERY, containerFactory = KafkaContainerFactories.RESILIENT)
    public void onOrderStatusChanged(OrderStatusChangedEvent orderStatusChangedEvent,  @Header(value = KafkaHeaders.DELIVERY_ATTEMPT, required = false) Integer deliveryAttempt) {

        ProcessedEventEntity processed = idempotencyService.tryClaim(orderStatusChangedEvent.eventId(), OrderTopics.ORDER_UPDATED, deliveryAttempt);

        if (processed == null) {
            return;
        }

        try {
            log.info("Processando order.status.changed: orderId={}", orderStatusChangedEvent.orderId(),
                    orderStatusChangedEvent.oldStatus(),  orderStatusChangedEvent.newStatus());

            OrderQueryEntity order = orderQueryRepository.findByOrderId(orderStatusChangedEvent.orderId())
                    .orElseThrow(() -> new IllegalStateException("Order not Found: " + orderStatusChangedEvent.orderId()));

                order.updateStatus(orderStatusChangedEvent.newStatus());

                orderQueryRepository.save(order);

                // Invalida caches
                orderQueryService.evictOrderCache(orderStatusChangedEvent.orderId());
                orderQueryService.evictUserOrdersCache();

            idempotencyService.markCompleted(processed);

            log.info("Order updated: orderId={}", orderStatusChangedEvent.orderId());
        } catch (Exception e) {

            idempotencyService.markFailed(processed, e);
            throw e;
        }
    }

    // ── order.cancelled ───────────────────────────────────────────────────────

    @Transactional
    @KafkaListener(topics = OrderTopics.ORDER_DELETED, groupId = KafkaConsumerGroups.QUERY,containerFactory = KafkaContainerFactories.RESILIENT)
    public void onOrderCancelled(OrderCancelledEvent orderCancelledEvent, @Header(value = KafkaHeaders.DELIVERY_ATTEMPT, required = false) Integer deliveryAttempt) {

        ProcessedEventEntity processed = idempotencyService.tryClaim(orderCancelledEvent.eventId(), OrderTopics.ORDER_DELETED,  deliveryAttempt);

        if (processed == null) {
            return;
        }

        try {
            log.info("Processando order.cancelled: orderId={}", orderCancelledEvent.orderId());

            OrderQueryEntity order = orderQueryRepository.findByOrderId(orderCancelledEvent.orderId())
                    .orElseThrow(() -> new IllegalStateException("Order not found: " + orderCancelledEvent.orderId()));

            order.updateStatus(OrderStatus.CANCELLED);

            orderQueryRepository.save(order);

            orderQueryService.evictOrderCache(orderCancelledEvent.orderId());
            orderQueryService.evictUserOrdersCache();

            idempotencyService.markCompleted(processed);

            log.info(
                    "Order cancelled: orderId={}",
                    orderCancelledEvent.orderId()
            );

        } catch (Exception e) {

            idempotencyService.markFailed(processed, e);

            throw e;
        }
    }
}
