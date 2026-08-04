package com.project.cqrs.shared.event.product;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public abstract sealed class ProductEvent permits ProductCreateEvent, ProductUpdateEvent, ProductDeleteEvent{

    private final String eventId;

    private  Long productId;

    private final Instant occurredAt;

    protected ProductEvent(Long productId) {
        this.eventId = UUID.randomUUID().toString();
        this.productId = productId;
        this.occurredAt = Instant.now();
    }

    protected ProductEvent() {
        this.eventId = UUID.randomUUID().toString();
        this.occurredAt = Instant.now();
    }

}
