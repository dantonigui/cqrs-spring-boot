-- ═══════════════════════════════════════════════════════════════════
-- V8__add_processed_events_status.sql
--
-- Corrige o bug: isNew() gravava o evento como "processado" ANTES
-- de confirmar que o processamento deu certo. Se o processamento
-- falhasse depois, o retry do Kafka nunca reprocessava — o registro
-- já existia e a segunda tentativa retornava false sem fazer nada.
--
-- Com status de duas fases:
--   PROCESSING → reivindicado, ainda não confirmado
--   COMPLETED  → processado com sucesso, nunca mais reprocessa
--
-- Um registro em PROCESSING sinaliza "uma tentativa começou mas não
-- terminou" — libera reprocessamento em vez de bloquear.
-- ═══════════════════════════════════════════════════════════════════

ALTER TABLE processed_events
    ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED'
    AFTER topic;

-- Registros existentes (gravados pela versão antiga do código) já
-- foram efetivamente processados quando a linha existia — o DEFAULT
-- 'COMPLETED' acima cobre isso automaticamente para dados legados.

CREATE INDEX idx_processed_events_status ON processed_events (status);