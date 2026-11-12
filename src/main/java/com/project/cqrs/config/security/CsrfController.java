package com.project.cqrs.config.security;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
@RestController
public class CsrfController {
    @GetMapping("/api/v1/csrf")
    public Map<String, String> csrf(CsrfToken token) { return Map.of("token", token.getToken(), "headerName", token.getHeaderName()); }
}