package com.project.cqrs.command.auth.model;

import jakarta.persistence.*;
import lombok.Builder;

@Entity
@Table(name = "user_command")
public class UserCommandEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    private String userName;

    private String userEmail;

    private String userPictureUrl;

    private String userGoogleId;

    @Enumerated(EnumType.STRING)
    private UserRole userRole;

    //Constructor
    protected UserCommandEntity() {}

    @Builder
    private UserCommandEntity(String userName, String userEmail, String userPictureUrl, String userGoogleId, UserRole userRole) {
        this.userName = userName;
        this.userEmail = userEmail;
        this.userPictureUrl = userPictureUrl;
        this.userGoogleId = userGoogleId;
        this.userRole = userRole;
    }

    public static UserCommandEntity createUser(String userName, String userEmail, String userPictureUrl, String userGoogleId, UserRole userRole) {
        return UserCommandEntity.builder()
                .userName(userName)
                .userEmail(userEmail)
                .userPictureUrl(userPictureUrl)
                .userGoogleId(userGoogleId)
                .userRole(userRole)
                .build();
    }

    //Getters
    public Long getUserId() {
        return userId;
    }

    public String getUserName() {
        return userName;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public String getUserPictureUrl() {
        return userPictureUrl;
    }

    public String getUserGoogleId() {
        return userGoogleId;
    }

    public UserRole getUserRole() {
        return userRole;
    }

    public void syncFromGoogle(String userEmail) {
        this.userEmail = userEmail;
    }
}
