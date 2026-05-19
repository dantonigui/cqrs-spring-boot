package com.project.cqrs.shared.event.product;

import com.project.cqrs.command.product.model.ProductCommandEntity;

import java.math.BigDecimal;

public class ProductCreateEvent {

    private Long productId;
    private String productName;
    private String productCode;
    private BigDecimal productPrice;
    private String productImage;
    private Long categoryId;

    //Constructors
    public ProductCreateEvent(Long productId, String productName, String productCode, BigDecimal productPrice, String productImage, Long categoryId) {
        this.productId = productId;
        this.productName = productName;
        this.productCode = productCode;
        this.productPrice = productPrice;
        this.productImage = productImage;
        this.categoryId = categoryId;
    }

    protected ProductCreateEvent() {}

    public static ProductCreateEvent fromEntity(ProductCommandEntity productEntity) {
        return new ProductCreateEvent(
                productEntity.getProductId(),
                productEntity.getProductName(),
                productEntity.getProductCode(),
                productEntity.getProductPrice(),
                productEntity.getProductImage(),
                productEntity.getCategoryCommandEntity().getCategoryId()
        );
    }

    //Getters
    public Long getProductId() {
        return productId;
    }

    public String getProductName() {
        return productName;
    }
    public String getProductCode() {
        return productCode;
    }

    public BigDecimal getProductPrice() {
        return productPrice;
    }

    public String getProductImage() {
        return productImage;
    }

    public Long getCategoryId() {
        return categoryId;
    }
}
