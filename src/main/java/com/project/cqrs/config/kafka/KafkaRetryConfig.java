package com.project.cqrs.config.kafka;


import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.*;
import org.springframework.util.backoff.ExponentialBackOff;


@Configuration
public class KafkaRetryConfig {

    @Bean
    public DefaultErrorHandler kafkaErrorHandler(
            KafkaTemplate<String, Object> kafkaTemplate
    ) {


        DeadLetterPublishingRecoverer recoverer =
                new DeadLetterPublishingRecoverer(
                        kafkaTemplate,
                        (record, exception) ->
                                new TopicPartition(
                                        record.topic()+".DLT",
                                        record.partition()
                                )
                );



        ExponentialBackOff backOff =
                new ExponentialBackOff(
                        1000,
                        2
                );


        backOff.setMaxInterval(
                4000
        );


        backOff.setMaxElapsedTime(
                10000
        );



        return new DefaultErrorHandler(
                recoverer,
                backOff
        );
    }
}