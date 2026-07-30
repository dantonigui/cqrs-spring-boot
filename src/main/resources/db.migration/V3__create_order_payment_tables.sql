-- ═══════════════════════════════════════════════════════════════════
-- V3__create_order_payment_tables.sql
-- Command Side — orders, order_items, payments
--
-- Alterações em relação à versão anterior:
--   - total_amount (era literalmente "totalAmount" no Java — corrigido)
--   - payment_status (era "status" — a entity real usa "paymentStatus",
--     que a naming strategy converte para "payment_status")
--   - payments.order_id SEM UNIQUE — @OneToOne foi corrigido para
--     @ManyToOne, então múltiplas linhas de pagamento por pedido são
--     esperadas (tentativas rejeitadas, retries)
--   - REMOVIDO mp_preference_id — não existe na PaymentCommandEntity real
-- ═══════════════════════════════════════════════════════════════════

-- ── ORDERS ─────────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS orders (
                                      id               BIGINT        NOT NULL AUTO_INCREMENT,
                                      user_id          BIGINT        NOT NULL,
                                      status           VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
    total_amount     DECIMAL(10,2) NOT NULL,
    created_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_orders_user   (user_id),
    INDEX idx_orders_status (status),

    CONSTRAINT chk_orders_total_positive CHECK (total_amount > 0.00)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── ORDER ITEMS ────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS order_items (
                                           id           BIGINT        NOT NULL AUTO_INCREMENT,
                                           order_id     BIGINT        NOT NULL,
                                           product_id   BIGINT        NOT NULL,
                                           product_name VARCHAR(255)  NOT NULL,
    unit_price   DECIMAL(10,2) NOT NULL,
    quantity     INT           NOT NULL,

    PRIMARY KEY (id),
    INDEX idx_order_items_order (order_id),

    CONSTRAINT fk_order_items_order FOREIGN KEY (order_id)
    REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT chk_order_items_unit_price_positive CHECK (unit_price > 0.00),
    CONSTRAINT chk_order_items_quantity_positive CHECK (quantity > 0)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── PAYMENTS ───────────────────────────────────────────────────────
-- order_id SEM UNIQUE: @ManyToOne (corrigido de @OneToOne) permite
-- múltiplas linhas de pagamento por pedido — necessário para a lógica
-- de idempotência (PIX reenviado, tentativas de cartão rejeitadas).

CREATE TABLE IF NOT EXISTS payments (
                                        id                    BIGINT        NOT NULL AUTO_INCREMENT,
                                        order_id              BIGINT        NOT NULL,
                                        payment_method        VARCHAR(30)   NOT NULL,
    payment_type          VARCHAR(30)   NOT NULL,
    payment_status        VARCHAR(30)   NOT NULL DEFAULT 'PENDING',
    mp_payment_id         VARCHAR(100),
    transaction_amount    DECIMAL(10,2) NOT NULL,
    installments          INT           NOT NULL DEFAULT 1,

    in_person_method      VARCHAR(30),

    pix_qr_code           TEXT,
    pix_qr_code_base64    TEXT,
    pix_expiration        DATETIME,

    card_last_four        VARCHAR(4),
    card_brand            VARCHAR(20),

    created_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at            DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_payments_order  (order_id),
    INDEX idx_payments_status (payment_status),

    CONSTRAINT fk_payments_order FOREIGN KEY (order_id)
    REFERENCES orders(id) ON DELETE CASCADE,
    CONSTRAINT chk_payments_amount_positive CHECK (transaction_amount > 0.00)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;