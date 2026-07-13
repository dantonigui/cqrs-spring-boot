package com.project.cqrs.command.payment.kafka.producer;

import com.project.cqrs.shared.event.payment.PaymentApprovedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class PaymentEventProducer {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentEventProducer.class);

    public static final String TOPIC_PAYMENT_APPROVED = "payment-approved";//trocar no futuro

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public PaymentEventProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publishPaymentApproved(String orderId, PaymentApprovedEvent paymentApprovedEvent) {
        kafkaTemplate.send(TOPIC_PAYMENT_APPROVED, orderId, paymentApprovedEvent)
                .whenComplete((result, error) -> {
                    if (error != null) {
                        LOG.error(
                                "Falha ao publicar payment.approved: " +
                                        "orderId={}, eventId={}, erro={}",
                                orderId, paymentApprovedEvent.eventId(), error.getMessage()
                        );
                    } else {
                        LOG.info("payment.approved publicado com sucesso: " + "orderId={}, eventId={}, partition={}, offset={}",
                                orderId, paymentApprovedEvent.eventId(), result.getRecordMetadata().offset(), result.getRecordMetadata().partition());
                    }
                });
    }
}
