package com.project.cqrs.command.product.dto.request;

import com.project.cqrs.command.category.model.CategoryCommandEntity;
import com.project.cqrs.command.product.model.ProductCommandEntity;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record UpdateProductRequestDTO(
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
) {
    public void applyTo(ProductCommandEntity productEntity, CategoryCommandEntity categoryId) {
        productEntity.updateProductName(productName);
        productEntity.updateProductCode(productCode);
        productEntity.updateProductPrice(productPrice);
        productEntity.updateProductImage(productImage);
        productEntity.updateProductCategory(categoryId);
    }
}
