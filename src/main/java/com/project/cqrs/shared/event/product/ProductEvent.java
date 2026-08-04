package com.project.cqrs.shared.event.product;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
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

}
