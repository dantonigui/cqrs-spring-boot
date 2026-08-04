package com.project.cqrs.shared.event.user;

import lombok.Getter;

import java.time.Instant;
import java.util.UUID;

@Getter
public abstract sealed class  UserEvent permits UserCreatedEvent, UserLogoutEvent, UserUpdatedEvent  {

    private String eventId;

    private Long userId;

    private  Instant occurredAt;

    protected UserEvent(){}

    protected UserEvent(Long userId) {
        this.eventId = UUID.randomUUID().toString();
        this.userId = userId;
        this.occurredAt = Instant.now();
    }
}
