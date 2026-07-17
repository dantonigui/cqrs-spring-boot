package com.project.cqrs.shared.event.payment;

import com.project.cqrs.shared.enums.PaymentMethod;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record PaymentApprovedEvent(
        String eventId,
        Long paymentId,
        Long orderId,
        String userId,
        BigDecimal amount,
        PaymentMethod paymentMethod
) {
    public static PaymentApprovedEvent of(
            String mpPaymentId,
            Long paymentId,
            Long orderId,
            String userId,
            BigDecimal amount,
            PaymentMethod paymentMethod
    ) {
        String deterministicId = UUID.nameUUIDFromBytes(("approved-" + mpPaymentId).getBytes(StandardCharsets.UTF_8)).toString();

        return new PaymentApprovedEvent(deterministicId, paymentId, orderId, userId, amount, paymentMethod);
    }
}

