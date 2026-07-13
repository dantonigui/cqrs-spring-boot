package com.project.cqrs.shared.event.payment;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public record PaymentApprovedEvent(
        String eventId,
        Long paymentId,
        Long orderId,
        String userId,
        BigDecimal amount,
        String paymentMethod
) {
    public static PaymentApprovedEvent of(
            String mpPaymentId,
            Long paymentId,
            Long orderId,
            String userId,
            BigDecimal amount,
            String paymentMethod
    ) {
        String deterministicId = UUID.nameUUIDFromBytes(("approved-" + mpPaymentId).getBytes(StandardCharsets.UTF_8)).toString();

        return new PaymentApprovedEvent(deterministicId, paymentId, orderId, userId, amount, paymentMethod);
    }
}

