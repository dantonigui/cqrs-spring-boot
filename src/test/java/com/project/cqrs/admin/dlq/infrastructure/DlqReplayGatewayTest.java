package com.project.cqrs.admin.dlq.infrastructure;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testes do DlqReplayGateway — republica mensagens da DLQ no topic
 * original via KafkaTemplate, após lê-las com um KafkaConsumer raw
 * (mockado através da DlqConsumerFactory).
 *
 * O loop `do { poll() } while (!records.isEmpty())` é simulado
 * configurando duas respostas consecutivas de poll(): uma com
 * mensagens, outra vazia — para encerrar o laço.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DlqReplayGateway")
class DlqReplayGatewayTest {

    @Mock private DlqConsumerFactory        consumerFactory;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @SuppressWarnings("unchecked")
    private final KafkaConsumer<String, String> consumer = mock(KafkaConsumer.class);

    private DlqReplayGateway gateway;

    private static final String SOURCE_TOPIC      = "product.created.DLT";
    private static final String DESTINATION_TOPIC = "product.created";

    @BeforeEach
    void setUp() {
        gateway = new DlqReplayGateway(consumerFactory, kafkaTemplate);
        when(consumerFactory.create()).thenReturn(consumer);
        when(consumer.partitionsFor(SOURCE_TOPIC)).thenReturn(
                List.of(new PartitionInfo(SOURCE_TOPIC, 0, null, null, null)));
    }

    private ConsumerRecords<String, String> recordsWith(
            ConsumerRecord<String, String>... records) {
        TopicPartition tp = new TopicPartition(SOURCE_TOPIC, 0);
        return new ConsumerRecords<>(Map.of(tp, List.of(records)));
    }

    private ConsumerRecords<String, String> emptyRecords() {
        return new ConsumerRecords<>(Map.of());
    }

    @Test
    @DisplayName("deve republicar cada mensagem lida no topic de destino")
    void shouldRepublishEachMessageToDestinationTopic() {
        ConsumerRecord<String, String> record1 =
                new ConsumerRecord<>(SOURCE_TOPIC, 0, 0L, "key-1", "value-1");
        ConsumerRecord<String, String> record2 =
                new ConsumerRecord<>(SOURCE_TOPIC, 0, 1L, "key-2", "value-2");

        when(consumer.poll(any(Duration.class)))
                .thenReturn(recordsWith(record1, record2))
                .thenReturn(emptyRecords());

        gateway.replayMessages(SOURCE_TOPIC, DESTINATION_TOPIC);

        verify(kafkaTemplate).send(DESTINATION_TOPIC, "key-1", "value-1");
        verify(kafkaTemplate).send(DESTINATION_TOPIC, "key-2", "value-2");
    }

    @Test
    @DisplayName("deve retornar o total de mensagens replicadas")
    void shouldReturnTotalReplayedCount() {
        ConsumerRecord<String, String> record1 =
                new ConsumerRecord<>(SOURCE_TOPIC, 0, 0L, "k1", "v1");

        when(consumer.poll(any(Duration.class)))
                .thenReturn(recordsWith(record1))
                .thenReturn(emptyRecords());

        int replayed = gateway.replayMessages(SOURCE_TOPIC, DESTINATION_TOPIC);

        assertThat(replayed).isEqualTo(1);
    }

    @Test
    @DisplayName("deve retornar zero quando a DLQ está vazia")
    void shouldReturnZeroWhenDlqIsEmpty() {
        when(consumer.poll(any(Duration.class))).thenReturn(emptyRecords());

        int replayed = gateway.replayMessages(SOURCE_TOPIC, DESTINATION_TOPIC);

        assertThat(replayed).isZero();
        verifyNoInteractions(kafkaTemplate);
    }

    @Test
    @DisplayName("deve continuar consumindo em múltiplos polls até esvaziar a DLQ")
    void shouldKeepPollingUntilDlqIsEmpty() {
        ConsumerRecord<String, String> batch1 =
                new ConsumerRecord<>(SOURCE_TOPIC, 0, 0L, "k1", "v1");
        ConsumerRecord<String, String> batch2 =
                new ConsumerRecord<>(SOURCE_TOPIC, 0, 1L, "k2", "v2");

        // Simula 2 batches de mensagens antes da DLQ esvaziar
        when(consumer.poll(any(Duration.class)))
                .thenReturn(recordsWith(batch1))
                .thenReturn(recordsWith(batch2))
                .thenReturn(emptyRecords());

        int replayed = gateway.replayMessages(SOURCE_TOPIC, DESTINATION_TOPIC);

        assertThat(replayed).isEqualTo(2);
        verify(consumer, times(3)).poll(any(Duration.class));
    }

    @Test
    @DisplayName("deve posicionar o consumer no início da partição antes de consumir")
    void shouldSeekToBeginningBeforeConsuming() {
        when(consumer.poll(any(Duration.class))).thenReturn(emptyRecords());

        gateway.replayMessages(SOURCE_TOPIC, DESTINATION_TOPIC);

        verify(consumer).seekToBeginning(anyList());
    }

    @Test
    @DisplayName("deve fechar o consumer ao final da operação")
    void shouldCloseConsumerAfterReplay() {
        when(consumer.poll(any(Duration.class))).thenReturn(emptyRecords());

        gateway.replayMessages(SOURCE_TOPIC, DESTINATION_TOPIC);

        verify(consumer).close();
    }
}