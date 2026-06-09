package com.project.cqrs.admin.dlq.infrastructure;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Properties;
import java.util.UUID;

@Component
public class DlqConsumerFactory {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    public KafkaConsumer<String, String> create() {

        Properties props = new Properties();

        props.put(
                "bootstrap.servers",
                bootstrapServers
        );

        props.put(
                "group.id",
                "dlq-admin-" + UUID.randomUUID()
        );

        props.put(
                "key.deserializer",
                "org.apache.kafka.common.serialization.StringDeserializer"
        );

        props.put(
                "value.deserializer",
                "org.apache.kafka.common.serialization.StringDeserializer"
        );

        props.put(
                "auto.offset.reset",
                "earliest"
        );

        props.put(
                "enable.auto.commit",
                "false"
        );

        return new KafkaConsumer<>(props);
    }
}
