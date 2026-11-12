package com.project.cqrs.shared.event.payment;
import com.project.cqrs.command.payment.model.PaymentCommandEntity;
import com.project.cqrs.shared.enums.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
public record PaymentApprovedEvent(String eventId, Long paymentId, Long orderId, Long userId, BigDecimal amount, PaymentMethod paymentMethod, String mpPaymentId, PaymentType paymentType) {
    public PaymentApprovedEvent(String eventId, Long paymentId, Long orderId, Long userId, BigDecimal amount, PaymentMethod method) {
        this(eventId, paymentId, orderId, userId, amount, method, null, PaymentType.ONLINE);
    }
    public static PaymentApprovedEvent of(String mpPaymentId, Long paymentId, Long orderId, Long userId, BigDecimal amount, PaymentMethod method) {
        String key = mpPaymentId != null ? mpPaymentId : "local-" + paymentId;
        String id = UUID.nameUUIDFromBytes(("approved-" + key).getBytes(StandardCharsets.UTF_8)).toString();
        return new PaymentApprovedEvent(id, paymentId, orderId, userId, amount, method, mpPaymentId, mpPaymentId == null ? PaymentType.IN_PERSON : PaymentType.ONLINE);
    }
    public static PaymentApprovedEvent from(PaymentCommandEntity payment) {
        return of(payment.getMpPaymentId(), payment.getId(), payment.getOrder().getOrderId(), payment.getOrder().getUserId(), payment.getTransactionAmount(), payment.getPaymentMethod());
    }
}