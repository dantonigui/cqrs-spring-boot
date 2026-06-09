package com.project.cqrs.shared.event.product;

import java.time.Instant;
import java.util.UUID;

public abstract sealed class ProductEvent permits ProductCreateEvent, ProductUpdateEvent, ProductDeleteEvent{

    private  String eventId;

    private  Long productId;

    private Instant occurredAt;



    protected ProductEvent(Long productId) {
        this.eventId = UUID.randomUUID().toString();
        this.productId = productId;
        this.occurredAt = Instant.now();
    }

    protected ProductEvent() {

    }

    public String getEventId() {
        return eventId;
    }

    public Long getProductId() {
        return productId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
