package com.project.cqrs.shared.event.order;

import com.project.cqrs.shared.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record OrderCreatedEvent(
        String eventId,
        Long orderId,
        Long userId,
        OrderStatus status,
        BigDecimal totalAmount,
        LocalDateTime createdAt,
        List<ItemDTO> items
) {
    public record ItemDTO(
            Long productId,
            String productName,
            BigDecimal unitPrice,
            Integer quantity
    ) {}

    public static OrderCreatedEvent of(
            Long orderId,
            Long userId,
            OrderStatus status,
            BigDecimal totalAmount,
            LocalDateTime createdAt,
            List<ItemDTO> items) {
        return new OrderCreatedEvent(
                UUID.randomUUID().toString(),
                orderId,
                userId,
                status,
                totalAmount,
                createdAt,
                items
        );
    }
}
