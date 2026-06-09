package com.project.cqrs.admin.idempotency.service;

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
    public boolean isNew(String eventId, String topic) {
        if(processedEventRepository.existsByEventIdAndTopic(eventId, topic)){
            log.warn("Evento duplicado detectado e descartado: eventId={}, topic={}", eventId, topic);
            return false;
        }

        try {
            processedEventRepository.saveAndFlush(new ProcessedEventEntity(eventId, topic));
            return true;
        } catch (DataIntegrityViolationException e) {
            log.warn("Race condition na idempotência: eventId={}, topic={}", eventId, topic);
            return false;
        }
    }
}
