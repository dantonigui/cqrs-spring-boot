package com.project.cqrs.query.product.dto.response;

import com.project.cqrs.query.product.model.ProductQueryEntity;

import java.io.Serializable;
import java.math.BigDecimal;

public record ProductQueryDTO(
        Long productId,
        String productName,
        String productCode,
        BigDecimal productPrice,
        String productImage,
        Long categoryId
) implements Serializable {
    public static ProductQueryDTO from (ProductQueryEntity productEntity) {
        return new ProductQueryDTO(
                productEntity.getProductId(),
                productEntity.getProductName(),
                productEntity.getProductCode(),
                productEntity.getProductPrice(),
                productEntity.getProductImage(),
                productEntity.getCategoryId()
        );
    }
}

