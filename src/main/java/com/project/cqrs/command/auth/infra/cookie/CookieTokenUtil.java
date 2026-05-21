package com.project.cqrs.command.auth.infra.cookie;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Optional;

@Component
public class CookieTokenUtil {

    @Value("${app.cookie.name}")
    private String COOKIE_NAME;

    @Value("${app.cookie.max-age}")
    private int COOKIE_MAX_AGE;

    public void writeToken(HttpServletResponse response, String token) {
        ResponseCookie cookie = buildCookie(token);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public Optional<String> readToken(HttpServletRequest request) {
        if(request.getCookies() == null) {
            return Optional.empty();
        }

        return Arrays.stream(request.getCookies()).filter(c -> COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst();
        //pedir explicação aqui
    }

    public void clearToken(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(COOKIE_NAME, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private ResponseCookie buildCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(COOKIE_MAX_AGE)
                .build();
    }
}
