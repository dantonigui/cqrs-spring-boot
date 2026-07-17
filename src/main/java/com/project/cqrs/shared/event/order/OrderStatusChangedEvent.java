package com.project.cqrs.shared.event.order;

import com.project.cqrs.shared.enums.OrderStatus;

import java.util.UUID;

public record OrderStatusChangedEvent(
        String eventId,
        Long orderId,
        Long userId,
        OrderStatus oldStatus,
        OrderStatus newStatus
) {
    public static OrderStatusChangedEvent of(
            Long orderId,
            Long userId,
            OrderStatus oldStatus,
            OrderStatus newStatus
    ) {
        return new OrderStatusChangedEvent(
                UUID.randomUUID().toString(),
                orderId,
                userId,
                oldStatus,
                newStatus
        );
    }
}
