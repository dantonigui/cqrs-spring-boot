package com.project.cqrs.command.payment.dto.response;

import java.math.BigDecimal;

public record InPersonPaymentResponseDTO(
        Long paymentId,
        Long orderId,
        String method,
        String status,
        BigDecimal amount
) {}