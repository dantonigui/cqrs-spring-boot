package com.project.cqrs.admin.dlq.infrastructure;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.TopicPartition;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class DlqReplayGateway {

    private final DlqConsumerFactory consumerFactory;
    private final KafkaTemplate<Object, Object> kafkaTemplate;

    public DlqReplayGateway(
            DlqConsumerFactory consumerFactory,
            KafkaTemplate<Object, Object> kafkaTemplate
    ) {
        this.consumerFactory = consumerFactory;
        this.kafkaTemplate = kafkaTemplate;
    }

    public int replayMessages(
            String sourceTopic,
            String destinationTopic
    ) {

        int replayed = 0;

        try (KafkaConsumer<String, String> consumer =
                     consumerFactory.create()) {

            List<TopicPartition> partitions =
                    consumer.partitionsFor(sourceTopic)
                            .stream()
                            .map(partition ->
                                    new TopicPartition(
                                            sourceTopic,
                                            partition.partition()
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
                            destinationTopic,
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