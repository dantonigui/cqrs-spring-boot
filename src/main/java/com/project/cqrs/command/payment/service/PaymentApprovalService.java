package com.project.cqrs.command.payment.service;

import com.project.cqrs.command.order.repository.OrderRepository;
import com.project.cqrs.command.payment.kafka.producer.PaymentEventProducer;
import com.project.cqrs.command.payment.model.PaymentEntity;
import com.project.cqrs.shared.event.payment.PaymentApprovedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentApprovalService {

    private static final Logger log = LoggerFactory.getLogger(PaymentApprovalService.class);

    private final OrderRepository orderRepository;
    private final PaymentEventProducer paymentEventProducer;

    public PaymentApprovalService(OrderRepository orderRepository, PaymentEventProducer paymentEventProducer) {
        this.orderRepository = orderRepository;
        this.paymentEventProducer = paymentEventProducer;
    }

    @Transactional
    public void approve(PaymentEntity payment, String mpPaymentId) {
        orderRepository.findByIdForUpdate(payment.getOrder().getId()).ifPresent(order -> {

            if(order.getStatus().name().equals("PAID")) {
                return;
            }

            order.markAsPaid();

            orderRepository.save(order);

            PaymentApprovedEvent event = PaymentApprovedEvent.of(
                    mpPaymentId,
                    payment.getId(),
                    order.getId(),
                    order.getUserId(),
                    payment.getTransactionAmount(),
                    payment.getPaymentMethod().name()
            );

            paymentEventProducer.publishPaymentApproved(order.getId().toString(), event);

            log.info("Payment approved for payment with id " + order.getId());
        });
    }
}
