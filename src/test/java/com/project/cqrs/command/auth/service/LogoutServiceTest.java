package com.project.cqrs.command.auth.service;

import com.project.cqrs.command.auth.infra.cookie.CookieTokenUtil;
import com.project.cqrs.command.auth.infra.kafka.UserEventProducer;
import com.project.cqrs.command.auth.infra.security.JwtAuthFilter;
import com.project.cqrs.command.auth.infra.security.RefreshTokenService;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogoutServiceTest {
    @Mock CookieTokenUtil cookies;
    @Mock RefreshTokenService sessions;
    @Mock UserEventProducer events;
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }
    @Test void logoutWithoutRefreshCookieStillRevokesAccessSession() {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.setAttribute(JwtAuthFilter.SESSION_ATTRIBUTE, "sid");
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", null, java.util.List.of()));
        new LogoutService(cookies, sessions, events).logout(request, response);
        verify(sessions).revokeSession("sid", "42");
        verify(cookies).clearAll(response);
        verify(events).publishUserLogoutEvent(any());
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
    @Test void logoutAllRevokesEveryDeviceAndClearsCookies() {
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("42", null, java.util.List.of()));
        new LogoutService(cookies, sessions, events).logoutAll(request, response);
        verify(sessions).revokeAll("42");
        verify(cookies).clearAll(response);
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
