package com.project.cqrs;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.KafkaContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Classe base para todos os testes de integração.
 *
 * Sobe MySQL, Kafka e Redis reais em containers Docker antes dos testes
 * e os compartilha entre todas as subclasses (static = um container
 * por suite, não por teste — muito mais rápido).
 *
 * Uso:
 *   class MeuTesteIT extends AbstractIntegrationTest { ... }
 *
 * Os containers são iniciados uma única vez e reutilizados em todos
 * os testes da suite. O Spring recebe as portas dinâmicas via
 * @DynamicPropertySource.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
public abstract class AbstractIntegrationTest {

    @Container
    static final MySQLContainer<?> mysql =
            new MySQLContainer<>(DockerImageName.parse("mysql:8.0"))
                    .withDatabaseName("cqrs_test")
                    .withUsername("test")
                    .withPassword("test");

    @Container
    static final KafkaContainer kafka =
            new KafkaContainer(DockerImageName.parse(
                    "confluentinc/cp-kafka:7.4.0"));

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> redis =
            new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine"))
                    .withExposedPorts(6379)
                    .withCommand("redis-server", "--requirepass", "test-redis-pass");

    /**
     * Injeta as propriedades dinâmicas (portas aleatórias dos containers)
     * no contexto Spring antes de iniciar a aplicação.
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        // MySQL
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);

        // Kafka
        registry.add("spring.kafka.bootstrap-servers",
                kafka::getBootstrapServers);

        // Redis
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port",
                () -> redis.getMappedPort(6379).toString());
        registry.add("spring.data.redis.password", () -> "test-redis-pass");

        // JWT e Refresh Token (valores fixos para teste)
        registry.add("app.jwt.secret",
                () -> "dGVzdC1qd3Qtc2VjcmV0LW1pbmltby0zMi1ieXRlcw==");
        registry.add("app.jwt.expiration-ms", () -> "900000");
        registry.add("app.refresh-token.expiration-ms", () -> "604800000");

        // Mercado Pago (sandbox — não faz chamadas reais nos testes)
        registry.add("mp.access-token", () -> "TEST-access-token");
        registry.add("mp.public-key",   () -> "TEST-public-key");
        registry.add("mp.webhook-secret", () -> "");  // desabilita validação HMAC

        // URL base
        registry.add("app.base-url", () -> "http://localhost");
        registry.add("app.frontend-url", () -> "http://localhost:3000");
        registry.add("app.cookie.name", () -> "access_token");
        registry.add("app.cookie.max-age", () -> "900");
        registry.add("app.refresh-token.cookie-name", () -> "refresh_token");
    }
}