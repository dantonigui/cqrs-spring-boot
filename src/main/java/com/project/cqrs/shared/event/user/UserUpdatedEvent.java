package com.project.cqrs.shared.event.user;

import com.project.cqrs.command.auth.model.UserCommandEntity;

public final class UserUpdatedEvent extends UserEvent {

    private  String email;

    private String name;


    public UserUpdatedEvent() {
        super();
    }

    private UserUpdatedEvent(Long userId, String email, String name) {
        super(userId);
        this.email = email;
        this.name = name;
    }

    public static UserUpdatedEvent userUpdatedEvent(UserCommandEntity userEntity) {
        return new UserUpdatedEvent(
                userEntity.getUserId(),
                userEntity.getUserEmail(),
                userEntity.getUserName()
        );
    }

    public String getEmail() {
        return email;
    }

    public String getName() {
        return name;
    }

}
