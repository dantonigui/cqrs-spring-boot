package com.project.cqrs.shared.event.product;

import com.project.cqrs.command.product.model.ProductCommandEntity;

import java.math.BigDecimal;

public final class ProductUpdateEvent extends ProductEvent {

    private String productName;
    private String productCode;
    private BigDecimal productPrice;
    private String productImage;
    private Long categoryId;

    //Constructors
    public ProductUpdateEvent(Long productId, String productName, String productCode, BigDecimal productPrice, String productImage, Long categoryId) {
        super(productId);
        this.productName = productName;
        this.productCode = productCode;
        this.productPrice = productPrice;
        this.productImage = productImage;
        this.categoryId = categoryId;

    }

    public ProductUpdateEvent() {}

    public static ProductUpdateEvent fromEntity(ProductCommandEntity productCommandEntity) {
        return  new ProductUpdateEvent(
                productCommandEntity.getProductId(),
                productCommandEntity.getProductName(),
                productCommandEntity.getProductCode(),
                productCommandEntity.getProductPrice(),
                productCommandEntity.getProductImage(),
                productCommandEntity.getCategoryCommandEntity().getCategoryId()
        );
    }

    //Getters
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

    //Setters
    public void setProductName(String productName) {
        this.productName = productName;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public void setProductPrice(BigDecimal productPrice) {
        this.productPrice = productPrice;
    }

    public void setProductImage(String productImage) {
        this.productImage = productImage;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }
}
