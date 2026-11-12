package com.project.cqrs.query.auth.controller;

import com.project.cqrs.config.rateLimit.RateLimit;
import com.project.cqrs.command.auth.repository.UserCommandRepository;
import com.project.cqrs.config.exception.ResourceNotFoundException;
import com.project.cqrs.query.auth.dto.UserQueryDTO;
import com.project.cqrs.query.auth.repository.UserQueryRepository;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/query/auth")
public class AuthQueryController {

    private final UserQueryRepository userQueryRepository;

    private final UserCommandRepository userCommandRepository;

    public AuthQueryController(UserQueryRepository userQueryRepository, UserCommandRepository userCommandRepository) {
        this.userQueryRepository = userQueryRepository;
        this.userCommandRepository = userCommandRepository;
    }

    @RateLimit(requests = 32, durationSeconds = 30)
    @GetMapping("/me")

    public ResponseEntity<UserQueryDTO> me(Principal principal) {
        Long userId = Long.parseLong(principal.getName());

        UserQueryDTO user = userQueryRepository.findById(userId)
                .map(UserQueryDTO::from)
                .orElseGet(() -> userCommandRepository.findById(userId)
                        .map(source -> new UserQueryDTO(source.getUserId(), source.getUserName(),
                                source.getUserEmail(), source.getUserRole()))
                        .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado")));
        return ResponseEntity.ok(user);
    }
}
