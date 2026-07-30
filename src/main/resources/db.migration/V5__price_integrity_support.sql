-- ═══════════════════════════════════════════════════════════════════
-- V5__price_integrity_support.sql
--
-- Ajustado: tabela agora é "product_query" (sem hífen, ver
-- ProductQueryEntity corrigida) — não precisa mais de backticks.
-- ═══════════════════════════════════════════════════════════════════

CREATE INDEX IF NOT EXISTS idx_product_query_batch_lookup
    ON product_query (product_id, product_price, product_name);