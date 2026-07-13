package com.project.cqrs.command.payment.dto.response;

import java.math.BigDecimal;

public record CardPaymentResponseDTO(
        Long paymentId,
        Long orderId,
        String status,
        String statusDetail,
        String cardBrand,
        String cardLastFour,
        Integer installments,
        BigDecimal amount
) {}
