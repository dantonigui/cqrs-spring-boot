package com.project.cqrs.admin.dlq.service;

import com.project.cqrs.admin.dlq.infrastructure.DlqConsumerFactory;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Service
public class DlqReplayService {

    private final DlqConsumerFactory consumerFactory;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public DlqReplayService(
            DlqConsumerFactory consumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate
    ) {
        this.consumerFactory = consumerFactory;
        this.kafkaTemplate = kafkaTemplate;
    }

    public int replay(String dlqTopic) {

        String originalTopic =
                dlqTopic.replace(".DLT", "");

        int replayed = 0;

        try (KafkaConsumer<String, String> consumer =
                     consumerFactory.create()) {

            List<TopicPartition> partitions =
                    consumer.partitionsFor(dlqTopic)
                            .stream()
                            .map(p ->
                                    new TopicPartition(
                                            dlqTopic,
                                            p.partition()
                                    ))
                            .toList();

            consumer.assign(partitions);

            consumer.seekToBeginning(partitions);

            ConsumerRecords<String, String> records;

            do {

                records =
                        consumer.poll(Duration.ofSeconds(3));

                for (ConsumerRecord<String, String> record : records) {

                    kafkaTemplate.send(
                            originalTopic,
                            record.key(),
                            record.value()
                    );

                    replayed++;
                }

            } while (!records.isEmpty());
        }

        return replayed;
    }
}
