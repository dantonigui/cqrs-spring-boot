package com.project.cqrs.command.product.event;

import com.project.cqrs.command.product.model.ProductCommandEntity;

public class ProductDeleteEvent {

    private Long productId;

    //Constructors
    public ProductDeleteEvent(Long productId) {
        this.productId = productId;
    }

    public ProductDeleteEvent() {}

    public static ProductDeleteEvent fromEntity(ProductCommandEntity productEntity) {
        return new ProductDeleteEvent(productEntity.getProductId());
    }

    // Getter
    public Long getProductId() {
        return productId;
    }
}
