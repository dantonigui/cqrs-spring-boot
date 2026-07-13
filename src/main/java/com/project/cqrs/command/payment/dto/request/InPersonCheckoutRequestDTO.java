package com.project.cqrs.command.payment.dto.request;

import jakarta.validation.constraints.NotBlank;

public record InPersonCheckoutRequestDTO(
        @NotBlank
        String method
) {}