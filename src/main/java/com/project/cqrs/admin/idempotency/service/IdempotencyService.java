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

            record.updateRetryCount(deliveryAttempt);

            processedEventRepository.save(record);

            log.info("Reprocessando evento com tentativa anterior incompleta: " +
                    "eventId={}, topic={}", eventId, topic);
            return record;
        }

        try {
            ProcessedEventEntity entity = ProcessedEventEntity.claim(eventId, topic);

            entity.updateRetryCount(deliveryAttempt);

            return processedEventRepository.saveAndFlush(entity);

        } catch (DataIntegrityViolationException e) {
            // Race condition real: dois threads tentaram reivindicar
            // o mesmo evento novo ao mesmo tempo. O constraint
            // UNIQUE(event_id, topic) garante que só um vence.
            log.warn("Race condition na idempotência: eventId={}, topic={}",
                    eventId, topic);
            return null;
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markCompleted(ProcessedEventEntity entity) {

        entity.markCompleted();
        processedEventRepository.save(entity);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(ProcessedEventEntity entity) {
        entity.markFailed();
        processedEventRepository.save(entity);
    }

    @Transactional
    public void updateRetryCount(ProcessedEventEntity entity, Integer retryCount) {
        entity.updateRetryCount(retryCount);
        processedEventRepository.save(entity);
    }
}
