package com.project.cqrs.admin.idempotency.service;

import com.project.cqrs.admin.idempotency.entity.EventStatus;
import com.project.cqrs.admin.idempotency.entity.ProcessedEventEntity;
import com.project.cqrs.admin.idempotency.repository.ProcessedEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

    private static final Logger log = LoggerFactory.getLogger(IdempotencyService.class);

    private final ProcessedEventRepository processedEventRepository;

    public IdempotencyService(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProcessedEventEntity tryClaim(String eventId, String topic, Integer deliveryAttempt) {

        var existing = processedEventRepository.findByEventIdAndTopic(eventId, topic);

        if (existing.isPresent()) {
            ProcessedEventEntity record = existing.get();

            if (record.getStatus() == EventStatus.COMPLETED) {
                log.warn("Evento já processado com sucesso, descartado: " +
                        "eventId={}, topic={}", eventId, topic);
                return null;
            }

            record.retry();

            processedEventRepository.save(record);

            log.info("Reprocessando evento com tentativa anterior incompleta: " +
                    "eventId={}, topic={}", eventId, topic);

            return record;
        }

        try {
            ProcessedEventEntity entity = ProcessedEventEntity.claim(eventId, topic);

            return processedEventRepository.saveAndFlush(entity);

        } catch (DataIntegrityViolationException ex) {

            log.warn(
                    "Race condition para eventId={}, topic={}",
                    eventId,
                    topic
            );

            return processedEventRepository
                    .findByEventIdAndTopic(eventId, topic)
                    .orElse(null);
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(ProcessedEventEntity entity) {

        entity.markCompleted();
        processedEventRepository.save(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(ProcessedEventEntity entity, Exception e) {

        entity.markFailed(e.getMessage());

        processedEventRepository.save(entity);
    }
}
