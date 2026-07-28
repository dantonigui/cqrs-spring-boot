package com.project.cqrs.admin.dlq.service;

import com.project.cqrs.admin.dlq.dto.DlqMessageResponse;
import com.project.cqrs.admin.dlq.dto.DlqPeekResponse;
import com.project.cqrs.admin.dlq.dto.DlqReplayResponse;
import com.project.cqrs.admin.dlq.dto.DlqStatsResponse;
import com.project.cqrs.admin.dlq.infrastructure.DlqInspector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("DlqService")
class DlqServiceTest {

    @Mock private DlqInspector      dlqInspector;
    @Mock private DlqReplayService  dlqReplayService;

    private DlqService dlqService;

    @BeforeEach
    void setUp() {
        dlqService = new DlqService(dlqInspector, dlqReplayService);
    }

    // ── stats() ──────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("stats()")
    class Stats {

        @Test
        @DisplayName("deve somar as contagens dos três topics DLQ conhecidos")
        void shouldSumCountsAcrossAllKnownTopics() {
            when(dlqInspector.countMessages("product.created.DLT")).thenReturn(2L);
            when(dlqInspector.countMessages("product.updated.DLT")).thenReturn(0L);
            when(dlqInspector.countMessages("product.deleted.DLT")).thenReturn(3L);

            DlqStatsResponse result = dlqService.stats();

            assertThat(result.totalPending()).isEqualTo(5L);
            assertThat(result.dlqCounts())
                    .containsEntry("product.created.DLT", 2L)
                    .containsEntry("product.updated.DLT", 0L)
                    .containsEntry("product.deleted.DLT", 3L);
        }

        @Test
        @DisplayName("deve retornar status HAS_PENDING quando total > 0")
        void shouldReturnHasPendingWhenTotalGreaterThanZero() {
            when(dlqInspector.countMessages(anyString())).thenReturn(0L, 1L, 0L);

            DlqStatsResponse result = dlqService.stats();

            assertThat(result.status()).isEqualTo("HAS_PENDING");
        }

        @Test
        @DisplayName("deve retornar status EMPTY quando todas as DLQs estão vazias")
        void shouldReturnEmptyWhenAllZero() {
            when(dlqInspector.countMessages(anyString())).thenReturn(0L);

            DlqStatsResponse result = dlqService.stats();

            assertThat(result.status()).isEqualTo("EMPTY");
            assertThat(result.totalPending()).isZero();
        }

        @Test
        @DisplayName("deve consultar o inspector exatamente uma vez por topic")
        void shouldQueryInspectorOncePerTopic() {
            when(dlqInspector.countMessages(anyString())).thenReturn(0L);

            dlqService.stats();

            verify(dlqInspector, times(3)).countMessages(anyString());
        }
    }

    // ── peek() ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("peek()")
    class Peek {

        @Test
        @DisplayName("deve adicionar sufixo .DLT quando o topic não o possui")
        void shouldAppendDltSuffixWhenMissing() {
            when(dlqInspector.peek("product.created.DLT", 10))
                    .thenReturn(List.of());

            dlqService.peek("product.created", 10);

            verify(dlqInspector).peek("product.created.DLT", 10);
        }

        @Test
        @DisplayName("não deve duplicar o sufixo quando o topic já termina em .DLT")
        void shouldNotDuplicateSuffixWhenAlreadyPresent() {
            when(dlqInspector.peek("product.created.DLT", 5))
                    .thenReturn(List.of());

            dlqService.peek("product.created.DLT", 5);

            verify(dlqInspector).peek("product.created.DLT", 5);
            verify(dlqInspector, never()).peek("product.created.DLT.DLT", 5);
        }

        @Test
        @DisplayName("deve lançar IllegalArgumentException para DLQ desconhecida")
        void shouldThrowForUnknownDlq() {
            assertThatThrownBy(() -> dlqService.peek("topic.inexistente", 10))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("desconhecida");

            verifyNoInteractions(dlqInspector);
        }

        @Test
        @DisplayName("deve retornar count igual ao tamanho da lista de mensagens")
        void shouldReturnCountMatchingMessageListSize() {
            List<DlqMessageResponse> messages = List.of(
                    new DlqMessageResponse("product.created.DLT", 0, 10L, "k1", "v1"),
                    new DlqMessageResponse("product.created.DLT", 0, 11L, "k2", "v2")
            );
            when(dlqInspector.peek("product.created.DLT", 10)).thenReturn(messages);

            DlqPeekResponse result = dlqService.peek("product.created", 10);

            assertThat(result.count()).isEqualTo(2);
            assertThat(result.messages()).isEqualTo(messages);
            assertThat(result.dlqTopic()).isEqualTo("product.created.DLT");
        }
    }

    // ── replay() ─────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("replay()")
    class Replay {

        @Test
        @DisplayName("deve lançar IllegalArgumentException para DLQ desconhecida")
        void shouldThrowForUnknownDlq() {
            assertThatThrownBy(() -> dlqService.replay("topic.qualquer"))
                    .isInstanceOf(IllegalArgumentException.class);

            verifyNoInteractions(dlqReplayService);
        }

        @Test
        @DisplayName("deve calcular originalTopic removendo o sufixo .DLT")
        void shouldComputeOriginalTopicByStrippingSuffix() {
            when(dlqReplayService.replay("product.created.DLT")).thenReturn(0);

            DlqReplayResponse result = dlqService.replay("product.created.DLT");

            assertThat(result.originalTopic()).isEqualTo("product.created");
        }

        @Test
        @DisplayName("deve retornar mensagem de sucesso quando replayed > 0")
        void shouldReturnSuccessMessageWhenReplayedGreaterThanZero() {
            when(dlqReplayService.replay("product.created.DLT")).thenReturn(4);

            DlqReplayResponse result = dlqService.replay("product.created");

            assertThat(result.replayed()).isEqualTo(4);
            assertThat(result.message()).contains("reenviadas");
        }

        @Test
        @DisplayName("deve retornar mensagem de vazio quando replayed == 0")
        void shouldReturnEmptyMessageWhenReplayedIsZero() {
            when(dlqReplayService.replay("product.created.DLT")).thenReturn(0);

            DlqReplayResponse result = dlqService.replay("product.created");

            assertThat(result.replayed()).isZero();
            assertThat(result.message()).contains("Nenhuma mensagem");
        }

        @Test
        @DisplayName("deve delegar ao DlqReplayService com o topic já normalizado (.DLT)")
        void shouldDelegateWithNormalizedTopic() {
            when(dlqReplayService.replay("product.updated.DLT")).thenReturn(1);

            dlqService.replay("product.updated");

            verify(dlqReplayService).replay("product.updated.DLT");
        }
    }
}