package com.project.cqrs.admin.idempotency.entity;

import jakarta.persistence.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_events")
public class ProcessedEventEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 255)
    private String eventId;

    @Column(name = "topic", nullable = false, length = 255)
    private String topic;

    @Column(name = "processed_at", nullable = false, updatable = false)
    private LocalDateTime processedAt;

    protected ProcessedEventEntity() {}

    public ProcessedEventEntity(String eventId, String topic) {
        this.eventId     = eventId;
        this.topic       = topic;
        this.processedAt = LocalDateTime.now();
    }

    public Long getId()                  { return id; }
    public String getEventId()           { return eventId; }
    public String getTopic()             { return topic; }
    public LocalDateTime getProcessedAt(){ return processedAt; }
}
