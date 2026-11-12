package com.project.cqrs;
import com.project.cqrs.notification.service.EmailSenderService;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.*;
import org.testcontainers.containers.*;
import org.testcontainers.utility.DockerImageName;

@org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class AbstractIntegrationTest {
    static final MySQLContainer<?> command = new MySQLContainer<>(DockerImageName.parse("mysql:8.0")).withDatabaseName("cqrs_command_test").withUsername("test").withPassword("test");
    static final MySQLContainer<?> query = new MySQLContainer<>(DockerImageName.parse("mysql:8.0")).withDatabaseName("cqrs_query_test").withUsername("test").withPassword("test");
    static final KafkaContainer kafka = new KafkaContainer(DockerImageName.parse("confluentinc/cp-kafka:7.4.0"));
    static final GenericContainer<?> redis = new GenericContainer<>(DockerImageName.parse("redis:7.2-alpine")).withExposedPorts(6379).withCommand("redis-server", "--requirepass", "test-redis-pass");
    static {
        // Avoid simultaneous database initialization on small Docker Desktop VMs.
        command.withStartupTimeout(java.time.Duration.ofMinutes(5)).start();
        query.withStartupTimeout(java.time.Duration.ofMinutes(5)).start();
        kafka.withStartupTimeout(java.time.Duration.ofMinutes(5)).start();
        redis.start();
    }
    @MockBean protected EmailSenderService emailSender;
    @MockBean protected com.project.cqrs.command.payment.service.PaymentGateway paymentGateway;
    @org.springframework.beans.factory.annotation.Autowired
    @org.springframework.beans.factory.annotation.Qualifier("queryDataSource")
    private javax.sql.DataSource queryDataSource;

    @org.junit.jupiter.api.BeforeEach
    void seedCustomerProjection() {
        new org.springframework.jdbc.core.JdbcTemplate(queryDataSource).update(
                "INSERT IGNORE INTO user_query (user_id, user_name, user_email, user_picture, user_role) VALUES (42, 'Cliente teste', 'customer@example.com', 'https://example.com/avatar.png', 'USER')");
    }
    @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
        r.add("spring.datasource.command.url", command::getJdbcUrl); r.add("spring.datasource.command.username", command::getUsername); r.add("spring.datasource.command.password", command::getPassword);
        r.add("spring.datasource.query.url", query::getJdbcUrl); r.add("spring.datasource.query.username", query::getUsername); r.add("spring.datasource.query.password", query::getPassword);
        r.add("spring.kafka.bootstrap-servers", kafka::getBootstrapServers);
        r.add("spring.data.redis.host", redis::getHost); r.add("spring.data.redis.port", () -> redis.getMappedPort(6379)); r.add("spring.data.redis.password", () -> "test-redis-pass");
        r.add("app.jwt.secret", () -> "MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTIzNDU2Nzg5MDE="); r.add("app.jwt.expiration-ms", () -> "900000");
        r.add("app.cookie.name", () -> "access_token"); r.add("app.cookie.max-age", () -> "900"); r.add("app.cookie.secure", () -> "false");
        r.add("app.refresh-token.cookie-name", () -> "refresh_token"); r.add("mp.access-token", () -> "TEST-token"); r.add("mp.public-key", () -> "TEST-key"); r.add("mp.webhook-secret", () -> "test-webhook-secret");
        r.add("app.base-url", () -> "http://localhost:8080"); r.add("app.frontend-url", () -> "http://localhost:3000"); r.add("app.security.email-admin", () -> "admin@example.com");
        r.add("spring.security.oauth2.client.registration.google.client-id", () -> "test-google"); r.add("spring.security.oauth2.client.registration.google.client-secret", () -> "test-secret"); r.add("spring.security.oauth2.client.registration.google.redirect-uri", () -> "http://localhost:8080/login/oauth2/code/google");
        r.add("spring.mail.username", () -> "test"); r.add("spring.mail.password", () -> "test");
        r.add("app.orders.expiration-poll-ms", () -> "3600000");
    }
}
