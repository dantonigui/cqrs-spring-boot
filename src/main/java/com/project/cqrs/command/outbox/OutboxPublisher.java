package com.project.cqrs.command.outbox;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxPublisher {
    private final OutboxRepository repository;
    private final ObjectMapper mapper;
    public OutboxPublisher(OutboxRepository repository, ObjectMapper mapper) {
        this.repository = repository; this.mapper = mapper;
    }
    @Transactional("commandTransactionManager")
    public void publish(String topic, String key, Object event) {
        try {
            repository.save(new OutboxEvent(topic, key, event.getClass().getName(), mapper.writeValueAsString(event)));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Não foi possível registrar o evento", e);
        }
    }
}