package com.project.cqrs.shared.event.product;

import java.time.Instant;

public abstract sealed class ProductEvent permits ProductCreateEvent, ProductUpdateEvent, ProductDeleteEvent{

    private Long productId;

    private Instant occurredAt;

    protected ProductEvent() {

    }

    protected ProductEvent(Long productId) {
        this.productId = productId;
        this.occurredAt = Instant.now();
    }

    public Long getProductId() {
        return productId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
