package com.project.cqrs.shared.event.user;

import com.project.cqrs.command.auth.model.UserCommandEntity;

public final class UserUpdatedEvent extends UserEvent {

    private final String email;

    private UserUpdatedEvent(Long userId, String email) {
        super(userId);
        this.email = email;
    }

    public static UserUpdatedEvent userUpdatedEvent(UserCommandEntity userEntity) {
        return new UserUpdatedEvent(
                userEntity.getUserId(),
                userEntity.getUserEmail()
        );
    }

    public String getEmail() {
        return email;
    }
}
