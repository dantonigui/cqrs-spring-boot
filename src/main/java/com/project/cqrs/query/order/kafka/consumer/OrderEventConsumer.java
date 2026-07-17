package com.project.cqrs.query.order.kafka.consumer;

import com.project.cqrs.admin.idempotency.service.IdempotencyService;
import com.project.cqrs.query.order.model.OrderItemQueryEntity;
import com.project.cqrs.query.order.model.OrderQueryEntity;
import com.project.cqrs.query.order.repository.OrderQueryRepository;
import com.project.cqrs.query.order.service.OrderQueryService;
import com.project.cqrs.query.payment.model.PaymentQueryEntity;
import com.project.cqrs.query.payment.repository.PaymentQueryRepository;
import com.project.cqrs.shared.enums.OrderStatus;
import com.project.cqrs.shared.event.order.OrderCancelledEvent;
import com.project.cqrs.shared.event.order.OrderCreatedEvent;
import com.project.cqrs.shared.event.order.OrderStatusChangedEvent;
import com.project.cqrs.shared.event.payment.PaymentApprovedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class OrderEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(OrderEventConsumer.class);

    private final OrderQueryRepository orderQueryRepository;
    private final PaymentQueryRepository paymentQueryRepository;
    private final OrderQueryService orderQueryService;
    private final IdempotencyService idempotencyService;

    public OrderEventConsumer(OrderQueryRepository orderQueryRepository,
                              PaymentQueryRepository paymentQueryRepository,
                              OrderQueryService orderQueryService,
                              IdempotencyService idempotencyService) {
        this.orderQueryRepository = orderQueryRepository;
        this.paymentQueryRepository = paymentQueryRepository;
        this.orderQueryService = orderQueryService;
        this.idempotencyService = idempotencyService;
    }

    // ── order.created ─────────────────────────────────────────────────────────

    @Transactional
    @KafkaListener(topics = "order.created", groupId = "cqrs-resilient-consumer-group", containerFactory = "resilientKafkaListenerContainerFactory")
    public void onOrderCreated(OrderCreatedEvent orderCreatedEvent) {

        if (!idempotencyService.isNew(orderCreatedEvent.eventId(), "order.created")) {
            return;
        }

        log.info("Processando order.created: orderId={}", orderCreatedEvent.orderId());
        // Guarda extra: não cria duplicata se o orderId já existir
        if (orderQueryRepository.existsByOrderId(orderCreatedEvent.orderId())) {
            log.warn("order.created ignorado: orderId={}", orderCreatedEvent.orderId());
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

        log.info("Order created: orderId={}", orderCreatedEvent.orderId());

    }

    // ── order.status.changed ──────────────────────────────────────────────────

    @Transactional
    @KafkaListener(topics = "order.status.changed", groupId = "cqrs-resilient-consumer-group", containerFactory = "resilientKafkaListenerContainerFactory")
    public void onOrderStatusChanged(OrderStatusChangedEvent orderStatusChangedEvent) {

        if (!idempotencyService.isNew(orderStatusChangedEvent.eventId(), "order.status.changed")) {
            return;
        }

        log.info("Processando order.status.changed: orderId={}", orderStatusChangedEvent.orderId(),
                orderStatusChangedEvent.oldStatus(),  orderStatusChangedEvent.newStatus());

        orderQueryRepository.findByOrderId(orderStatusChangedEvent.orderId()).ifPresent(order -> {
            order.updateStatus(orderStatusChangedEvent.newStatus());
            orderQueryRepository.save(order);

            // Invalida caches
            orderQueryService.evictOrderCache(orderStatusChangedEvent.orderId());
            orderQueryService.evictUserOrdersCache();
        });
    }

    // ── order.cancelled ───────────────────────────────────────────────────────

    @Transactional
    @KafkaListener(topics = "order.cancelled", groupId = "cqrs-resilient-consumer-group")
    public void onOrderCancelled(OrderCancelledEvent orderCancelledEvent) {

        if (!idempotencyService.isNew(orderCancelledEvent.eventId(), "order.cancelled")) {
            return;
        }

        log.info("Processando order.cancelled: orderId={}", orderCancelledEvent.orderId());

        orderQueryRepository.findByOrderId(orderCancelledEvent.orderId()).ifPresent(order -> {
            order.updateStatus(OrderStatus.CANCELLED);
            orderQueryRepository.save(order);

            orderQueryService.evictOrderCache(orderCancelledEvent.orderId());
            orderQueryService.evictUserOrdersCache();
        });
    }

    // ── payment.approved ──────────────────────────────────────────────────────

    /**
     * Consome o PaymentApprovedEvent (já publicado pelo PaymentApprovalService)
     * para criar a projeção de leitura do pagamento e atualizar o status
     * do pedido na projeção.
     */
    @Transactional
    @KafkaListener(topics = "payment.approved", groupId = "cqrs-resilient-consumer-group", containerFactory = "resilientKafkaListenerContainerFactory")
    public void onPaymentApproved(PaymentApprovedEvent paymentApprovedEvent) {

        if(!idempotencyService.isNew(paymentApprovedEvent.eventId(), "payment.approved")) {
            return;
        }

        log.info("Processando payment.approved: orderId={}, paymentId={}", paymentApprovedEvent.orderId(),  paymentApprovedEvent.paymentId());

        boolean alreadyExists = paymentQueryRepository.existsByOrderIdAndMpPaymentId(paymentApprovedEvent.orderId(), paymentApprovedEvent.eventId());
        if (!alreadyExists) {
            PaymentQueryEntity paymentQuery = PaymentQueryEntity.fromApprovedEvent(
                    paymentApprovedEvent.orderId(),
                    paymentApprovedEvent.paymentId().toString(),
                    paymentApprovedEvent.amount(),
                    paymentApprovedEvent.paymentMethod()
                    );
            paymentQueryRepository.save(paymentQuery);
        }

        // 2. Atualiza status do pedido para PAID na projeção
        orderQueryRepository.findByOrderId(paymentApprovedEvent.orderId()).ifPresent(order -> {
            if (!OrderStatus.PAID.equals(order.getStatus())) {
                order.updateStatus(OrderStatus.PAID);
                orderQueryRepository.save(order);
            }
        });

        // 3. Invalida caches
        orderQueryService.evictOrderCache(paymentApprovedEvent.orderId());
        orderQueryService.evictUserOrdersCache();

        log.info("Order approved: orderId={}", paymentApprovedEvent.orderId());
    }
}
