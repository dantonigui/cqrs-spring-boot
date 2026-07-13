package com.project.cqrs.command.payment.dto.response;

import java.math.BigDecimal;

public record PixPaymentResponseDTO(
        Long paymentId,
        Long orderId,
        String status,
        String pixQrCode,
        String pixQrCodeBase64,
        String pixExpiration,
        BigDecimal amount
) {}
