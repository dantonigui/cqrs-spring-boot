package com.project.cqrs.command.auth.dto;

import com.project.cqrs.command.auth.model.UserCommandEntity;
import com.project.cqrs.command.auth.model.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserRequestDTO(

        @NotNull
        Long userId,

        @NotBlank
        @Email
        String userEmail,

        @NotBlank
        UserRole userRole
) {
    public static UserRequestDTO from(UserCommandEntity userCommandEntity) {
        return new UserRequestDTO(
                userCommandEntity.getUserId(),
                userCommandEntity.getUserEmail(),
                userCommandEntity.getUserRole()
        );
    }
}
