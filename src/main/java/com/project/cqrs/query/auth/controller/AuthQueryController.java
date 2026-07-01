package com.project.cqrs.query.auth.controller;

import com.project.cqrs.config.rateLimit.RateLimit;
import com.project.cqrs.query.auth.dto.UserQueryDTO;
import com.project.cqrs.query.auth.model.UserQueryEntity;
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

    public AuthQueryController(UserQueryRepository userQueryRepository) {
        this.userQueryRepository = userQueryRepository;
    }

    @RateLimit(requests = 32, durationSeconds = 30)
    @GetMapping("/me")

    public ResponseEntity<UserQueryDTO> me(Principal principal) {
        Long userId = Long.parseLong(principal.getName());

        UserQueryEntity user = userQueryRepository.findById(userId).orElseThrow(() -> new IllegalStateException("User not found" + userId));

        return ResponseEntity.ok(UserQueryDTO.from(user));
    }
}
