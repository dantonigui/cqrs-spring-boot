package com.project.cqrs.query.auth.model;

import com.project.cqrs.command.auth.model.UserRole;
import jakarta.persistence.*;
import lombok.Getter;

@Table(name = "user_query")
@Entity
@Getter
public class UserQueryEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long userId;

    @Column(nullable = false)
    private String userName;

    @Column(nullable = false)
    private String userEmail;

    @Column(nullable = false)
    private UserRole userRole;

    protected UserQueryEntity() {}

    public UserQueryEntity(Long userId, String userName, String userEmail, UserRole userRole) {
        this.userId = userId;
        this.userName = userName;
        this.userEmail = userEmail;
        this.userRole = userRole;
    }
}
