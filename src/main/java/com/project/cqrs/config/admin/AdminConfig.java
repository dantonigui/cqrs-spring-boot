package com.project.cqrs.config.admin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;

@Component
public class AdminConfig {

    @Value("${app.security.email-admin}") private String emailAdmin;

    public boolean isAdmin(String email) {
        return Arrays.stream(emailAdmin.split(","))
                .map(String::trim).anyMatch(email::equalsIgnoreCase);
    }
}
