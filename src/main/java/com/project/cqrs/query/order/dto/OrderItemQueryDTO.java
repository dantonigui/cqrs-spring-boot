package com.project.cqrs.query.order.dto;

import com.project.cqrs.query.order.model.OrderItemQueryEntity;

import java.math.BigDecimal;

public record OrderItemQueryDTO(
        Long productId,
        String productName,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal subtotal
) {
    public static OrderItemQueryDTO fromEntity(OrderItemQueryEntity entity) {
        return new OrderItemQueryDTO(
                entity.getProductId(),
                entity.getProductName(),
                entity.getUnitPrice(),
                entity.getQuantity(),
                entity.getUnitPrice().multiply(BigDecimal.valueOf(entity.getQuantity()))
        );
    }
}
