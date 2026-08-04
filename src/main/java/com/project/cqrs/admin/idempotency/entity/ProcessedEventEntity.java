package com.project.cqrs.admin.idempotency.entity;

import jakarta.persistence.*;
import lombok.Getter;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "processed_events",
        uniqueConstraints = {
                @UniqueConstraint(
                        columnNames = {
                                "event_id",
                                "topic"
                        }
                )
        }
)
@Getter
public class ProcessedEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 255)
    private String eventId;

    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @Column(name = "claimed_at", nullable = false, updatable = false)
    private LocalDateTime claimedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "error_message", length = 2000)
    private String errorMessage;

    protected ProcessedEventEntity() {
    }

    private ProcessedEventEntity(String eventId, String topic) {
        this.eventId = eventId;
        this.topic = topic;
        this.status = EventStatus.PROCESSING;
        this.claimedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
        this.retryCount = 1;
    }

    public static ProcessedEventEntity claim(
            String eventId,
            String topic
    ) {
        return new ProcessedEventEntity(eventId, topic);
    }

    public void retry() {
        this.retryCount++;
        this.status = EventStatus.PROCESSING;
        this.updatedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    public void markCompleted() {
        this.status = EventStatus.COMPLETED;
        this.updatedAt = LocalDateTime.now();
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = EventStatus.FAILED;
        this.updatedAt = LocalDateTime.now();
        this.errorMessage = errorMessage;
    }
}