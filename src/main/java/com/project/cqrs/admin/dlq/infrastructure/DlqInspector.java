package com.project.cqrs.admin.dlq.infrastructure;

import com.project.cqrs.admin.dlq.dto.DlqMessageResponse;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DlqInspector {

    private final DlqConsumerFactory consumerFactory;

    public DlqInspector(
            DlqConsumerFactory consumerFactory
    ) {
        this.consumerFactory = consumerFactory;
    }

    public long countMessages(String topic) {

        try (KafkaConsumer<String, String> consumer =
                     consumerFactory.create()) {

            List<TopicPartition> partitions =
                    consumer.partitionsFor(topic)
                            .stream()
                            .map(p ->
                                    new TopicPartition(
                                            topic,
                                            p.partition()
                                    ))
                            .toList();

            consumer.assign(partitions);

            Map<TopicPartition, Long> endOffsets =
                    consumer.endOffsets(partitions);

            Map<TopicPartition, Long> beginOffsets =
                    consumer.beginningOffsets(partitions);

            long count = 0;

            for (TopicPartition partition : partitions) {

                count +=
                        endOffsets.getOrDefault(partition, 0L)
                                - beginOffsets.getOrDefault(partition, 0L);
            }

            return count;
        }
    }

    public List<DlqMessageResponse> peek(
            String topic,
            int limit
    ) {

        List<DlqMessageResponse> messages =
                new ArrayList<>();

        try (KafkaConsumer<String, String> consumer =
                     consumerFactory.create()) {

            List<TopicPartition> partitions =
                    consumer.partitionsFor(topic)
                            .stream()
                            .map(p ->
                                    new TopicPartition(
                                            topic,
                                            p.partition()
                                    ))
                            .toList();

            consumer.assign(partitions);

            consumer.seekToEnd(partitions);

            for (TopicPartition partition : partitions) {

                long endOffset =
                        consumer.position(partition);

                long startOffset =
                        Math.max(0, endOffset - limit);

                consumer.seek(
                        partition,
                        startOffset
                );
            }

            ConsumerRecords<String, String> records =
                    consumer.poll(Duration.ofSeconds(3));

            for (ConsumerRecord<String, String> record : records) {

                messages.add(
                        new DlqMessageResponse(
                                record.topic(),
                                record.partition(),
                                record.offset(),
                                record.key(),
                                record.value()
                        )
                );
            }
        }

        return messages;
    }
}
