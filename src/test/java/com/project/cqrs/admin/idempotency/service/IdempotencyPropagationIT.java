package com.project.cqrs.admin.idempotency.service;
import com.project.cqrs.AbstractIntegrationTest;
import com.project.cqrs.admin.idempotency.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.*;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;
class IdempotencyPropagationIT extends AbstractIntegrationTest {
    @Autowired IdempotencyService service;
    @Autowired ProcessedEventRepository repository;
    @Autowired @Qualifier("queryTransactionManager") PlatformTransactionManager tx;
    @Test void processingAndCompletionRollBackTogether() {
        String id = UUID.randomUUID().toString();
        new TransactionTemplate(tx).executeWithoutResult(status -> { var claim = service.tryClaim(id, "test", 1); service.markCompleted(claim); status.setRollbackOnly(); });
        assertThat(repository.existsByEventIdAndTopic(id, "test")).isFalse();
        new TransactionTemplate(tx).executeWithoutResult(status -> service.markCompleted(service.tryClaim(id, "test", 2)));
        assertThat(repository.existsByEventIdAndTopic(id, "test")).isTrue();
        new TransactionTemplate(tx).executeWithoutResult(status -> assertThat(service.tryClaim(id, "test", 3)).isNull());
    }
    @Test void onlyOneConcurrentTransactionProcessesAnEvent() throws Exception {
        String id = UUID.randomUUID().toString(); var wins = new AtomicInteger(); var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(4)) {
            var tasks = new java.util.ArrayList<Future<?>>();
            for (int i = 0; i < 4; i++) tasks.add(executor.submit(() -> {
                try { start.await(); new TransactionTemplate(tx).executeWithoutResult(status -> { var claim = service.tryClaim(id, "race", 1); if (claim != null) { service.markCompleted(claim); wins.incrementAndGet(); } }); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); }
                catch (org.springframework.dao.DataAccessException e) { /* Concurrent insert is retried by Kafka in production. */ }
            }));
            start.countDown(); for (var task : tasks) task.get(20, TimeUnit.SECONDS);
        }
        assertThat(wins.get()).isEqualTo(1);
    }
}