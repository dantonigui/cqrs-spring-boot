package com.project.cqrs.query.auth.controller;

import com.project.cqrs.command.auth.model.UserCommandEntity;
import com.project.cqrs.command.auth.model.UserRole;
import com.project.cqrs.command.auth.repository.UserCommandRepository;
import com.project.cqrs.query.auth.model.UserQueryEntity;
import com.project.cqrs.query.auth.repository.UserQueryRepository;
import com.project.cqrs.config.exception.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AuthQueryControllerTest {
    @Test void firstLoginReadsCommandUserUntilTheProjectionExists() {
        var query = mock(UserQueryRepository.class);
        var command = mock(UserCommandRepository.class);
        var user = UserCommandEntity.createUser("Name", "user@example.com", "picture", "google", UserRole.USER);
        ReflectionTestUtils.setField(user, "userId", 42L);
        when(command.findById(42L)).thenReturn(Optional.of(user));
        var response = new AuthQueryController(query, command).me(() -> "42");
        assertThat(response.getBody().userId()).isEqualTo(42L);
        assertThat(response.getBody().userEmail()).isEqualTo("user@example.com");
    }
    @Test void existingProjectionDoesNotReadTheCommandDatabase() {
        var query = mock(UserQueryRepository.class);
        var command = mock(UserCommandRepository.class);
        var user = mock(UserQueryEntity.class);
        when(user.getUserId()).thenReturn(42L);
        when(query.findById(42L)).thenReturn(Optional.of(user));
        assertThat(new AuthQueryController(query, command).me(() -> "42").getBody().userId()).isEqualTo(42L);
        verifyNoInteractions(command);
    }
    @Test void unknownUserReturnsNotFoundInsteadOfAnInternalError() {
        var controller = new AuthQueryController(mock(UserQueryRepository.class), mock(UserCommandRepository.class));
        assertThatThrownBy(() -> controller.me(() -> "42")).isInstanceOf(ResourceNotFoundException.class);
    }
}
