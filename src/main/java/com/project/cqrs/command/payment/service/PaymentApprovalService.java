package com.project.cqrs.command.payment.service;
import com.project.cqrs.command.order.repository.OrderCommandRepository;
import com.project.cqrs.command.payment.kafka.producer.PaymentEventProducer;
import com.project.cqrs.command.payment.model.PaymentCommandEntity;
import com.project.cqrs.shared.enums.*;
import com.project.cqrs.shared.event.payment.PaymentApprovedEvent;
import com.project.cqrs.config.exception.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PaymentApprovalService {
    private final OrderCommandRepository orders;
    private final PaymentEventProducer events;
    private final PaymentGateway gateway;
    public PaymentApprovalService(OrderCommandRepository orders, PaymentEventProducer events, PaymentGateway gateway) {
        this.orders = orders; this.events = events; this.gateway = gateway;
    }
    @Transactional("commandTransactionManager")
    public void approve(PaymentCommandEntity payment, String mpPaymentId) {
        var order = orders.findByIdForUpdate(payment.getOrder().getOrderId()).orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado"));
        if (order.getStatus() == OrderStatus.CANCELLED) {
            if (mpPaymentId != null) gateway.refund(mpPaymentId);
            payment.setPaymentStatus(PaymentStatus.REFUNDED);
            return;
        }
        if (order.getStatus() == OrderStatus.PAID) return;
        payment.setPaymentStatus(PaymentStatus.APPROVED);
        order.markAsPaid();
        orders.save(order);
        events.publishPaymentApproved(order.getOrderId().toString(), PaymentApprovedEvent.from(payment));
    }
}