package com.project.cqrs.shared.event.product;

import com.project.cqrs.command.product.model.ProductCommandEntity;

import java.util.UUID;

public final class ProductDeleteEvent extends ProductEvent {

    //Constructors
    private ProductDeleteEvent() {}

    public ProductDeleteEvent(Long productId) {
        super(productId);
    }

    public static ProductDeleteEvent fromEntity(ProductCommandEntity productEntity) {
        return new ProductDeleteEvent(productEntity.getProductId());
    }
}
