package com.project.cqrs.admin.idempotency.entity;

import jakarta.persistence.*;

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
public class ProcessedEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 255)
    private String eventId;

    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EventStatus status;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;

    private Integer retryCount;

    protected ProcessedEventEntity() {}

    public ProcessedEventEntity(String eventId, String topic, EventStatus status) {
        this.eventId     = eventId;
        this.topic       = topic;
        this.status      = status;
        this.processedAt = LocalDateTime.now();
        this.retryCount = 1;
    }

    public static ProcessedEventEntity claim(String eventId, String topic) {
        return new ProcessedEventEntity(eventId, topic, EventStatus.PROCESSING);
    }

    public void markCompleted() {
        this.status = EventStatus.COMPLETED;
    }

    public void markFailed() {
        this.status = EventStatus.FAILED;
    }

    public Long getId()                  { return id; }
    public String getEventId()           { return eventId; }
    public String getTopic()             { return topic; }
    public LocalDateTime getProcessedAt(){ return processedAt; }
    public EventStatus getStatus() { return status; }
    public Integer getRetryCount() { return retryCount; }

    public void updateRetryCount(Integer retryCount) { this.retryCount = retryCount; }
}
