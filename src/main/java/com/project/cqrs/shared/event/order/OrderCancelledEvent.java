package com.project.cqrs.shared.event.order;

import java.math.BigDecimal;
import java.util.UUID;

public record OrderCancelledEvent(
        String eventId,
        Long orderId,
        Long userId,
        String mpPaymentId,
        BigDecimal amount,
        String reason
) {
    public static OrderCancelledEvent of(
            Long orderId,
            Long userId,
            String mpPaymentId,
            BigDecimal amount,
            String reason
    ) {
        return new OrderCancelledEvent(
                UUID.randomUUID().toString(),
                orderId,
                userId,
                mpPaymentId,
                amount,
                reason
        );
    }
}
