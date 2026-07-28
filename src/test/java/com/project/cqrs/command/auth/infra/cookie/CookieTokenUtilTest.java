package com.project.cqrs.command.auth.infra.cookie;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Testes do CookieTokenUtil — leitura, escrita e limpeza dos
 * cookies de access token e refresh token.
 *
 * Usa MockHttpServletRequest/Response do Spring Test — não precisa
 * de servidor real, apenas simula os objetos servlet.
 */
@DisplayName("CookieTokenUtil")
class CookieTokenUtilTest {

    private CookieTokenUtil cookieTokenUtil;

    private static final String ACCESS_COOKIE_NAME  = "access_token";
    private static final int    ACCESS_MAX_AGE       = 900;
    private static final String REFRESH_COOKIE_NAME = "refresh_token";
    private static final long   REFRESH_EXPIRATION_MS = 604_800_000L; // 7 dias

    @BeforeEach
    void setUp() {
        cookieTokenUtil = new CookieTokenUtil();
        ReflectionTestUtils.setField(cookieTokenUtil,
                "ACCESS_COOKIE_NAME", ACCESS_COOKIE_NAME);
        ReflectionTestUtils.setField(cookieTokenUtil,
                "ACCESS_COOKIE_MAX_AGE", ACCESS_MAX_AGE);
        ReflectionTestUtils.setField(cookieTokenUtil,
                "REFRESH_COOKIE_NAME", REFRESH_COOKIE_NAME);
        ReflectionTestUtils.setField(cookieTokenUtil,
                "refreshExpirationMs", REFRESH_EXPIRATION_MS);
    }

    // ── Access Token ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Access Token")
    class AccessToken {

        @Test
        @DisplayName("writeToken deve gravar cookie HttpOnly com o valor correto")
        void writeTokenShouldSetHttpOnlyCookie() {
            MockHttpServletResponse response = new MockHttpServletResponse();

            cookieTokenUtil.writeToken(response, "meu-jwt-aqui");

            String setCookieHeader = response.getHeader("Set-Cookie");
            assertThat(setCookieHeader).contains(ACCESS_COOKIE_NAME + "=meu-jwt-aqui");
            assertThat(setCookieHeader).contains("HttpOnly");
            assertThat(setCookieHeader).contains("Secure");
            assertThat(setCookieHeader).contains("SameSite=Lax");
            assertThat(setCookieHeader).contains("Max-Age=" + ACCESS_MAX_AGE);
        }

        @Test
        @DisplayName("readToken deve retornar o valor quando cookie existe")
        void readTokenShouldReturnValueWhenCookieExists() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new Cookie(ACCESS_COOKIE_NAME, "token-existente"));

            Optional<String> result = cookieTokenUtil.readToken(request);

            assertThat(result).contains("token-existente");
        }

        @Test
        @DisplayName("readToken deve retornar Optional vazio quando não há cookies")
        void readTokenShouldReturnEmptyWhenNoCookies() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            // sem setCookies() — request.getCookies() retorna null

            assertThat(cookieTokenUtil.readToken(request)).isEmpty();
        }

        @Test
        @DisplayName("readToken deve retornar Optional vazio quando cookie não está presente")
        void readTokenShouldReturnEmptyWhenCookieNotPresent() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new Cookie("outro_cookie", "valor"));

            assertThat(cookieTokenUtil.readToken(request)).isEmpty();
        }

        @Test
        @DisplayName("clearToken deve gravar cookie com Max-Age=0")
        void clearTokenShouldSetMaxAgeZero() {
            MockHttpServletResponse response = new MockHttpServletResponse();

            cookieTokenUtil.clearToken(response);

            String setCookieHeader = response.getHeader("Set-Cookie");
            assertThat(setCookieHeader).contains(ACCESS_COOKIE_NAME + "=");
            assertThat(setCookieHeader).contains("Max-Age=0");
        }
    }

    // ── Refresh Token ─────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Refresh Token")
    class RefreshToken {

        @Test
        @DisplayName("writeRefreshToken deve gravar cookie restrito ao path /auth/refresh")
        void writeRefreshTokenShouldRestrictPath() {
            MockHttpServletResponse response = new MockHttpServletResponse();

            cookieTokenUtil.writeRefreshToken(response, "meu-refresh-token");

            String setCookieHeader = response.getHeader("Set-Cookie");
            assertThat(setCookieHeader)
                    .contains(REFRESH_COOKIE_NAME + "=meu-refresh-token");
            assertThat(setCookieHeader).contains("Path=/auth/refresh");
            assertThat(setCookieHeader).contains("SameSite=Strict");
            assertThat(setCookieHeader).contains("HttpOnly");
        }

        @Test
        @DisplayName("writeRefreshToken deve usar o TTL configurado em segundos")
        void writeRefreshTokenShouldUseConfiguredTtl() {
            MockHttpServletResponse response = new MockHttpServletResponse();

            cookieTokenUtil.writeRefreshToken(response, "token");

            long expectedMaxAgeSeconds = REFRESH_EXPIRATION_MS / 1000;
            assertThat(response.getHeader("Set-Cookie"))
                    .contains("Max-Age=" + expectedMaxAgeSeconds);
        }

        @Test
        @DisplayName("readRefreshToken deve retornar o valor quando cookie existe")
        void readRefreshTokenShouldReturnValue() {
            MockHttpServletRequest request = new MockHttpServletRequest();
            request.setCookies(new Cookie(REFRESH_COOKIE_NAME, "refresh-existente"));

            assertThat(cookieTokenUtil.readRefreshToken(request))
                    .contains("refresh-existente");
        }

        @Test
        @DisplayName("clearRefreshToken deve zerar o Max-Age")
        void clearRefreshTokenShouldZeroMaxAge() {
            MockHttpServletResponse response = new MockHttpServletResponse();

            cookieTokenUtil.clearRefreshToken(response);

            assertThat(response.getHeader("Set-Cookie")).contains("Max-Age=0");
        }
    }

    // ── clearAll ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("clearAll()")
    class ClearAll {

        @Test
        @DisplayName("deve limpar access token e refresh token em duas chamadas separadas")
        void shouldClearBothCookies() {
            MockHttpServletResponse response = new MockHttpServletResponse();

            cookieTokenUtil.clearAll(response);

            // Duas chamadas a addHeader("Set-Cookie", ...) geram duas entradas
            var cookieHeaders = response.getHeaders("Set-Cookie");
            assertThat(cookieHeaders).hasSize(2);
            assertThat(cookieHeaders.get(0)).contains(ACCESS_COOKIE_NAME);
            assertThat(cookieHeaders.get(1)).contains(REFRESH_COOKIE_NAME);
        }
    }
}