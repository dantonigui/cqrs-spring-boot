package com.project.cqrs.command.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderItemDTO(
        @NotNull
        Long productId,

        @NotNull
        @Positive
        Integer quantity
) {}
