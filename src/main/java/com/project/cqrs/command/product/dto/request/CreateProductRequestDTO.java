package com.project.cqrs.command.product.dto.request;


import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record CreateProductRequestDTO(
    @NotBlank(message = "The product name is required")
    @Size(min = 5, message = "The product name must be longer than 5 characters")
    String productName,

    @NotBlank(message = "The product code is required")
    @Size(min = 1, message = "The product code must be longer than 5 characters")
    String productCode,

    @NotNull(message = "The product price cannot be zero")
    @DecimalMin(value = "0.01", message = "The product price must be greater than 0")
    BigDecimal productPrice,

    @NotBlank(message = "The product code is required")
    @Size(min = 5, message = "The product image must be longer than 5 characters")
    String productImage,

    Long categoryId
) {}
