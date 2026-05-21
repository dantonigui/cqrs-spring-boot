package com.project.cqrs.shared.event.user;

import java.time.Instant;

public abstract sealed class  UserEvent permits UserCreatedEvent, UserLogoutEvent, UserUpdatedEvent  {

    private Long userId;

    private  Instant occurredAt;

    protected UserEvent(){

    }

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
