package com.project.cqrs.admin.idempotency.service;

import com.project.cqrs.admin.idempotency.entity.ProcessedEventEntity;
import com.project.cqrs.admin.idempotency.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes do IdempotencyService — a base que garante que nenhum
 * consumer Kafka processe o mesmo evento duas vezes.
 *
 * Três cenários críticos:
 *   1. Evento novo → processa e registra
 *   2. Evento já processado → descarta sem tocar no banco de novo
 *   3. Race condition (dois threads simultâneos) → apenas um vence,
 *      o outro recebe DataIntegrityViolationException do constraint
 *      UNIQUE(event_id, topic) e é tratado graciosamente
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("IdempotencyService")
class IdempotencyServiceTest {

    @Mock
    private ProcessedEventRepository repository;

    private IdempotencyService idempotencyService;

    private static final String EVENT_ID = "evt-123e4567-e89b-12d3";
    private static final String TOPIC    = "payment.approved";

    @BeforeEach
    void setUp() {
        idempotencyService = new IdempotencyService(repository);
    }

    // ── Evento novo ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("isNew() — evento novo")
    class NewEvent {

        @Test
        @DisplayName("deve retornar true quando o evento nunca foi processado")
        void shouldReturnTrueForNewEvent() {
            when(repository.existsByEventIdAndTopic(EVENT_ID, TOPIC))
                    .thenReturn(false);

            boolean result = idempotencyService.isNew(EVENT_ID, TOPIC);

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("deve registrar o evento no repositório")
        void shouldPersistTheEvent() {
            when(repository.existsByEventIdAndTopic(EVENT_ID, TOPIC))
                    .thenReturn(false);

            idempotencyService.isNew(EVENT_ID, TOPIC);

            ArgumentCaptor<ProcessedEventEntity> captor =
                    ArgumentCaptor.forClass(ProcessedEventEntity.class);
            verify(repository).saveAndFlush(captor.capture());

            assertThat(captor.getValue().getEventId()).isEqualTo(EVENT_ID);
            assertThat(captor.getValue().getTopic()).isEqualTo(TOPIC);
        }

        @Test
        @DisplayName("deve verificar existência ANTES de tentar salvar")
        void shouldCheckExistenceBeforeSaving() {
            when(repository.existsByEventIdAndTopic(EVENT_ID, TOPIC))
                    .thenReturn(false);

            idempotencyService.isNew(EVENT_ID, TOPIC);

            var inOrder = inOrder(repository);
            inOrder.verify(repository).existsByEventIdAndTopic(EVENT_ID, TOPIC);
            inOrder.verify(repository).saveAndFlush(any());
        }
    }

    // ── Evento duplicado (caminho feliz) ──────────────────────────────────────

    @Nested
    @DisplayName("isNew() — evento já processado")
    class DuplicateEvent {

        @Test
        @DisplayName("deve retornar false quando o evento já existe")
        void shouldReturnFalseForDuplicateEvent() {
            when(repository.existsByEventIdAndTopic(EVENT_ID, TOPIC))
                    .thenReturn(true);

            boolean result = idempotencyService.isNew(EVENT_ID, TOPIC);

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("não deve tentar salvar quando o evento já existe")
        void shouldNotSaveWhenAlreadyExists() {
            when(repository.existsByEventIdAndTopic(EVENT_ID, TOPIC))
                    .thenReturn(true);

            idempotencyService.isNew(EVENT_ID, TOPIC);

            verify(repository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("mesmo eventId em topics diferentes não conflita")
        void sameEventIdDifferentTopicsShouldNotConflict() {
            when(repository.existsByEventIdAndTopic(EVENT_ID, "product.created"))
                    .thenReturn(false);
            when(repository.existsByEventIdAndTopic(EVENT_ID, "order.created"))
                    .thenReturn(false);

            boolean resultTopic1 = idempotencyService.isNew(EVENT_ID, "product.created");
            boolean resultTopic2 = idempotencyService.isNew(EVENT_ID, "order.created");

            assertThat(resultTopic1).isTrue();
            assertThat(resultTopic2).isTrue();
            verify(repository, times(2)).saveAndFlush(any());
        }
    }

    // ── Race condition — o cenário mais importante ────────────────────────────

    @Nested
    @DisplayName("isNew() — race condition entre consumers simultâneos")
    class RaceCondition {

        @Test
        @DisplayName("""
            deve retornar false quando o saveAndFlush lança
            DataIntegrityViolationException (outro thread venceu a corrida)
            """)
        void shouldReturnFalseOnRaceConditionViolation() {
            // Simula: ambos os threads passam pelo existsBy (retorna false
            // para os dois, pois checaram antes de qualquer um commitar),
            // mas o segundo saveAndFlush esbarra no constraint UNIQUE
            // (event_id, topic) que o primeiro thread já commitou.
            when(repository.existsByEventIdAndTopic(EVENT_ID, TOPIC))
                    .thenReturn(false);
            when(repository.saveAndFlush(any(ProcessedEventEntity.class)))
                    .thenThrow(new DataIntegrityViolationException(
                            "Duplicate entry for key uq_event_topic"));

            boolean result = idempotencyService.isNew(EVENT_ID, TOPIC);

            // O "perdedor" da corrida deve tratar graciosamente — false,
            // não uma exceção estourando para o consumer Kafka
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("não deve propagar a exceção do banco para o caller")
        void shouldNotPropagateExceptionToCaller() {
            when(repository.existsByEventIdAndTopic(EVENT_ID, TOPIC))
                    .thenReturn(false);
            when(repository.saveAndFlush(any(ProcessedEventEntity.class)))
                    .thenThrow(new DataIntegrityViolationException("Duplicate entry"));

            // Não deve lançar exceção — deve retornar false silenciosamente.
            // Se propagasse, o @KafkaListener cairia no fluxo de retry/DLQ
            // por um evento que na verdade já foi processado com sucesso
            // por outro consumer — desperdício e falso alarme.
            assertThat(idempotencyService.isNew(EVENT_ID, TOPIC))
                    .isFalse();
        }

        @Test
        @DisplayName("simula dois consumers processando o mesmo evento em sequência")
        void shouldSimulateTwoConsumersProcessingSameEvent() {
            // Primeira chamada (consumer A): evento novo, processa
            when(repository.existsByEventIdAndTopic(EVENT_ID, TOPIC))
                    .thenReturn(false)   // consumer A verifica: não existe
                    .thenReturn(true);   // consumer B verifica: já existe

            boolean resultA = idempotencyService.isNew(EVENT_ID, TOPIC);
            boolean resultB = idempotencyService.isNew(EVENT_ID, TOPIC);

            assertThat(resultA).isTrue();   // A processa
            assertThat(resultB).isFalse();  // B descarta

            // saveAndFlush deve ter sido chamado apenas uma vez (por A)
            verify(repository, times(1)).saveAndFlush(any());
        }
    }

    // ── Transactional propagation (documentação de comportamento) ────────────

    @Nested
    @DisplayName("Comportamento transacional")
    class TransactionalBehavior {

        @Test
        @DisplayName("""
            o registro de idempotência deve persistir mesmo se o
            processamento do evento falhar depois (REQUIRES_NEW)
            """)
        void shouldPersistEvenIfCallerRollsBack() {
            // Este teste documenta a EXPECTATIVA de comportamento do
            // @Transactional(propagation = Propagation.REQUIRES_NEW).
            //
            // Não é possível testar propagation real com Mockito puro
            // (isso requer um teste de integração com Spring context
            // real + banco real, verificando que o registro em
            // processed_events sobrevive a um rollback do caller).
            //
            // Ver IdempotencyPropagationIT para o teste de integração
            // equivalente com Testcontainers.

            when(repository.existsByEventIdAndTopic(EVENT_ID, TOPIC))
                    .thenReturn(false);

            boolean result = idempotencyService.isNew(EVENT_ID, TOPIC);

            assertThat(result).isTrue();
            verify(repository).saveAndFlush(any());
        }
    }
}