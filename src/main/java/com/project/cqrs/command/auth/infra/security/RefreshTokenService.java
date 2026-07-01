package com.project.cqrs.command.auth.infra.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private static final String KEY_PREFIX = "refresh_token:";

    private final RedisTemplate<String, Object> redisTemplate;

    private final long expirationMs;

    public RefreshTokenService(RedisTemplate<String, Object> redisTemplate,@Value("${app.refresh-token.expiration-ms:604800000}") long expirationMs) {
        this.redisTemplate = redisTemplate;
        this.expirationMs = expirationMs;
    }

    public String generate(String userId) {
        String token = UUID.randomUUID().toString();
        String key = KEY_PREFIX + token;

        redisTemplate.opsForValue().set(key,userId, Duration.ofMillis(expirationMs));

        log.info("Refresh token generated for user {}, TTL={}ms", userId, expirationMs);

        return token;
    }

    public Optional<String> validate(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }

        String key = KEY_PREFIX + token;
        Object value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            log.warn("Refresh token not found or expirated in Redis");
            return Optional.empty();
        }

        return Optional.of(value.toString());
    }

    public void revoke(String token) {
        if (token == null || token.isBlank()) return;

        Boolean deleted = redisTemplate.delete(KEY_PREFIX + token);
        if(Boolean.TRUE.equals(deleted)) {
            log.info("Refresh token revogado com sucesso");
        } else {
            log.warn("Tentativa de revogar refresh token inexistente ou já expirado");
        }
    }

    public String rotate(String oldToken, String userId) {
        revoke(oldToken);
        String newToken = generate(userId);
        log.info("Refresh token rotacionado para userId={ }", userId);
        return newToken;
    }

    public void revokeAll(String userId) {
        var keys = redisTemplate.keys(KEY_PREFIX + "*");
        if(keys == null || keys.isEmpty()) return;

        long revoked = keys.stream().filter(key -> {
            Object value = redisTemplate.opsForValue().get(key);
            return userId.equals(value != null ? value.toString() : null);
        })
                .mapToLong(key -> Boolean.TRUE.equals(
                        redisTemplate.delete(key)
                ) ? 1L : 0L).sum();

        log.info("Todos os refresh tokens para userId={}", userId, revoked);
    }
}
