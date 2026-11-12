package com.project.cqrs.command.webhook.service;
import com.project.cqrs.command.payment.repository.PaymentCommandRepository;
import com.project.cqrs.command.payment.service.*;
import com.project.cqrs.command.order.repository.OrderCommandRepository;
import com.project.cqrs.shared.enums.PaymentStatus;
import com.project.cqrs.config.exception.PaymentException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class MercadoPagoWebhookProcessor {
    private final PaymentCommandRepository payments;
    private final OrderCommandRepository orders;
    private final PaymentApprovalService approvals;
    private final PaymentGateway gateway;
    @jakarta.persistence.PersistenceContext(unitName = "command") private jakarta.persistence.EntityManager entityManager;
    public MercadoPagoWebhookProcessor(PaymentCommandRepository payments, OrderCommandRepository orders, PaymentApprovalService approvals, PaymentGateway gateway) {
        this.payments = payments; this.orders = orders; this.approvals = approvals; this.gateway = gateway;
    }
    @Transactional("commandTransactionManager")
    public void processPayment(String mpPaymentId) {
        var mp = gateway.get(mpPaymentId);
        var payment = payments.findByMpPaymentId(mpPaymentId).orElseThrow(() -> new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "Pagamento ainda não sincronizado"));
        orders.findByIdForUpdate(payment.getOrder().getOrderId()).orElseThrow(() -> new PaymentException("Pedido não encontrado"));
        // Reload after taking the order lock: cancellation and checkout use the same lock.
        entityManager.refresh(payment, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        if (payment.getPaymentStatus() == PaymentStatus.REFUNDED) return;
        if ("approved".equals(mp.getStatus())) {
            if (payment.getPaymentStatus() != PaymentStatus.APPROVED) approvals.approve(payment, mpPaymentId);
        } else if (payment.getPaymentStatus() != PaymentStatus.APPROVED) {
            payment.setPaymentStatus(switch (mp.getStatus()) {
                case "rejected" -> PaymentStatus.REJECTED;
                case "cancelled" -> PaymentStatus.CANCELLED;
                case "refunded" -> PaymentStatus.REFUNDED;
                case "in_process" -> PaymentStatus.IN_PROCESS;
                default -> PaymentStatus.PENDING;
            });
        }
        payments.save(payment);
    }
}