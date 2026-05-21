package com.project.cqrs.shared.event.user;

import com.project.cqrs.command.auth.model.UserCommandEntity;
import com.project.cqrs.command.auth.model.UserRole;

public final class UserCreatedEvent extends UserEvent{

    private  String userName;

    private  String userEmail;

    private  String userPicture;

    private  UserRole userRole;

    public UserCreatedEvent() {
        super();
    }

    private UserCreatedEvent(Long userId, String userName, String userEmail, String userPicture, UserRole userRole) {
        super(userId);
        this.userName = userName;
        this.userEmail = userEmail;
        this.userPicture = userPicture;
        this.userRole = userRole;
    }

    public static UserCreatedEvent createdEvent(UserCommandEntity userEntity) {
            return  new UserCreatedEvent(
                    userEntity.getUserId(),
                    userEntity.getUserName(),
                    userEntity.getUserEmail(),
                    userEntity.getUserPictureUrl(),
                    userEntity.getUserRole()
            );
    }


    public String getUserName() {
        return userName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getUserPicture() {
        return userPicture;
    }

    public UserRole getUserRole() {
        return userRole;
    }
}
