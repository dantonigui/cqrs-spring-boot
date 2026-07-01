package com.project.cqrs.command.auth.service;

import com.project.cqrs.command.auth.infra.cookie.CookieTokenUtil;
import com.project.cqrs.command.auth.infra.kafka.UserEventProducer;
import com.project.cqrs.command.auth.infra.security.RefreshTokenService;
import com.project.cqrs.shared.event.user.UserLogoutEvent;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class LogoutService {

    private static final Logger logger = LoggerFactory.getLogger(LogoutService.class);

    private final CookieTokenUtil cookieTokenUtil;
    private final RefreshTokenService refreshTokenService;
    private final UserEventProducer userEventProducer;

    public LogoutService(CookieTokenUtil cookieTokenUtil, RefreshTokenService refreshTokenService, UserEventProducer userEventProducer) {
        this.cookieTokenUtil = cookieTokenUtil;
        this.refreshTokenService = refreshTokenService;
        this.userEventProducer = userEventProducer;
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String userId = resolveUserId();

        cookieTokenUtil.readRefreshToken(request).ifPresent(refreshToken -> {
            refreshTokenService.revoke(refreshToken);
            logger.info("Refresh token has been revoked", userId);
        });

        cookieTokenUtil.clearAll(response);

        SecurityContextHolder.clearContext();

        if (userId != null) {
            userEventProducer.publishUserLogoutEvent(
                    UserLogoutEvent.logoutEvent(Long.parseLong(userId)));
        }

        logger.info("UserLogoutEvent has been cleared", userId);
    }

    public void logoutAll(HttpServletRequest request, HttpServletResponse response) {
        String userId = resolveUserId();
        if (userId != null) {
            refreshTokenService.revokeAll(userId);
            logger.info("Refresh token has been revoked", userId);
        }

        cookieTokenUtil.clearAll(response);
        SecurityContextHolder.clearContext();
    }

    private String resolveUserId() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getPrincipal() == null) return null;
        return auth.getPrincipal().toString();
    }
}
