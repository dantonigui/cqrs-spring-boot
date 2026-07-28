package com.project.cqrs.command.auth.service;

import com.project.cqrs.command.auth.infra.cookie.CookieTokenUtil;
import com.project.cqrs.command.auth.infra.kafka.UserEventProducer;
import com.project.cqrs.command.auth.infra.security.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LogoutService")
class LogoutServiceTest {

    @Mock private CookieTokenUtil     cookieTokenUtil;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private UserEventProducer   userEventProducer;
    @Mock private HttpServletRequest  request;
    @Mock private HttpServletResponse response;

    private LogoutService logoutService;

    private static final String USER_ID       = "42";
    private static final String REFRESH_TOKEN = "refresh-token-ativo";

    @BeforeEach
    void setUp() {
        logoutService = new LogoutService(
                cookieTokenUtil, refreshTokenService, userEventProducer);
    }

    @AfterEach
    void tearDown() {
        // Evita vazamento de contexto de segurança entre testes
        SecurityContextHolder.clearContext();
    }

    private void authenticateAs(String userId) {
        Authentication auth = new UsernamePasswordAuthenticationToken(
                userId, null, java.util.List.of());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    // ── logout() ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("logout()")
    class Logout {

        @Test
        @DisplayName("deve revogar o refresh token do Redis quando presente no cookie")
        void shouldRevokeRefreshTokenWhenPresent() {
            authenticateAs(USER_ID);
            when(cookieTokenUtil.readRefreshToken(request))
                    .thenReturn(Optional.of(REFRESH_TOKEN));

            logoutService.logout(request, response);

            verify(refreshTokenService).revoke(REFRESH_TOKEN);
        }

        @Test
        @DisplayName("não deve chamar revoke quando não há refresh token no cookie")
        void shouldNotRevokeWhenNoRefreshTokenCookie() {
            authenticateAs(USER_ID);
            when(cookieTokenUtil.readRefreshToken(request))
                    .thenReturn(Optional.empty());

            logoutService.logout(request, response);

            verify(refreshTokenService, never()).revoke(anyString());
        }

        @Test
        @DisplayName("deve limpar ambos os cookies")
        void shouldClearAllCookies() {
            authenticateAs(USER_ID);
            when(cookieTokenUtil.readRefreshToken(request))
                    .thenReturn(Optional.empty());

            logoutService.logout(request, response);

            verify(cookieTokenUtil).clearAll(response);
        }

        @Test
        @DisplayName("deve publicar UserLogoutEvent quando há usuário autenticado")
        void shouldPublishLogoutEventWhenAuthenticated() {
            authenticateAs(USER_ID);
            when(cookieTokenUtil.readRefreshToken(request))
                    .thenReturn(Optional.empty());

            logoutService.logout(request, response);

            verify(userEventProducer).publishUserLogoutEvent(any());
        }

        @Test
        @DisplayName("não deve publicar evento quando não há usuário autenticado")
        void shouldNotPublishEventWhenNotAuthenticated() {
            // Nenhuma autenticação no SecurityContext
            when(cookieTokenUtil.readRefreshToken(request))
                    .thenReturn(Optional.empty());

            logoutService.logout(request, response);

            verify(userEventProducer, never()).publishUserLogoutEvent(any());
        }

        @Test
        @DisplayName("deve limpar o SecurityContext após o logout")
        void shouldClearSecurityContext() {
            authenticateAs(USER_ID);
            when(cookieTokenUtil.readRefreshToken(request))
                    .thenReturn(Optional.empty());

            logoutService.logout(request, response);

            org.assertj.core.api.Assertions
                    .assertThat(SecurityContextHolder.getContext().getAuthentication())
                    .isNull();
        }
    }

    // ── logoutAll() ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("logoutAll()")
    class LogoutAll {

        @Test
        @DisplayName("deve revogar TODOS os refresh tokens do usuário")
        void shouldRevokeAllTokensForUser() {
            authenticateAs(USER_ID);

            logoutService.logoutAll(request, response);

            verify(refreshTokenService).revokeAll(USER_ID);
        }

        @Test
        @DisplayName("deve limpar os cookies mesmo em logoutAll")
        void shouldClearCookiesInLogoutAll() {
            authenticateAs(USER_ID);

            logoutService.logoutAll(request, response);

            verify(cookieTokenUtil).clearAll(response);
        }

        @Test
        @DisplayName("não deve chamar revokeAll quando não há usuário autenticado")
        void shouldNotCallRevokeAllWhenNotAuthenticated() {
            logoutService.logoutAll(request, response);

            verify(refreshTokenService, never()).revokeAll(anyString());
        }
    }
}