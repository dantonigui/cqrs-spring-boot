package com.project.cqrs.query.auth.dto;

import com.project.cqrs.command.auth.model.UserRole;
import com.project.cqrs.query.auth.model.UserQueryEntity;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserQueryDTO(

        @NotNull
        Long userId,

        @NotBlank
        String userName,

        @NotBlank
        @Email
        String userEmail,

        @NotBlank
        UserRole userRole
) {
    public static UserQueryDTO from (UserQueryEntity userQueryEntity) {
        return new UserQueryDTO(
                userQueryEntity.getUserId(),
                userQueryEntity.getUserName(),
                userQueryEntity.getUserEmail(),
                userQueryEntity.getUserRole()
        );
    }
}

