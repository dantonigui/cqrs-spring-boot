package com.project.cqrs.command.auth.controller;

import com.project.cqrs.command.auth.infra.cookie.CookieTokenUtil;
import com.project.cqrs.command.auth.infra.kafka.UserEventProducer;
import com.project.cqrs.command.auth.service.LogoutService;
import com.project.cqrs.config.rateLimit.RateLimit;
import com.project.cqrs.shared.event.user.UserLogoutEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/command/auth")
public class AuthController {

    private final LogoutService logoutService;

    public AuthController(LogoutService logoutService) {
        this.logoutService = logoutService;
    }

    @RateLimit(requests = 5, durationSeconds = 30)
    @PostMapping("/logout")
    public ResponseEntity<Map<String, String>> logout(HttpServletRequest request, HttpServletResponse response ) {

        logoutService.logout(request, response);
        return ResponseEntity.ok(Map.of("message", "Logout realizado com sucesso!"));
    }

    @PostMapping("/logout-all")
    public ResponseEntity<Map<String, String>> logoutAll(HttpServletRequest request, HttpServletResponse response ) {

        logoutService.logoutAll(request, response);
        return ResponseEntity.ok(Map.of("message", "Sessão encerrada em todos os dispositivos"));
    }

}
