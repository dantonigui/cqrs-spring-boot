package com.project.cqrs.command.order.dto;

import com.project.cqrs.shared.enums.OrderStatus;

import java.math.BigDecimal;
import java.util.List;

public record OrderResponseDTO(
        Long orderId,
        OrderStatus status,
        BigDecimal amount,
        List<OrderResponseDTO.ItemDTO> items
){
    public record ItemDTO(
            Long productId,
            String productName,
            BigDecimal unitPrice,
            Integer quantity
    ) {}
}