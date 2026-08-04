package com.project.cqrs.shared.event.user;

import com.project.cqrs.command.auth.model.UserCommandEntity;
import com.project.cqrs.command.auth.model.UserRole;
import lombok.Getter;

@Getter
public final class UserUpdatedEvent extends UserEvent {

    private  String userEmail;

    private String userName;

    private String userPicture;

    private UserRole userRole;


    private UserUpdatedEvent() {}

    private UserUpdatedEvent(Long userId, String email, String name, String picture, UserRole userRole) {
        super(userId);
        this.userEmail = email;
        this.userName = name;
        this.userPicture = picture;
        this.userRole = userRole;
    }

    public static UserUpdatedEvent userUpdatedEvent(UserCommandEntity userEntity) {
        return new UserUpdatedEvent(
                userEntity.getUserId(),
                userEntity.getUserEmail(),
                userEntity.getUserName(),
                userEntity.getUserPictureUrl(),
                userEntity.getUserRole()
        );
    }
}
