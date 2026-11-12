package com.project.cqrs.notification.consumer;
import com.project.cqrs.admin.idempotency.service.IdempotencyService;
import com.project.cqrs.query.auth.repository.UserQueryRepository;
import com.project.cqrs.notification.service.*;
import com.project.cqrs.shared.event.order.OrderCancelledEvent;
import com.project.cqrs.shared.event.payment.PaymentApprovedEvent;
import com.project.cqrs.shared.kafka.topics.*;
import com.project.cqrs.shared.kafka.factory.KafkaContainerFactories;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
@Component
public class NotificationEventConsumer {
    private final IdempotencyService idempotency;
    private final UserQueryRepository users;
    private final NotificationTemplateService templates;
    private final EmailSenderService sender;
    public NotificationEventConsumer(IdempotencyService idempotency, UserQueryRepository users, NotificationTemplateService templates, EmailSenderService sender) {
        this.idempotency = idempotency; this.users = users; this.templates = templates; this.sender = sender;
    }
    @Transactional("queryTransactionManager")
    @KafkaListener(topics = PaymentTopics.PAYMENT_APPROVED, groupId = "cqrs-notifications", containerFactory = KafkaContainerFactories.RESILIENT)
    public void approved(PaymentApprovedEvent event) {
        var claim = idempotency.tryClaim(event.eventId(), "notification:" + PaymentTopics.PAYMENT_APPROVED, null);
        if (claim == null) return;
        var user = users.findById(event.userId()).orElseThrow(() -> new IllegalStateException("Usuário ainda não sincronizado"));
        sender.send(templates.paymentApproved(user.getUserEmail(), user.getUserName(), event.orderId(), event.amount(), event.paymentMethod().name()));
        idempotency.markCompleted(claim);
    }
    @Transactional("queryTransactionManager")
    @KafkaListener(topics = OrderTopics.ORDER_DELETED, groupId = "cqrs-notifications", containerFactory = KafkaContainerFactories.RESILIENT)
    public void cancelled(OrderCancelledEvent event) {
        var claim = idempotency.tryClaim(event.eventId(), "notification:" + OrderTopics.ORDER_DELETED, null);
        if (claim == null) return;
        var user = users.findById(event.userId()).orElseThrow(() -> new IllegalStateException("Usuário ainda não sincronizado"));
        sender.send(templates.orderCancelled(user.getUserEmail(), user.getUserName(), event.orderId(), event.amount(), event.reason(), event.mpPaymentId() != null));
        idempotency.markCompleted(claim);
    }
}