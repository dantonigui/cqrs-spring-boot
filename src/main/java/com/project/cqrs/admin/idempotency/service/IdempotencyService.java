package com.project.cqrs.admin.idempotency.service;
import com.project.cqrs.admin.idempotency.entity.*;
import com.project.cqrs.admin.idempotency.repository.ProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
@Transactional(transactionManager = "queryTransactionManager", propagation = Propagation.MANDATORY)
public class IdempotencyService {
    private final ProcessedEventRepository repository;
    public IdempotencyService(ProcessedEventRepository repository) { this.repository = repository; }
    public ProcessedEventEntity tryClaim(String eventId, String topic, Integer deliveryAttempt) {
        var existing = repository.findByEventIdAndTopic(eventId, topic);
        if (existing.isPresent()) {
            var record = existing.get();
            if (record.getStatus() == EventStatus.COMPLETED) return null;
            if (record.getStatus() == EventStatus.PROCESSING) {
                throw new IllegalStateException("Evento já em processamento");
            }
            record.retry();
            return repository.save(record);
        }
        // A concurrent duplicate fails the unique constraint and is retried after rollback.
        return repository.saveAndFlush(ProcessedEventEntity.claim(eventId, topic));
    }
    public void markCompleted(ProcessedEventEntity entity) { entity.markCompleted(); repository.save(entity); }
    public void markFailed(ProcessedEventEntity entity, Exception e) { entity.markFailed(e.getMessage()); repository.save(entity); }
}