package com.project.cqrs.command.outbox;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxDispatcher {
    private static final Logger log = LoggerFactory.getLogger(OutboxDispatcher.class);
    private final OutboxRepository repository;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper mapper;
    public OutboxDispatcher(OutboxRepository repository, KafkaTemplate<String, Object> kafka, ObjectMapper mapper) {
        this.repository = repository; this.kafka = kafka; this.mapper = mapper;
    }
    @Scheduled(fixedDelayString = "${app.outbox.poll-ms:1000}")
    @Transactional("commandTransactionManager")
    public void dispatch() {
        for (OutboxEvent event : repository.pending(PageRequest.of(0, 50))) {
            try {
                if (!event.getEventType().startsWith("com.project.cqrs.shared.event.")) {
                    throw new IllegalStateException("Tipo de evento não permitido");
                }
                Object payload = mapper.readValue(event.getPayload(), Class.forName(event.getEventType()));
                kafka.send(event.getTopic(), event.getEventKey(), payload).get(10, TimeUnit.SECONDS);
                event.published();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("Publicação interrompida", e);
            } catch (Exception e) {
                log.error("Outbox {} pendente; nova tentativa no próximo ciclo", event.getId(), e);
                break; // Preserve publication order; at-least-once delivery is intentional.
            }
        }
    }
}