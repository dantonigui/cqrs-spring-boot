-- ═══════════════════════════════════════════════════════════════════
-- V2__create_processed_events.sql
-- Confirmado 1:1 com ProcessedEventEntity (admin.idempotency.entity)
-- — nenhuma mudança necessária em relação à versão anterior.
-- ═══════════════════════════════════════════════════════════════════

CREATE TABLE IF NOT EXISTS processed_events (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    event_id     VARCHAR(255) NOT NULL,
    topic        VARCHAR(255) NOT NULL,
    processed_at DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_event_topic (event_id, topic),
    INDEX idx_processed_at (processed_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;