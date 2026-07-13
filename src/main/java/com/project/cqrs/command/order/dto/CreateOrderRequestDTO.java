package com.project.cqrs.command.order.dto;

import jakarta.validation.constraints.*;


import java.util.List;

public record CreateOrderRequestDTO(

        @NotEmpty
        List<OrderItemDTO> items
) {}
