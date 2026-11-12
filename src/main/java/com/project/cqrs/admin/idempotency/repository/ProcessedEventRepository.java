package com.project.cqrs.admin.idempotency.repository;

import com.project.cqrs.admin.idempotency.entity.ProcessedEventEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEventEntity,Long> {

    boolean existsByEventIdAndTopic(String eventId, String topic);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    Optional<ProcessedEventEntity> findByEventIdAndTopic(String eventId, String topic);
}
