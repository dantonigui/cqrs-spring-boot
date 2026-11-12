package com.project.cqrs.command.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events")
@Getter
@NoArgsConstructor
public class OutboxEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false) private String topic;
    @Column(nullable = false) private String eventKey;
    @Column(nullable = false) private String eventType;
    @Column(nullable = false, columnDefinition = "LONGTEXT") private String payload;
    @Column(nullable = false) private LocalDateTime createdAt;
    private LocalDateTime publishedAt;

    public OutboxEvent(String topic, String key, String type, String payload) {
        this.topic = topic; this.eventKey = key; this.eventType = type;
        this.payload = payload; this.createdAt = LocalDateTime.now();
    }
    public void published() { publishedAt = LocalDateTime.now(); }
}