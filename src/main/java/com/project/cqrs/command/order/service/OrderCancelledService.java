package com.project.cqrs.command.order.service;
import com.project.cqrs.command.order.kafka.producer.OrderEventProducer;
import com.project.cqrs.command.order.repository.OrderCommandRepository;
import com.project.cqrs.command.payment.repository.PaymentCommandRepository;
import com.project.cqrs.command.payment.service.PaymentGateway;
import com.project.cqrs.command.product.service.InventoryService;
import com.project.cqrs.config.exception.ResourceNotFoundException;
import com.project.cqrs.shared.enums.*;
import com.project.cqrs.shared.event.order.OrderCancelledEvent;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderCancelledService {
    private final OrderCommandRepository orders;
    private final PaymentCommandRepository payments;
    private final OrderEventProducer events;
    private final InventoryService inventory;
    private final PaymentGateway gateway;
    public OrderCancelledService(OrderCommandRepository orders, PaymentCommandRepository payments, OrderEventProducer events, InventoryService inventory, PaymentGateway gateway) {
        this.orders = orders; this.payments = payments; this.events = events; this.inventory = inventory; this.gateway = gateway;
    }
    @Transactional("commandTransactionManager")
    public void expireOrder(Long orderId, Long userId) {
        var order = orders.findByIdForUpdate(orderId).orElseThrow();
        if (order.getStatus() != OrderStatus.PENDING && order.getStatus() != OrderStatus.AWAITING_PAYMENT) return;
        cancelOrder(orderId, userId, "Reserva de estoque expirada");
    }
    @Transactional("commandTransactionManager")
    public void cancelOrder(Long orderId, Long userId, String reason) {
        var order = orders.findByIdForUpdate(orderId).orElseThrow(() -> new ResourceNotFoundException("Pedido não encontrado"));
        var auth = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        boolean admin = auth != null && auth.getAuthorities().stream().anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (!admin && !userId.equals(order.getUserId())) throw new ResourceNotFoundException("Pedido não encontrado");
        if (order.getStatus() == OrderStatus.CANCELLED) throw new IllegalStateException("Pedido já cancelado");
        var orderPayments = payments.findByOrderOrderId(orderId);
        if (!admin && orderPayments.stream().anyMatch(p -> p.getPaymentType() == PaymentType.IN_PERSON && p.getPaymentStatus() == PaymentStatus.APPROVED)) {
            throw new org.springframework.security.access.AccessDeniedException("Somente ADMIN pode cancelar pedido pago presencialmente");
        }
        String refundedId = null;
        for (var payment : orderPayments) {
            if (payment.getPaymentStatus() == PaymentStatus.REFUNDED || payment.getPaymentStatus() == PaymentStatus.REJECTED) continue;
            if (payment.getMpPaymentId() != null) {
                boolean refunded = gateway.cancelOrRefund(payment.getMpPaymentId());
                payment.setPaymentStatus(refunded ? PaymentStatus.REFUNDED : PaymentStatus.CANCELLED);
                if (refunded) refundedId = payment.getMpPaymentId();
            } else {
                payment.setPaymentStatus(payment.getPaymentStatus() == PaymentStatus.APPROVED ? PaymentStatus.REFUNDED : PaymentStatus.CANCELLED);
            }
            payments.save(payment);
        }
        inventory.release(order.getItems());
        order.markAsCancelled();
        orders.save(order);
        events.publishOrderCancelled(orderId.toString(), OrderCancelledEvent.of(orderId, order.getUserId(), refundedId, order.getTotalAmount(), reason));
    }
}