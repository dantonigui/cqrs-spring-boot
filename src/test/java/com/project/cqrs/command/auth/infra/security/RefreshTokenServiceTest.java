package com.project.cqrs.command.auth.infra.security;

import org.junit.jupiter.api.*;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.ArrayList;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class RefreshTokenServiceTest {
    @Container static final GenericContainer<?> redis = new GenericContainer<>(
            DockerImageName.parse("redis:7.2-alpine")).withExposedPorts(6379);
    private LettuceConnectionFactory factory;
    private RefreshTokenService service;

    @BeforeEach void setup() {
        factory = new LettuceConnectionFactory(redis.getHost(), redis.getMappedPort(6379));
        factory.afterPropertiesSet();
        var template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();
        service = new RefreshTokenService(template, 60_000);
    }
    @AfterEach void cleanup() { factory.destroy(); }

    @Test void onlyOneConcurrentRotationCanConsumeTheSameToken() throws Exception {
        var session = service.start("42");
        try (var pool = Executors.newFixedThreadPool(8)) {
            var ready = new CountDownLatch(8);
            var go = new CountDownLatch(1);
            var attempts = new ArrayList<Future<java.util.Optional<RefreshTokenService.SessionTokens>>>();
            for (int i = 0; i < 8; i++) attempts.add(pool.submit(() -> {
                ready.countDown();
                if (!go.await(5, TimeUnit.SECONDS)) throw new IllegalStateException("Start timeout");
                return service.rotate(session.refreshToken());
            }));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
            go.countDown();
            var winners = new ArrayList<RefreshTokenService.SessionTokens>();
            for (var attempt : attempts) attempt.get(10, TimeUnit.SECONDS).ifPresent(winners::add);
            assertThat(winners).hasSize(1);
            assertThat(winners.getFirst().sessionId()).isEqualTo(session.sessionId());
            assertThat(service.lookup(session.refreshToken())).isEmpty();
            assertThat(service.lookup(winners.getFirst().refreshToken())).contains(winners.getFirst());
            assertThat(service.isSessionActive(session.sessionId(), "42")).isTrue();
        }
    }

    @Test void logoutRevokesAccessAndTheLatestRotatedRefreshOnlyForItsSession() {
        var first = service.start("42");
        var second = service.start("42");
        var rotated = service.rotate(first.refreshToken()).orElseThrow();
        service.revokeSession(first.sessionId(), "42");
        assertThat(service.isSessionActive(first.sessionId(), "42")).isFalse();
        assertThat(service.lookup(rotated.refreshToken())).isEmpty();
        assertThat(service.rotate(rotated.refreshToken())).isEmpty();
        assertThat(service.isSessionActive(second.sessionId(), "42")).isTrue();
    }

    @Test void logoutAllRevokesEverySessionAndPreservesOtherUsersAndFutureLogins() {
        var first = service.start("42");
        var second = service.start("42");
        var other = service.start("43");
        service.rotate(first.refreshToken());
        service.revokeAll("42");
        assertThat(service.isSessionActive(first.sessionId(), "42")).isFalse();
        assertThat(service.isSessionActive(second.sessionId(), "42")).isFalse();
        assertThat(service.rotate(second.refreshToken())).isEmpty();
        assertThat(service.isSessionActive(other.sessionId(), "43")).isTrue();
        var fresh = service.start("42");
        assertThat(service.isSessionActive(fresh.sessionId(), "42")).isTrue();
    }

    @Test void anotherUserCannotRevokeOrAuthenticateUsingTheSession() {
        var session = service.start("42");
        service.revokeSession(session.sessionId(), "43");
        assertThat(service.isSessionActive(session.sessionId(), "43")).isFalse();
        assertThat(service.isSessionActive(session.sessionId(), "42")).isTrue();
        assertThat(service.lookup("unknown")).isEmpty();
        assertThat(service.rotate("unknown")).isEmpty();
        assertThat(service.lookup(null)).isEmpty();
        assertThat(service.isSessionActive(null, "42")).isFalse();
    }
}
