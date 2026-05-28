package com.project.cqrs.command.auth.dto;

import com.project.cqrs.command.auth.model.UserRole;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateUserRequestDTO(
        @NotBlank
        @Size(min = 3)
        String userName,

        @NotBlank
        @Email
        String userEmail,

        @NotBlank
        String userPictureUrl,

        @NotBlank
        String userGoogleId,

        @NotBlank
        UserRole userRole
) {
}
