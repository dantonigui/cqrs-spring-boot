package com.project.cqrs.query.auth.model;

import com.project.cqrs.command.auth.model.UserRole;
import com.project.cqrs.shared.event.user.UserCreatedEvent;
import jakarta.persistence.*;
import lombok.Builder;
import lombok.Getter;

@Table(name = "user_query")
@Entity
@Getter
public class UserQueryEntity {

    @Id
    private Long userId;

    @Column(nullable = false)
    private String userName;

    @Column(nullable = false)
    private String userEmail;

    @Column(nullable = false)
    private String userPicture;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private UserRole userRole;

    protected UserQueryEntity() {}

    private UserQueryEntity(Long userId, String userName, String userEmail, String userPicture, UserRole userRole) {
        this.userId = userId;
        this.userName = userName;
        this.userEmail = userEmail;
        this.userPicture = userPicture;
        this.userRole = userRole;
    }

    public static UserQueryEntity from(UserCreatedEvent event) {
        return new UserQueryEntity(
                event.getUserId(),
                event.getUserName(),
                event.getUserEmail(),
                event.getUserPicture(),
                event.getUserRole()
        );
    }

    public void updateUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }
}
