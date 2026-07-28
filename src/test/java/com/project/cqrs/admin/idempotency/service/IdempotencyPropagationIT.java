package com.project.cqrs.admin.idempotency.service;

import com.project.cqrs.AbstractIntegrationTest;
import com.project.cqrs.admin.idempotency.repository.ProcessedEventRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;


import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Prova, com banco MySQL real (Testcontainers), os dois comportamentos
 * que não são verificáveis com Mockito puro:
 *
 *   1. REQUIRES_NEW: o registro em processed_events sobrevive mesmo
 *      se a transação do caller (que chamou isNew()) for revertida.
 *
 *   2. Race condition real: N threads chamando isNew() com o MESMO
 *      eventId simultaneamente — o constraint UNIQUE(event_id, topic)
 *      do banco garante que só uma chamada retorna true.
 */
@DisplayName("IdempotencyService — comportamento transacional real")
class IdempotencyPropagationIT extends AbstractIntegrationTest {

    @Autowired private IdempotencyService         idempotencyService;
    @Autowired private ProcessedEventRepository   processedEventRepository;
    @Autowired private PlatformTransactionManager transactionManager;

    private static final String TOPIC = "test.propagation";

    @Test
    @DisplayName("""
        registro de idempotência deve persistir mesmo quando a
        transação externa (do caller) sofre rollback
        """)
    void shouldPersistIdempotencyRecordEvenWhenCallerTransactionRollsBack() {
        String eventId = "evt-rollback-test-" + System.nanoTime();

        TransactionTemplate txTemplate = new TransactionTemplate(transactionManager);
        txTemplate.setPropagationBehavior(Propagation.REQUIRED.value());

        // Simula um consumer Kafka: chama isNew() dentro de uma transação
        // externa e depois força um rollback (como se o processamento
        // do evento tivesse falhado DEPOIS de registrar a idempotência)
        try {
            txTemplate.execute(status -> {
                boolean isNew = idempotencyService.isNew(eventId, TOPIC);
                assertThat(isNew).isTrue();

                // Simula falha no processamento do evento APÓS o registro
                // de idempotência — força rollback da transação externa
                status.setRollbackOnly();
                return null;
            });
        } catch (Exception ignored) {
            // esperado — rollback intencional
        }

        // Mesmo com o rollback da transação externa, o registro em
        // processed_events deve ter sobrevivido — porque isNew() usa
        // REQUIRES_NEW, que abre sua PRÓPRIA transação independente
        boolean existsAfterRollback = processedEventRepository
                .existsByEventIdAndTopic(eventId, TOPIC);

        assertThat(existsAfterRollback)
                .as("Registro de idempotência deve sobreviver ao rollback " +
                        "do caller graças ao REQUIRES_NEW")
                .isTrue();
    }

    @Test
    @DisplayName("""
        entre 10 threads concorrentes chamando isNew() com o mesmo
        eventId, exatamente 1 deve retornar true
        """)
    void onlyOneThreadShouldWinTheRaceCondition() throws InterruptedException {
        String eventId = "evt-race-test-" + System.nanoTime();
        int threadCount = 10;

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    // Todas as threads aguardam o mesmo sinal de partida
                    // para maximizar a chance de colisão real no banco
                    startLatch.await();

                    boolean isNew = idempotencyService.isNew(eventId, TOPIC);
                    if (isNew) {
                        successCount.incrementAndGet();
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        startLatch.countDown();          // libera todas as threads de uma vez
        doneLatch.await(10, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(successCount.get())
                .as("Exatamente uma thread deve vencer a corrida — " +
                        "o constraint UNIQUE(event_id, topic) do banco garante isso")
                .isEqualTo(1);

        // Confirma que só existe UM registro no banco, não 10
        long totalRecords = processedEventRepository
                .findAll().stream()
                .filter(e -> e.getEventId().equals(eventId))
                .count();

        assertThat(totalRecords).isEqualTo(1);
    }

    @Test
    @DisplayName("evento processado deve ser rejeitado em chamada subsequente real")
    void shouldRejectDuplicateOnRealSubsequentCall() {
        String eventId = "evt-duplicate-test-" + System.nanoTime();

        boolean firstCall  = idempotencyService.isNew(eventId, TOPIC);
        boolean secondCall = idempotencyService.isNew(eventId, TOPIC);

        assertThat(firstCall).isTrue();
        assertThat(secondCall).isFalse();
    }
}