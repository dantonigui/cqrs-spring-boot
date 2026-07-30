-- ═══════════════════════════════════════════════════════════════════
-- V4__payment_idempotency_constraints.sql
-- Ajustado para referenciar payment_status (não "status", que era
-- o nome assumido incorretamente na versão anterior).
-- ═══════════════════════════════════════════════════════════════════

ALTER TABLE payments
    ADD COLUMN active_payment_sentinel VARCHAR(50) GENERATED ALWAYS AS (
        CASE
            WHEN payment_status IN ('PENDING', 'APPROVED', 'IN_PROCESS')
                THEN CONCAT('active-', order_id)
            ELSE NULL
            END
        ) VIRTUAL;

CREATE UNIQUE INDEX uq_payments_active_per_order
    ON payments (active_payment_sentinel);

CREATE UNIQUE INDEX uq_payments_mp_id
    ON payments (mp_payment_id);