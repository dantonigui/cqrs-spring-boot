package com.project.cqrs.query.order.dto;

import com.project.cqrs.query.order.model.OrderQueryEntity;
import com.project.cqrs.query.payment.dto.PaymentQueryDTO;
import com.project.cqrs.shared.enums.OrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public record OrderQueryResponseDTO(
        Long orderId,
        Long userId,
        OrderStatus status,
        BigDecimal totalAmount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        List<OrderItemQueryDTO> items,
        List<PaymentQueryDTO> payments
) {
    public static OrderQueryResponseDTO from(OrderQueryEntity orderQueryEntity) {
        return new OrderQueryResponseDTO(
                orderQueryEntity.getOrderId(),
                orderQueryEntity.getUserId(),
                orderQueryEntity.getStatus(),
                orderQueryEntity.getTotalAmount(),
                orderQueryEntity.getCreatedAt(),
                orderQueryEntity.getUpdatedAt(),
                orderQueryEntity.getItems().stream().map(OrderItemQueryDTO::fromEntity).toList(),
                orderQueryEntity.getPayments().stream()
                        .map(PaymentQueryDTO::from).toList()
        );
    }

    public static OrderQueryResponseDTO summary(OrderQueryEntity orderQueryEntity) {
        return new OrderQueryResponseDTO(
                orderQueryEntity.getOrderId(),
                orderQueryEntity.getUserId(),
                orderQueryEntity.getStatus(),
                orderQueryEntity.getTotalAmount(),
                orderQueryEntity.getCreatedAt(),
                orderQueryEntity.getUpdatedAt(),
                List.of(),
                List.of()
        );
    }
}
