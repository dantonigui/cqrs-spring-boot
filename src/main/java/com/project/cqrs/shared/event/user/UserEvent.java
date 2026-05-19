package com.project.cqrs.shared.event.user;

import java.time.Instant;

public abstract sealed class  UserEvent permits UserCreatedEvent, UserLogoutEvent, UserUpdatedEvent  {

    private final Long userId;

    private final Instant occurredAt;

    protected UserEvent(Long userId) {
        this.userId = userId;
        this.occurredAt = Instant.now();
    }

    public Long getUserId() {
        return userId;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
