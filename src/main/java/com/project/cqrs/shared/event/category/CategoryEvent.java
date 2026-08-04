package com.project.cqrs.shared.event.category;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public abstract sealed class CategoryEvent permits CategoryCreateEvent, CategoryDeleteEvent, CategoryUpdateEvent {

    private String eventId;

    private Long categoryId;

    private Instant occurredAt;

    protected CategoryEvent(Long categoryId) {
        this.eventId = UUID.randomUUID().toString();
        this.categoryId = categoryId;
        this.occurredAt = Instant.now();
    }

    protected CategoryEvent() {}

}
