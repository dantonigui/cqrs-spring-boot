package com.project.cqrs.query.product.model;

import com.project.cqrs.shared.event.product.ProductCreateEvent;
import com.project.cqrs.shared.event.product.ProductUpdateEvent;
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

    public static ProductQueryEntity fromCreateEvent(ProductCreateEvent createEvent) {
        return new ProductQueryEntity(
                createEvent.getProductId(),
                createEvent.getProductName(),
                createEvent.getProductCode(),
                createEvent.getProductPrice(),
                createEvent.getProductImage(),
                createEvent.getCategoryId()
        );
    }

    public  void applyUpdatedEvent(ProductUpdateEvent updateEvent) {
        this.productId = updateEvent.getProductId();
        this.productName = updateEvent.getProductName();
        this.productCode = updateEvent.getProductCode();
        this.productPrice = updateEvent.getProductPrice();
        this.productImage = updateEvent.getProductImage();
        this.categoryId = updateEvent.getCategoryId();
    }

}
