package com.project.cqrs.config.kafka;


import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;


@Configuration
public class KafkaProducerConfig {


    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;


    private final KafkaTypeMappings kafkaTypeMappings;


    public KafkaProducerConfig(
            KafkaTypeMappings kafkaTypeMappings
    ) {
        this.kafkaTypeMappings = kafkaTypeMappings;
    }



    @Bean
    public ProducerFactory<String, Object> producerFactory() {


        Map<String,Object> config = new HashMap<>();


        config.put(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );


        config.put(
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                StringSerializer.class
        );


        config.put(
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                JsonSerializer.class
        );


        /*
         * Envia o header:
         *
         * __TypeId__
         *
         * para o consumer saber qual classe instanciar
         */
        config.put(
                JsonSerializer.ADD_TYPE_INFO_HEADERS,
                true
        );


        /*
         * Alias:
         *
         * productCreate -> ProductCreateEvent
         *
         */
        config.put(
                JsonSerializer.TYPE_MAPPINGS,
                kafkaTypeMappings.getTypeMappingsProperty()
        );


        /*
         * Garantia de entrega
         */
        config.put(
                ProducerConfig.ACKS_CONFIG,
                "all"
        );


        /*
         * Evita duplicação em caso de retry do producer
         */
        config.put(
                ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG,
                true
        );


        config.put(
                ProducerConfig.RETRIES_CONFIG,
                Integer.MAX_VALUE
        );


        config.put(
                ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION,
                5
        );


        return new DefaultKafkaProducerFactory<>(config);
    }



    @Bean
    public KafkaTemplate<String,Object> kafkaTemplate(
            ProducerFactory<String,Object> producerFactory
    ){

        return new KafkaTemplate<>(producerFactory);
    }

}