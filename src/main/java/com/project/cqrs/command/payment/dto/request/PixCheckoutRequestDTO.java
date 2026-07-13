package com.project.cqrs.command.payment.dto.request;

import jakarta.validation.constraints.NotBlank;

public record PixCheckoutRequestDTO(
        @NotBlank
        String payerEmail,

        @NotBlank
        String payerFirstName,

        @NotBlank
        String payerLastName,

        @NotBlank
        String payerDocument //CPF sem pontuação
) {}
