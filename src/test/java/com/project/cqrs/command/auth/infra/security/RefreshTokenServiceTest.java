package com.project.cqrs.command.auth.infra.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Duration;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RefreshTokenService")
class RefreshTokenServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOps;

    private RefreshTokenService refreshTokenService;

    private static final String USER_ID       = "42";
    private static final long   EXPIRATION_MS = 604800000L; // 7 dias

    @BeforeEach
    void setUp() {
        refreshTokenService = new RefreshTokenService(redisTemplate, EXPIRATION_MS);
    }

    // ── generate ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("generate()")
    class Generate {

        @Test
        @DisplayName("deve retornar um token UUID não nulo")
        void shouldReturnNonNullToken() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            String token = refreshTokenService.generate(USER_ID);
            assertThat(token).isNotNull().isNotBlank();
        }

        @Test
        @DisplayName("deve armazenar o token no Redis com o userId como valor")
        void shouldStoreTokenInRedisWithUserId() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            String token = refreshTokenService.generate(USER_ID);

            ArgumentCaptor<String> keyCaptor   = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Object> valueCaptor = ArgumentCaptor.forClass(Object.class);
            ArgumentCaptor<Duration> ttlCaptor = ArgumentCaptor.forClass(Duration.class);

            verify(valueOps).set(keyCaptor.capture(), valueCaptor.capture(),
                    ttlCaptor.capture());

            assertThat(keyCaptor.getValue()).isEqualTo("refresh_token:" + token);
            assertThat(valueCaptor.getValue()).isEqualTo(USER_ID);
            assertThat(ttlCaptor.getValue()).isEqualTo(Duration.ofMillis(EXPIRATION_MS));
        }

        @Test
        @DisplayName("deve gerar tokens diferentes a cada chamada")
        void shouldGenerateUniqueTokens() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            String token1 = refreshTokenService.generate(USER_ID);
            String token2 = refreshTokenService.generate(USER_ID);
            assertThat(token1).isNotEqualTo(token2);
        }
    }

    // ── validate ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("validate()")
    class Validate {

        @Test
        @DisplayName("deve retornar userId quando token existe no Redis")
        void shouldReturnUserIdWhenTokenExists() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            String token = "valid-token-uuid";
            when(valueOps.get("refresh_token:" + token)).thenReturn(USER_ID);

            Optional<String> result = refreshTokenService.validate(token);

            assertThat(result).isPresent().contains(USER_ID);
        }

        @Test
        @DisplayName("deve retornar Optional vazio quando token não existe")
        void shouldReturnEmptyWhenTokenNotFound() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            when(valueOps.get(anyString())).thenReturn(null);

            Optional<String> result = refreshTokenService.validate("token-inexistente");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("deve retornar Optional vazio quando token é null")
        void shouldReturnEmptyWhenTokenIsNull() {

            Optional<String> result = refreshTokenService.validate(null);

            assertThat(result).isEmpty();
            verifyNoInteractions(valueOps);
        }

        @Test
        @DisplayName("deve retornar Optional vazio quando token é blank")
        void shouldReturnEmptyWhenTokenIsBlank() {
            Optional<String> result = refreshTokenService.validate("   ");
            assertThat(result).isEmpty();
        }
    }

    // ── revoke ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("revoke()")
    class Revoke {

        @Test
        @DisplayName("deve deletar a chave do Redis")
        void shouldDeleteKeyFromRedis() {
            String token = "token-to-revoke";
            when(redisTemplate.delete("refresh_token:" + token)).thenReturn(true);

            refreshTokenService.revoke(token);

            verify(redisTemplate).delete("refresh_token:" + token);
        }

        @Test
        @DisplayName("não deve chamar Redis quando token é null")
        void shouldNotCallRedisWhenTokenIsNull() {
            refreshTokenService.revoke(null);
            verify(redisTemplate, never()).delete(anyString());
        }
    }

    // ── rotate ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("rotate()")
    class Rotate {

        @Test
        @DisplayName("deve revogar o token antigo e gerar um novo")
        void shouldRevokeOldAndGenerateNew() {
            String oldToken = "old-token";
            when(redisTemplate.delete("refresh_token:" + oldToken)).thenReturn(true);
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            String newToken = refreshTokenService.rotate(oldToken, USER_ID);

            // Revogou o antigo
            verify(redisTemplate).delete("refresh_token:" + oldToken);
            // Gerou o novo
            assertThat(newToken).isNotNull().isNotEqualTo(oldToken);
            // Armazenou o novo no Redis
            verify(valueOps).set(eq("refresh_token:" + newToken),
                    eq(USER_ID), any(Duration.class));
        }
    }

    // ── revokeAll ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("revokeAll()")
    class RevokeAll {

        @Test
        @DisplayName("deve deletar apenas as chaves que pertencem ao userId")
        void shouldDeleteOnlyKeysForUserId() {
            when(redisTemplate.opsForValue()).thenReturn(valueOps);
            String tokenA = "token-a";
            String tokenB = "token-b";
            String tokenC = "token-c-outro-usuario";

            when(redisTemplate.keys("refresh_token:*")).thenReturn(
                    Set.of("refresh_token:" + tokenA,
                            "refresh_token:" + tokenB,
                            "refresh_token:" + tokenC));

            when(valueOps.get("refresh_token:" + tokenA)).thenReturn(USER_ID);
            when(valueOps.get("refresh_token:" + tokenB)).thenReturn(USER_ID);
            when(valueOps.get("refresh_token:" + tokenC)).thenReturn("outro-user");

            when(redisTemplate.delete("refresh_token:" + tokenA)).thenReturn(true);
            when(redisTemplate.delete("refresh_token:" + tokenB)).thenReturn(true);

            refreshTokenService.revokeAll(USER_ID);

            verify(redisTemplate).delete("refresh_token:" + tokenA);
            verify(redisTemplate).delete("refresh_token:" + tokenB);
            verify(redisTemplate, never()).delete("refresh_token:" + tokenC);
        }

        @Test
        @DisplayName("não deve lançar exceção quando não há tokens")
        void shouldNotThrowWhenNoTokensExist() {
            when(redisTemplate.keys("refresh_token:*")).thenReturn(Set.of());
            // Não lança exceção
            refreshTokenService.revokeAll(USER_ID);
        }
    }
}