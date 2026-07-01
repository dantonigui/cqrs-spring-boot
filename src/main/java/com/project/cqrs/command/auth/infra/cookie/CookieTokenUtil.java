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

    @Value("${app.refresh-token.cookie-name}")
    private String REFRESH_COOKIE_NAME;

    @Value("${app.refresh-token.expiration-ms}")
    private long refreshExpirationMs;

    public void writeToken(HttpServletResponse response, String token) {
        ResponseCookie cookie = buildAccessCookie(token);
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public Optional<String> readToken(HttpServletRequest request) {
//        if(request.getCookies() == null) {
//            return Optional.empty();
//        }
//
//        return Arrays.stream(request.getCookies()).filter(c -> COOKIE_NAME.equals(c.getName()))
//                .map(Cookie::getValue)
//                .findFirst();
        return readCookie(request, COOKIE_NAME);
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

    public void writeRefreshToken(HttpServletResponse response, String refreshToken) {
        int maxAgeSeconds = (int) (refreshExpirationMs / 1000);
        ResponseCookie cookie = ResponseCookie.from(REFRESH_COOKIE_NAME, refreshToken)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/auth/refresh")
                .maxAge(maxAgeSeconds)
                .build();

        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    public Optional<String> readRefreshToken(HttpServletRequest request) {
        return readCookie(request, REFRESH_COOKIE_NAME);
    }

    public void clearRefreshToken(HttpServletResponse response) {
        clearCookie(response, REFRESH_COOKIE_NAME);
    }

    public void clearAll(HttpServletResponse response) {
        clearToken(response);
        clearRefreshToken(response);
    }

    private ResponseCookie buildAccessCookie(String value) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Lax")
                .path("/")
                .maxAge(COOKIE_MAX_AGE)
                .build();
    }

    private Optional<String> readCookie(HttpServletRequest request, String name) {
        if(request.getCookies() == null) return Optional.empty();
        return Arrays.stream(request.getCookies())
                .filter(c -> name.equals(c.getName())).map(
                        Cookie::getValue
                ).filter(v -> v != null && !v.isBlank())
                .findFirst();
    }

    private void clearCookie(HttpServletResponse response, String name) {
        ResponseCookie cookie = ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }
}
