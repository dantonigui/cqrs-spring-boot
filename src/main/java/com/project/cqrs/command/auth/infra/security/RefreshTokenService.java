package com.project.cqrs.command.auth.infra.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Sessions bind access tokens and rotating refresh tokens to the same revocable identity. */
@Service
public class RefreshTokenService {
    private static final String SESSION = "auth:v2:session:";
    private static final String REFRESH = "auth:v2:refresh:";
    private static final String USER = "auth:v2:user:";

    private static final DefaultRedisScript<String> START = new DefaultRedisScript<>("""
            redis.call('HSET', KEYS[1], 'userId', ARGV[1], 'refresh', ARGV[3])
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            redis.call('SET', KEYS[2], ARGV[2], 'PX', ARGV[4])
            redis.call('SADD', KEYS[3], ARGV[2])
            redis.call('PEXPIRE', KEYS[3], ARGV[4])
            return ARGV[1] .. '|' .. ARGV[2] .. '|' .. ARGV[3]
            """, String.class);

    private static final DefaultRedisScript<String> LOOKUP = new DefaultRedisScript<>("""
            local sid = redis.call('GET', KEYS[1])
            if not sid then return nil end
            local session = 'auth:v2:session:' .. sid
            local uid = redis.call('HGET', session, 'userId')
            if not uid or redis.call('HGET', session, 'refresh') ~= ARGV[1] then return nil end
            return uid .. '|' .. sid .. '|' .. ARGV[1]
            """, String.class);

    private static final DefaultRedisScript<String> ROTATE = new DefaultRedisScript<>("""
            local sid = redis.call('GET', KEYS[1])
            if not sid then return nil end
            local session = 'auth:v2:session:' .. sid
            local uid = redis.call('HGET', session, 'userId')
            if not uid or redis.call('HGET', session, 'refresh') ~= ARGV[1] then return nil end
            redis.call('DEL', KEYS[1])
            redis.call('SET', KEYS[2], sid, 'PX', ARGV[3])
            redis.call('HSET', session, 'refresh', ARGV[2])
            redis.call('PEXPIRE', session, ARGV[3])
            redis.call('PEXPIRE', 'auth:v2:user:' .. uid, ARGV[3])
            return uid .. '|' .. sid .. '|' .. ARGV[2]
            """, String.class);

    private static final DefaultRedisScript<Long> ACTIVE = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'userId') == ARGV[1] then return 1 end
            return 0
            """, Long.class);

    private static final DefaultRedisScript<Long> REVOKE = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], 'userId') ~= ARGV[1] then return 0 end
            local token = redis.call('HGET', KEYS[1], 'refresh')
            if token then redis.call('DEL', 'auth:v2:refresh:' .. token) end
            redis.call('DEL', KEYS[1])
            redis.call('SREM', KEYS[2], ARGV[2])
            return 1
            """, Long.class);

    private static final DefaultRedisScript<Long> REVOKE_ALL = new DefaultRedisScript<>("""
            local sessions = redis.call('SMEMBERS', KEYS[1])
            for _, sid in ipairs(sessions) do
                local session = 'auth:v2:session:' .. sid
                local token = redis.call('HGET', session, 'refresh')
                if token then redis.call('DEL', 'auth:v2:refresh:' .. token) end
                redis.call('DEL', session)
            end
            redis.call('DEL', KEYS[1])
            return #sessions
            """, Long.class);

    private final StringRedisTemplate redis;
    private final long expirationMs;

    public RefreshTokenService(StringRedisTemplate redis,
            @Value("${app.refresh-token.expiration-ms:604800000}") long expirationMs) {
        if (expirationMs <= 0) throw new IllegalArgumentException("Refresh TTL must be positive");
        this.redis = redis;
        this.expirationMs = expirationMs;
    }

    public record SessionTokens(String userId, String sessionId, String refreshToken) {}

    public SessionTokens start(String userId) {
        String sid = UUID.randomUUID().toString();
        String token = UUID.randomUUID().toString();
        return decode(redis.execute(START, List.of(SESSION + sid, REFRESH + token, USER + userId),
                userId, sid, token, Long.toString(expirationMs))).orElseThrow();
    }

    public Optional<SessionTokens> lookup(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        return decode(redis.execute(LOOKUP, List.of(REFRESH + token), token));
    }

    /** Validation, consumption and replacement happen in one Redis operation. */
    public Optional<SessionTokens> rotate(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        String replacement = UUID.randomUUID().toString();
        return decode(redis.execute(ROTATE, List.of(REFRESH + token, REFRESH + replacement),
                token, replacement, Long.toString(expirationMs)));
    }

    public boolean isSessionActive(String sid, String userId) {
        if (sid == null || sid.isBlank() || userId == null) return false;
        return Long.valueOf(1).equals(redis.execute(ACTIVE, List.of(SESSION + sid), userId));
    }

    public void revokeSession(String sid, String userId) {
        if (sid == null || userId == null) return;
        redis.execute(REVOKE, List.of(SESSION + sid, USER + userId), userId, sid);
    }

    public void revokeAll(String userId) {
        if (userId != null) redis.execute(REVOKE_ALL, List.of(USER + userId));
    }

    private Optional<SessionTokens> decode(String value) {
        if (value == null) return Optional.empty();
        String[] parts = value.split("\\|", -1);
        if (parts.length != 3) throw new IllegalStateException("Invalid Redis session response");
        return Optional.of(new SessionTokens(parts[0], parts[1], parts[2]));
    }
}
