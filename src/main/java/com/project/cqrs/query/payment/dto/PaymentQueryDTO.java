package com.project.cqrs.query.payment.dto;

import com.project.cqrs.query.order.model.OrderItemQueryEntity;
import com.project.cqrs.query.order.model.OrderQueryEntity;
import com.project.cqrs.query.payment.model.PaymentQueryEntity;
import com.project.cqrs.shared.enums.PaymentMethod;
import com.project.cqrs.shared.enums.PaymentStatus;
import com.project.cqrs.shared.enums.PaymentType;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentQueryDTO(
        Long paymentId,
        PaymentMethod paymentMethod,
        PaymentType paymentType,
        PaymentStatus status,
        String mpPaymentId,
        BigDecimal transactionAmount,
        Integer installments,
        String cardLastFour,
        String cardBrand,
        String inPersonMethod,
        LocalDateTime createdAt
) {
    public static PaymentQueryDTO from(PaymentQueryEntity entity) {
        return new PaymentQueryDTO(
                entity.getId(),
                entity.getPaymentMethod(),
                entity.getPaymentType(),
                entity.getPaymentStatus(),
                entity.getMpPaymentId(),
                entity.getTransactionAmount(),
                entity.getInstallments(),
                entity.getCardLastFour(),
                entity.getCardBrand(),
                entity.getInPersonMethod(),
                entity.getCreatedAt()
        );
    }
}
