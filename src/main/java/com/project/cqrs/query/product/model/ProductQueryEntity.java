package com.project.cqrs.query.product.model;

import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Entity
@Getter
@Setter
@Table(name = "product-query")
public class ProductQueryEntity {
    @Id
    private Long productId;
    private String productName;
    private String productCode;
    private BigDecimal productPrice;
    private String productImage;
    private Long  categoryId;

    protected ProductQueryEntity() {
    }

    public ProductQueryEntity(Long productId, String productName, String productCode, BigDecimal productPrice, String productImage, Long categoryId) {
        this.productId = productId;
        this.productName = productName;
        this.productCode = productCode;
        this.productPrice = productPrice;
        this.productImage = productImage;
        this.categoryId = categoryId;
    }

}
