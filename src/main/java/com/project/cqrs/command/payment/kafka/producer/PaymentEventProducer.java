package com.project.cqrs.command.payment.kafka.producer;

import com.project.cqrs.shared.event.payment.PaymentApprovedEvent;
import com.project.cqrs.shared.kafka.topics.PaymentTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.project.cqrs.command.outbox.OutboxPublisher;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventProducer {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentEventProducer.class);


    private final OutboxPublisher kafkaTemplate;

    public PaymentEventProducer(OutboxPublisher kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishPaymentApproved(String orderId, PaymentApprovedEvent paymentApprovedEvent) {
        kafkaTemplate.publish(PaymentTopics.PAYMENT_APPROVED, orderId, paymentApprovedEvent);
    }
}
