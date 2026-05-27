package com.project.cqrs.command.auth.controller;

import com.project.cqrs.command.auth.infra.cookie.CookieTokenUtil;
import com.project.cqrs.command.auth.infra.kafka.UserEventProducer;
import com.project.cqrs.config.rateLimit.RateLimit;
import com.project.cqrs.shared.event.user.UserLogoutEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/command/auth")
public class AuthController {

    private final CookieTokenUtil cookieTokenUtil;
    private final UserEventProducer userEventProducer;

    public AuthController(CookieTokenUtil cookieTokenUtil, UserEventProducer userEventProducer) {
        this.cookieTokenUtil = cookieTokenUtil;
        this.userEventProducer = userEventProducer;
    }

    @RateLimit(requests = 5, durationSeconds = 30)
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletResponse response, Principal principal) {
        cookieTokenUtil.clearToken(response);

        Long userId = Long.parseLong(principal.getName());
        userEventProducer.publishUserLogoutEvent(UserLogoutEvent.logoutEvent(userId));
        return ResponseEntity.noContent().build();
    }

}
