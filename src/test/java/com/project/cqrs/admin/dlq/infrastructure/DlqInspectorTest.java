package com.project.cqrs.admin.dlq.infrastructure;

import com.project.cqrs.admin.dlq.dto.DlqMessageResponse;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

/**
 * Testes do DlqInspector — a camada que efetivamente conversa com o
 * KafkaConsumer raw. Como DlqConsumerFactory é injetado (não é
 * `new KafkaConsumer()` inline), mockamos a factory para retornar
 * um KafkaConsumer mockado — sem precisar de Kafka real.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DlqInspector")
class DlqInspectorTest {

    @Mock private DlqConsumerFactory consumerFactory;

    @SuppressWarnings("unchecked")
    private final KafkaConsumer<String, String> consumer = mock(KafkaConsumer.class);

    private DlqInspector dlqInspector;

    private static final String TOPIC = "product.created.DLT";

    @BeforeEach
    void setUp() {
        dlqInspector = new DlqInspector(consumerFactory);
        when(consumerFactory.create()).thenReturn(consumer);
    }

    private PartitionInfo partitionInfo(String topic, int partition) {
        return new PartitionInfo(topic, partition, null, null, null);
    }

    // ── countMessages() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("countMessages()")
    class CountMessages {

        @Test
        @DisplayName("deve somar a diferença entre end e begin offsets de uma única partição")
        void shouldSumOffsetDifferenceForSinglePartition() {
            TopicPartition tp0 = new TopicPartition(TOPIC, 0);

            when(consumer.partitionsFor(TOPIC))
                    .thenReturn(List.of(partitionInfo(TOPIC, 0)));
            when(consumer.endOffsets(anyList()))
                    .thenReturn(Map.of(tp0, 15L));
            when(consumer.beginningOffsets(anyList()))
                    .thenReturn(Map.of(tp0, 5L));

            long count = dlqInspector.countMessages(TOPIC);

            assertThat(count).isEqualTo(10L);
        }

        @Test
        @DisplayName("deve somar corretamente através de múltiplas partições")
        void shouldSumAcrossMultiplePartitions() {
            TopicPartition tp0 = new TopicPartition(TOPIC, 0);
            TopicPartition tp1 = new TopicPartition(TOPIC, 1);

            when(consumer.partitionsFor(TOPIC)).thenReturn(List.of(
                    partitionInfo(TOPIC, 0),
                    partitionInfo(TOPIC, 1)
            ));
            when(consumer.endOffsets(anyList()))
                    .thenReturn(Map.of(tp0, 10L, tp1, 20L));
            when(consumer.beginningOffsets(anyList()))
                    .thenReturn(Map.of(tp0, 0L, tp1, 15L));

            // partição 0: 10-0=10 | partição 1: 20-15=5 | total = 15
            long count = dlqInspector.countMessages(TOPIC);

            assertThat(count).isEqualTo(15L);
        }

        @Test
        @DisplayName("deve retornar zero quando begin e end são iguais (DLQ vazia)")
        void shouldReturnZeroWhenTopicIsEmpty() {
            TopicPartition tp0 = new TopicPartition(TOPIC, 0);

            when(consumer.partitionsFor(TOPIC))
                    .thenReturn(List.of(partitionInfo(TOPIC, 0)));
            when(consumer.endOffsets(anyList())).thenReturn(Map.of(tp0, 7L));
            when(consumer.beginningOffsets(anyList())).thenReturn(Map.of(tp0, 7L));

            assertThat(dlqInspector.countMessages(TOPIC)).isZero();
        }

        @Test
        @DisplayName("deve fechar o consumer ao final (try-with-resources)")
        void shouldCloseConsumerAfterUse() {
            when(consumer.partitionsFor(TOPIC))
                    .thenReturn(List.of(partitionInfo(TOPIC, 0)));
            when(consumer.endOffsets(anyList()))
                    .thenReturn(Map.of(new TopicPartition(TOPIC, 0), 1L));
            when(consumer.beginningOffsets(anyList()))
                    .thenReturn(Map.of(new TopicPartition(TOPIC, 0), 0L));

            dlqInspector.countMessages(TOPIC);

            verify(consumer).close();
        }
    }

    // ── peek() ───────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("peek()")
    class Peek {

        @Test
        @DisplayName("deve converter os ConsumerRecords em DlqMessageResponse")
        void shouldConvertConsumerRecordsToMessageResponses() {
            TopicPartition tp0 = new TopicPartition(TOPIC, 0);

            when(consumer.partitionsFor(TOPIC))
                    .thenReturn(List.of(partitionInfo(TOPIC, 0)));
            when(consumer.position(tp0)).thenReturn(20L);

            ConsumerRecord<String, String> record1 =
                    new ConsumerRecord<>(TOPIC, 0, 18L, "key-1", "value-1");
            ConsumerRecord<String, String> record2 =
                    new ConsumerRecord<>(TOPIC, 0, 19L, "key-2", "value-2");

            ConsumerRecords<String, String> records = new ConsumerRecords<>(
                    Map.of(tp0, List.of(record1, record2)));

            when(consumer.poll(any(Duration.class))).thenReturn(records);

            List<DlqMessageResponse> result = dlqInspector.peek(TOPIC, 10);

            assertThat(result).hasSize(2);
            assertThat(result.get(0).key()).isEqualTo("key-1");
            assertThat(result.get(0).value()).isEqualTo("value-1");
            assertThat(result.get(0).offset()).isEqualTo(18L);
            assertThat(result.get(1).key()).isEqualTo("key-2");
        }

        @Test
        @DisplayName("deve calcular startOffset como max(0, endOffset - limit)")
        void shouldCalculateStartOffsetCorrectly() {
            TopicPartition tp0 = new TopicPartition(TOPIC, 0);

            when(consumer.partitionsFor(TOPIC))
                    .thenReturn(List.of(partitionInfo(TOPIC, 0)));
            // endOffset = 5, limit = 10 → startOffset deveria ser max(0, 5-10) = 0
            when(consumer.position(tp0)).thenReturn(5L);
            when(consumer.poll(any(Duration.class)))
                    .thenReturn(new ConsumerRecords<>(Map.of()));

            dlqInspector.peek(TOPIC, 10);

            verify(consumer).seek(tp0, 0L);
        }

        @Test
        @DisplayName("deve retornar lista vazia quando não há mensagens")
        void shouldReturnEmptyListWhenNoMessages() {
            when(consumer.partitionsFor(TOPIC))
                    .thenReturn(List.of(partitionInfo(TOPIC, 0)));
            when(consumer.position(any())).thenReturn(0L);
            when(consumer.poll(any(Duration.class)))
                    .thenReturn(new ConsumerRecords<>(Map.of()));

            List<DlqMessageResponse> result = dlqInspector.peek(TOPIC, 10);

            assertThat(result).isEmpty();
        }
    }
}