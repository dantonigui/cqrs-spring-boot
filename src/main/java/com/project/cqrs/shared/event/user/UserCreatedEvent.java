package com.project.cqrs.shared.event.user;

import com.project.cqrs.command.auth.model.UserCommandEntity;

public final class UserCreatedEvent extends UserEvent{

    private final String userName;

    private final String userEmail;

    private final String userPicture;

    private final String userRole;

    private UserCreatedEvent(Long userId, String userName, String userEmail, String userPicture, String userRole) {
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
                    userEntity.getUserRole().name()
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

    public String getUserRole() {
        return userRole;
    }
}
