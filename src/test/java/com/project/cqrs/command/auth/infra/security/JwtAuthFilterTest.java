package com.project.cqrs.command.auth.infra.security;

import com.project.cqrs.command.auth.infra.cookie.CookieTokenUtil;
import com.project.cqrs.command.auth.model.UserCommandEntity;
import com.project.cqrs.command.auth.model.UserRole;
import com.project.cqrs.command.auth.repository.UserCommandRepository;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
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
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Testes do JwtAuthFilter — o coração da renovação silenciosa de sessão.
 *
 * Cobre os três caminhos possíveis por requisição:
 *   1. JWT válido → autentica direto, nunca toca o refresh token
 *   2. JWT expirado/ausente + Refresh Token válido → renova silenciosamente
 *      (rotaciona o refresh, gera novo JWT, autentica a requisição atual)
 *   3. Nenhum dos dois válido → não autentica, segue a cadeia sem auth
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthFilter")
class JwtAuthFilterTest {

    @Mock private JwtTokenService       jwtTokenService;
    @Mock private CookieTokenUtil       cookieTokenUtil;
    @Mock private RefreshTokenService   refreshTokenService;
    @Mock private UserCommandRepository userCommandRepository;
    @Mock private HttpServletRequest    request;
    @Mock private HttpServletResponse   response;
    @Mock private FilterChain           filterChain;
    @Mock private Claims                validClaims;

    private JwtAuthFilter filter;

    private static final String ACCESS_TOKEN  = "jwt-de-acesso";
    private static final String REFRESH_TOKEN = "refresh-token-valido";
    private static final String NEW_REFRESH   = "refresh-token-novo";
    private static final String NEW_JWT       = "jwt-novo-gerado";
    private static final Long   USER_ID       = 42L;

    @BeforeEach
    void setUp() {
        filter = new JwtAuthFilter(
                jwtTokenService, cookieTokenUtil,
                refreshTokenService, userCommandRepository);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private UserCommandEntity mockUser() {
        UserCommandEntity user = mock(UserCommandEntity.class);
        when(user.getUserId()).thenReturn(USER_ID);
        when(user.getUserRole()).thenReturn(UserRole.USER);
        return user;
    }

    // ── Caminho 1: JWT válido ─────────────────────────────────────────────────

    @Nested
    @DisplayName("Quando o JWT é válido")
    class ValidJwt {

        @Test
        @DisplayName("deve autenticar a requisição sem tocar no refresh token")
        void shouldAuthenticateWithoutTouchingRefreshToken() throws Exception {
            when(cookieTokenUtil.readToken(request))
                    .thenReturn(Optional.of(ACCESS_TOKEN));
            when(jwtTokenService.parseToken(ACCESS_TOKEN))
                    .thenReturn(Optional.of(validClaims));
            when(validClaims.getSubject()).thenReturn(USER_ID.toString());
            when(validClaims.get("role")).thenReturn("USER");

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication())
                    .isNotNull();
            assertThat(SecurityContextHolder.getContext()
                    .getAuthentication().getName())
                    .isEqualTo(USER_ID.toString());

            // Não deve ter tentado ler ou renovar o refresh token
            verify(cookieTokenUtil, never()).readRefreshToken(any());
            verify(refreshTokenService, never()).rotate(anyString(), anyString());
        }

        @Test
        @DisplayName("deve continuar a cadeia de filtros")
        void shouldContinueFilterChain() throws Exception {
            when(cookieTokenUtil.readToken(request))
                    .thenReturn(Optional.of(ACCESS_TOKEN));
            when(jwtTokenService.parseToken(ACCESS_TOKEN))
                    .thenReturn(Optional.of(validClaims));
            when(validClaims.getSubject()).thenReturn(USER_ID.toString());
            when(validClaims.get("role")).thenReturn("USER");

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    // ── Caminho 2: JWT expirado, Refresh Token válido ────────────────────────

    @Nested
    @DisplayName("Quando o JWT está expirado mas o Refresh Token é válido")
    class ExpiredJwtValidRefresh {

        @Test
        @DisplayName("deve renovar a sessão silenciosamente e autenticar")
        void shouldSilentlyRenewAndAuthenticate() throws Exception {
            UserCommandEntity user = mockUser();

            // JWT ausente/inválido
            when(cookieTokenUtil.readToken(request)).thenReturn(Optional.empty());

            // Refresh token válido
            when(cookieTokenUtil.readRefreshToken(request))
                    .thenReturn(Optional.of(REFRESH_TOKEN));
            when(refreshTokenService.validate(REFRESH_TOKEN))
                    .thenReturn(Optional.of(USER_ID.toString()));
            when(userCommandRepository.findById(USER_ID))
                    .thenReturn(Optional.of(user));
            when(refreshTokenService.rotate(REFRESH_TOKEN, USER_ID.toString()))
                    .thenReturn(NEW_REFRESH);
            when(jwtTokenService.generateToken(any())).thenReturn(NEW_JWT);

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication())
                    .isNotNull();
            assertThat(SecurityContextHolder.getContext()
                    .getAuthentication().getName())
                    .isEqualTo(USER_ID.toString());
        }

        @Test
        @DisplayName("deve rotacionar o refresh token (invalida antigo, gera novo)")
        void shouldRotateRefreshToken() throws Exception {
            UserCommandEntity user = mockUser();

            when(cookieTokenUtil.readToken(request)).thenReturn(Optional.empty());
            when(cookieTokenUtil.readRefreshToken(request))
                    .thenReturn(Optional.of(REFRESH_TOKEN));
            when(refreshTokenService.validate(REFRESH_TOKEN))
                    .thenReturn(Optional.of(USER_ID.toString()));
            when(userCommandRepository.findById(USER_ID))
                    .thenReturn(Optional.of(user));
            when(refreshTokenService.rotate(REFRESH_TOKEN, USER_ID.toString()))
                    .thenReturn(NEW_REFRESH);
            when(jwtTokenService.generateToken(any())).thenReturn(NEW_JWT);

            filter.doFilterInternal(request, response, filterChain);

            verify(refreshTokenService).rotate(REFRESH_TOKEN, USER_ID.toString());
        }

        @Test
        @DisplayName("deve emitir novos cookies (access e refresh) na resposta")
        void shouldWriteNewCookiesOnResponse() throws Exception {
            UserCommandEntity user = mockUser();

            when(cookieTokenUtil.readToken(request)).thenReturn(Optional.empty());
            when(cookieTokenUtil.readRefreshToken(request))
                    .thenReturn(Optional.of(REFRESH_TOKEN));
            when(refreshTokenService.validate(REFRESH_TOKEN))
                    .thenReturn(Optional.of(USER_ID.toString()));
            when(userCommandRepository.findById(USER_ID))
                    .thenReturn(Optional.of(user));
            when(refreshTokenService.rotate(REFRESH_TOKEN, USER_ID.toString()))
                    .thenReturn(NEW_REFRESH);
            when(jwtTokenService.generateToken(any())).thenReturn(NEW_JWT);

            filter.doFilterInternal(request, response, filterChain);

            verify(cookieTokenUtil).writeToken(response, NEW_JWT);
            verify(cookieTokenUtil).writeRefreshToken(response, NEW_REFRESH);
        }

        @Test
        @DisplayName("deve continuar a cadeia de filtros mesmo após renovar")
        void shouldContinueFilterChainAfterRenewal() throws Exception {
            UserCommandEntity user = mockUser();

            when(cookieTokenUtil.readToken(request)).thenReturn(Optional.empty());
            when(cookieTokenUtil.readRefreshToken(request))
                    .thenReturn(Optional.of(REFRESH_TOKEN));
            when(refreshTokenService.validate(REFRESH_TOKEN))
                    .thenReturn(Optional.of(USER_ID.toString()));
            when(userCommandRepository.findById(USER_ID))
                    .thenReturn(Optional.of(user));
            when(refreshTokenService.rotate(REFRESH_TOKEN, USER_ID.toString()))
                    .thenReturn(NEW_REFRESH);
            when(jwtTokenService.generateToken(any())).thenReturn(NEW_JWT);

            filter.doFilterInternal(request, response, filterChain);

            verify(filterChain).doFilter(request, response);
        }
    }

    // ── Caminho 3: nem JWT nem Refresh Token válidos ─────────────────────────

    @Nested
    @DisplayName("Quando nem JWT nem Refresh Token são válidos")
    class NeitherValid {

        @Test
        @DisplayName("não deve autenticar quando ambos os cookies estão ausentes")
        void shouldNotAuthenticateWhenBothCookiesAbsent() throws Exception {
            when(cookieTokenUtil.readToken(request)).thenReturn(Optional.empty());
            when(cookieTokenUtil.readRefreshToken(request))
                    .thenReturn(Optional.empty());

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication())
                    .isNull();
        }

        @Test
        @DisplayName("não deve autenticar quando o refresh token é inválido no Redis")
        void shouldNotAuthenticateWhenRefreshTokenInvalid() throws Exception {
            when(cookieTokenUtil.readToken(request)).thenReturn(Optional.empty());
            when(cookieTokenUtil.readRefreshToken(request))
                    .thenReturn(Optional.of("token-que-nao-existe-no-redis"));
            when(refreshTokenService.validate("token-que-nao-existe-no-redis"))
                    .thenReturn(Optional.empty());

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication())
                    .isNull();
            verify(refreshTokenService, never()).rotate(anyString(), anyString());
        }

        @Test
        @DisplayName("não deve autenticar quando o usuário do refresh token não existe mais")
        void shouldNotAuthenticateWhenUserNoLongerExists() throws Exception {
            when(cookieTokenUtil.readToken(request)).thenReturn(Optional.empty());
            when(cookieTokenUtil.readRefreshToken(request))
                    .thenReturn(Optional.of(REFRESH_TOKEN));
            when(refreshTokenService.validate(REFRESH_TOKEN))
                    .thenReturn(Optional.of(USER_ID.toString()));
            // Usuário foi deletado do banco, mas o token ainda existia no Redis
            when(userCommandRepository.findById(USER_ID))
                    .thenReturn(Optional.empty());

            filter.doFilterInternal(request, response, filterChain);

            assertThat(SecurityContextHolder.getContext().getAuthentication())
                    .isNull();
            // Não deve rotacionar nem emitir cookies para usuário inexistente
            verify(refreshTokenService, never()).rotate(anyString(), anyString());
            verify(cookieTokenUtil, never()).writeToken(any(), anyString());
        }

        @Test
        @DisplayName("sempre deve continuar a cadeia de filtros, mesmo sem autenticar")
        void shouldAlwaysContinueFilterChainEvenWithoutAuth() throws Exception {
            when(cookieTokenUtil.readToken(request)).thenReturn(Optional.empty());
            when(cookieTokenUtil.readRefreshToken(request))
                    .thenReturn(Optional.empty());

            filter.doFilterInternal(request, response, filterChain);

            // O filtro nunca deve bloquear a requisição — quem decide
            // se autenticação é obrigatória é o SecurityConfig depois
            verify(filterChain).doFilter(request, response);
        }
    }
}