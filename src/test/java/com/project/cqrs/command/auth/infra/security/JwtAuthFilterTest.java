package com.project.cqrs.command.auth.infra.security;

import com.project.cqrs.command.auth.infra.cookie.CookieTokenUtil;
import com.project.cqrs.command.auth.model.UserCommandEntity;
import com.project.cqrs.command.auth.model.UserRole;
import com.project.cqrs.command.auth.repository.UserCommandRepository;
import com.project.cqrs.command.auth.service.LogoutService;
import com.project.cqrs.command.auth.infra.kafka.UserEventProducer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@ExtendWith(MockitoExtension.class)
class JwtAuthFilterTest {
    @Mock JwtTokenService jwt;
    @Mock CookieTokenUtil cookies;
    @Mock RefreshTokenService sessions;
    @Mock UserCommandRepository users;
    JwtAuthFilter filter;
    MockHttpServletRequest request;
    MockHttpServletResponse response;
    final RefreshTokenService.SessionTokens session = new RefreshTokenService.SessionTokens("42", "sid", "refresh");

    @BeforeEach void setup() {
        SecurityContextHolder.clearContext();
        filter = new JwtAuthFilter(jwt, cookies, sessions, users);
        request = new MockHttpServletRequest("GET", "/api/v1/query/auth/me");
        request.setServletPath("/api/v1/query/auth/me");
        response = new MockHttpServletResponse();
    }
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    private Claims claims() { return Jwts.claims().subject("42").add("sid", "sid").add("role", "USER").build(); }
    private void expiredAccess() {
        when(cookies.readToken(request)).thenReturn(Optional.of("expired"));
        when(jwt.parseToken("expired")).thenReturn(Optional.empty());
        when(cookies.readRefreshToken(request)).thenReturn(Optional.of("refresh"));
        when(sessions.lookup("refresh")).thenReturn(Optional.of(session));
        var user = UserCommandEntity.createUser("Name", "user@example.com", "picture", "google", UserRole.USER);
        ReflectionTestUtils.setField(user, "userId", 42L);
        when(users.findById(42L)).thenReturn(Optional.of(user));
    }

    @Test void validAccessRequiresAnActiveSession() throws Exception {
        when(cookies.readToken(request)).thenReturn(Optional.of("access"));
        when(jwt.parseToken("access")).thenReturn(Optional.of(claims()));
        when(sessions.isSessionActive("sid", "42")).thenReturn(true);
        filter.doFilter(request, response, (req, res) -> {});
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("42");
        assertThat(request.getAttribute(JwtAuthFilter.SESSION_ATTRIBUTE)).isEqualTo("sid");
        verify(sessions, never()).rotate(anyString());
    }

    @Test void copiedAccessFromRevokedSessionIsRejected() throws Exception {
        when(cookies.readToken(request)).thenReturn(Optional.of("access"));
        when(jwt.parseToken("access")).thenReturn(Optional.of(claims()));
        filter.doFilter(request, response, (req, res) -> {});
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(cookies, never()).writeToken(any(), any());
    }

    @Test void expiredAccessRotatesAndKeepsTheSameSession() throws Exception {
        expiredAccess();
        var replacement = new RefreshTokenService.SessionTokens("42", "sid", "replacement");
        when(sessions.rotate("refresh")).thenReturn(Optional.of(replacement));
        when(jwt.generateToken(any(), eq("sid"))).thenReturn("new-access");
        filter.doFilter(request, response, (req, res) -> {});
        verify(cookies).writeToken(response, "new-access");
        verify(cookies).writeRefreshToken(response, "replacement");
        assertThat(SecurityContextHolder.getContext().getAuthentication().getName()).isEqualTo("42");
    }

    @Test void losingConcurrentRotationDoesNotAuthenticateOrOverwriteCookies() throws Exception {
        expiredAccess();
        when(sessions.rotate("refresh")).thenReturn(Optional.empty());
        filter.doFilter(request, response, (req, res) -> {});
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        verify(cookies, never()).writeToken(any(), any());
        verify(cookies, never()).writeRefreshToken(any(), any());
    }

    @Test void expiredAccessLogoutRevokesWithoutCreatingReplacementTokens() throws Exception {
        expiredAccess();
        request.setMethod("POST");
        request.setServletPath("/api/v1/command/auth/logout");
        var producer = mock(UserEventProducer.class);
        var logout = new LogoutService(cookies, sessions, producer);
        filter.doFilter(request, response, (req, res) -> logout.logout(request, response));
        verify(sessions).revokeSession("sid", "42");
        verify(sessions, never()).rotate(anyString());
        verify(cookies, never()).writeToken(any(), any());
        verify(cookies, never()).writeRefreshToken(any(), any());
        verify(cookies).clearAll(response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    @Test void expiredAccessLogoutAllDoesNotRenewTokens() throws Exception {
        expiredAccess();
        request.setMethod("POST");
        request.setServletPath("/api/v1/command/auth/logout-all");
        var logout = new LogoutService(cookies, sessions, mock(UserEventProducer.class));
        filter.doFilter(request, response, (req, res) -> logout.logoutAll(request, response));
        verify(sessions).revokeAll("42");
        verify(sessions, never()).rotate(anyString());
        verify(cookies, never()).writeRefreshToken(any(), any());
    }
}
