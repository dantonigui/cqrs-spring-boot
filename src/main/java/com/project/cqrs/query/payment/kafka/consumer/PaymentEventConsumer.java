package com.project.cqrs.query.payment.kafka.consumer;

import com.project.cqrs.admin.idempotency.entity.ProcessedEventEntity;
import com.project.cqrs.admin.idempotency.service.IdempotencyService;
import com.project.cqrs.query.order.model.OrderQueryEntity;
import com.project.cqrs.query.order.repository.OrderQueryRepository;
import com.project.cqrs.query.order.service.OrderQueryService;
import com.project.cqrs.query.payment.model.PaymentQueryEntity;
import com.project.cqrs.query.payment.repository.PaymentQueryRepository;
import com.project.cqrs.shared.enums.OrderStatus;
import com.project.cqrs.shared.event.payment.PaymentApprovedEvent;
import com.project.cqrs.shared.kafka.factory.KafkaContainerFactories;
import com.project.cqrs.shared.kafka.groupId.KafkaConsumerGroups;
import com.project.cqrs.shared.kafka.topics.PaymentTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class PaymentEventConsumer {
    private static final Logger log = LoggerFactory.getLogger(PaymentEventConsumer.class);
    private final IdempotencyService idempotencyService;
    private final PaymentQueryRepository  paymentQueryRepository;
    private final OrderQueryRepository orderQueryRepository;
    private final OrderQueryService orderQueryService;

    public PaymentEventConsumer (IdempotencyService idempotencyService, PaymentQueryRepository paymentQueryRepository,
                                 OrderQueryRepository orderQueryRepository, OrderQueryService orderQueryService) {
        this.idempotencyService = idempotencyService;
        this.paymentQueryRepository = paymentQueryRepository;
        this.orderQueryRepository = orderQueryRepository;
        this.orderQueryService = orderQueryService;
    }

    @Transactional
    @KafkaListener(topics = PaymentTopics.PAYMENT_APPROVED, groupId = KafkaConsumerGroups.QUERY, containerFactory = KafkaContainerFactories.RESILIENT)
    public void onPaymentApproved(PaymentApprovedEvent paymentApprovedEvent, @Header(value = KafkaHeaders.DELIVERY_ATTEMPT, required = false)Integer deliveryAttempt) {

        ProcessedEventEntity processed =
                idempotencyService.tryClaim(
                        paymentApprovedEvent.eventId(),
                        PaymentTopics.PAYMENT_APPROVED,
                        deliveryAttempt
                );

        if (processed == null) {
            return;
        }

       try {
           log.info(
                   "Processando payment.approved: orderId={}, paymentId={}",
                   paymentApprovedEvent.orderId(),
                   paymentApprovedEvent.paymentId()
           );
           // 1. Evita criar uma projeção duplicada do pagamento.
           boolean alreadyExists = paymentQueryRepository.existsByOrderIdAndMpPaymentId(paymentApprovedEvent.orderId(), paymentApprovedEvent.paymentId().toString());
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
           OrderQueryEntity order = orderQueryRepository.findByOrderId(paymentApprovedEvent.orderId())
                   .orElseThrow(() -> new RuntimeException("Order Not Found: " + paymentApprovedEvent.orderId()));

               if (!OrderStatus.PAID.equals(order.getStatus())) {
                   order.updateStatus(OrderStatus.PAID);
                   orderQueryRepository.save(order);
               }

           // 3. Invalida caches
           orderQueryService.evictOrderCache(paymentApprovedEvent.orderId());
           orderQueryService.evictUserOrdersCache();

           idempotencyService.markCompleted(processed);
           log.info(
                   "Payment processed successfully: eventId={}, orderId={}, paymentId={}",
                   paymentApprovedEvent.eventId(),
                   paymentApprovedEvent.orderId(),
                   paymentApprovedEvent.paymentId()
           );
       } catch (Exception e) {
           idempotencyService.markFailed(processed);

           throw e;
       }
    }
}
