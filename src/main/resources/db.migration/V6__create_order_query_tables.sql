-- ═══════════════════════════════════════════════════════════════════
-- V6__create_order_query_tables.sql
-- Query Side — order_query, order_item_query, payment_query
--
-- Alterações em relação à versão anterior:
--   - REMOVIDA a FK de order_item_query → order_query que eu tinha
--     adicionado antes por suposição — na verdade JÁ EXISTIA no
--     @JoinColumn(referencedColumnName="order_id") da entity real,
--     então mantida (confirmado, não é mudança).
--   - payment_query.payment_method mantido VARCHAR(30) — agora
--     correto porque adicionamos @Enumerated(STRING) na entity
--     (antes fosse persistir como ORDINAL, o tipo da coluna estaria
--     semanticamente errado de qualquer forma)
--   - order_query NÃO tem mais relação com payment_query no Java
--     (mappedBy inválido removido) — sem impacto na tabela em si,
--     só na ausência de FK/relação ORM entre elas (que já não existia
--     fisicamente, era só uma inconsistência do lado Java)
-- ═══════════════════════════════════════════════════════════════════

-- ── ORDER QUERY ────────────────────────────────────────────────────

CREATE TABLE IF NOT EXISTS order_query (
                                           id             BIGINT        NOT NULL AUTO_INCREMENT,
                                           order_id       BIGINT        NOT NULL,
                                           user_id        BIGINT        NOT NULL,
                                           status         VARCHAR(30)   NOT NULL,
    total_amount   DECIMAL(10,2) NOT NULL,
    created_at     DATETIME      NOT NULL,
    updated_at     DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP
    ON UPDATE CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    UNIQUE KEY uq_order_query_order_id (order_id),
    INDEX idx_order_query_user   (user_id),
    INDEX idx_order_query_status (status)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── ORDER ITEM QUERY ───────────────────────────────────────────────
-- FK confirmada: OrderItemQueryEntity usa
-- @JoinColumn(name="order_id", referencedColumnName="order_id")
-- apontando para order_query.order_id (não para o PK "id").

CREATE TABLE IF NOT EXISTS order_item_query (
                                                id           BIGINT        NOT NULL AUTO_INCREMENT,
                                                order_id     BIGINT        NOT NULL,
                                                product_id   BIGINT        NOT NULL,
                                                product_name VARCHAR(255)  NOT NULL,
    unit_price   DECIMAL(10,2) NOT NULL,
    quantity     INT           NOT NULL,

    PRIMARY KEY (id),
    INDEX idx_order_item_query_order (order_id),

    CONSTRAINT fk_order_item_query_order FOREIGN KEY (order_id)
    REFERENCES order_query(order_id) ON DELETE CASCADE
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── PAYMENT QUERY ──────────────────────────────────────────────────
-- Sem FK para order_query — desacoplamento proposital (confirmado
-- pela ausência de qualquer relação JPA no lado Payment → Order).

CREATE TABLE IF NOT EXISTS payment_query (
                                             id                 BIGINT        NOT NULL AUTO_INCREMENT,
                                             order_id           BIGINT        NOT NULL,
                                             payment_method     VARCHAR(30)   NOT NULL,
    payment_type       VARCHAR(30)   NOT NULL,
    payment_status     VARCHAR(30)   NOT NULL,
    mp_payment_id      VARCHAR(100),
    transaction_amount DECIMAL(10,2) NOT NULL,
    installments       INT           NOT NULL DEFAULT 1,
    card_last_four     VARCHAR(4),
    card_brand         VARCHAR(20),
    in_person_method   VARCHAR(30),
    created_at         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,

    PRIMARY KEY (id),
    INDEX idx_payment_query_order  (order_id),
    INDEX idx_payment_query_status (payment_status),

    UNIQUE KEY uq_payment_query_order_mp (order_id, mp_payment_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;