package com.project.cqrs.query.payment.kafka.consumer;
import com.project.cqrs.admin.idempotency.service.IdempotencyService;
import com.project.cqrs.query.order.repository.OrderQueryRepository;
import com.project.cqrs.query.order.service.OrderQueryService;
import com.project.cqrs.query.payment.model.PaymentQueryEntity;
import com.project.cqrs.query.payment.repository.PaymentQueryRepository;
import com.project.cqrs.shared.enums.OrderStatus;
import com.project.cqrs.shared.event.payment.PaymentApprovedEvent;
import com.project.cqrs.shared.kafka.factory.KafkaContainerFactories;
import com.project.cqrs.shared.kafka.groupId.KafkaConsumerGroups;
import com.project.cqrs.shared.kafka.topics.PaymentTopics;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component
public class PaymentEventConsumer {
    private final IdempotencyService idempotency;
    private final PaymentQueryRepository payments;
    private final OrderQueryRepository orders;
    private final OrderQueryService cache;
    public PaymentEventConsumer(IdempotencyService idempotency, PaymentQueryRepository payments, OrderQueryRepository orders, OrderQueryService cache) {
        this.idempotency = idempotency; this.payments = payments; this.orders = orders; this.cache = cache;
    }
    @Transactional("queryTransactionManager")
    @KafkaListener(topics = PaymentTopics.PAYMENT_APPROVED, groupId = KafkaConsumerGroups.QUERY, containerFactory = KafkaContainerFactories.RESILIENT)
    public void onPaymentApproved(PaymentApprovedEvent event, @Header(value = KafkaHeaders.DELIVERY_ATTEMPT, required = false) Integer attempt) {
        var claim = idempotency.tryClaim(event.eventId(), PaymentTopics.PAYMENT_APPROVED, attempt);
        if (claim == null) return;
        var order = orders.findByOrderIdForUpdate(event.orderId()).orElseThrow(() -> new IllegalStateException("Pedido ainda não sincronizado"));
        if (order.getStatus() != OrderStatus.CANCELLED) {
            if (!payments.existsByOrderIdAndMpPaymentId(event.orderId(), event.mpPaymentId())) {
                payments.save(PaymentQueryEntity.fromApprovedEvent(event.orderId(), event.mpPaymentId(), event.amount(), event.paymentMethod(), event.paymentType()));
            }
            order.updateStatus(OrderStatus.PAID);
            orders.save(order);
            cache.evictOrderCache(event.orderId()); cache.evictUserOrdersCache();
        }
        idempotency.markCompleted(claim);
    }
}