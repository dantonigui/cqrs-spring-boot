package com.project.cqrs.command.payment.dto.request;

import jakarta.validation.constraints.NotBlank;

public record InPersonCheckoutRequestDTO(
        @jakarta.validation.constraints.Pattern(regexp = "CASH|CREDIT_CARD|DEBIT_CARD|PIX")
        @NotBlank
        String method
) {}