package com.project.cqrs.command.payment.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CardCheckoutRequestDTO(
        @NotBlank
        String cardToken,

        @NotNull
        @Min(1)
        @Max(12)
        Integer installments,

        @NotBlank
        String payerEmail,

        @NotBlank
        String payerDocument,

        @NotBlank
        String paymentMethodId
) {}
