-- ═══════════════════════════════════════════════════════════════════
-- V1__init_schema.sql
-- Schema base — category_command, user_command, user_query,
-- category_query, product_command, product_query
--
-- Reconstruído a partir das entities JPA reais compartilhadas.
-- Nomes de tabela normalizados de "category-command"/"product-query"
-- (hífen) para snake_case — ver CategoryCommandEntity/ProductQueryEntity
-- corrigidas.
-- ═══════════════════════════════════════════════════════════════════

-- ── CATEGORY COMMAND ───────────────────────────────────────────────
-- Entity: CategoryCommandEntity — sem timestamps, sem unique constraint
-- declarada em Java. Adicionamos UNIQUE(category_name) como proteção
-- de integridade — não conflita com nada na entidade.

CREATE TABLE IF NOT EXISTS category_command (
                                                category_id    BIGINT       NOT NULL AUTO_INCREMENT,
                                                category_name  VARCHAR(255) NOT NULL,

    PRIMARY KEY (category_id),
    UNIQUE KEY uq_category_command_name (category_name)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── USER COMMAND ───────────────────────────────────────────────────
-- Entity: UserCommandEntity — @Table(name="user_command"), sem timestamps.
-- userGoogleId deveria ser único na prática (login OAuth2), mas isso
-- NÃO está declarado na entidade Java. Adicionamos UNIQUE como proteção
-- — recomendo adicionar @Column(unique = true) no Java também para
-- deixar explícito.

CREATE TABLE IF NOT EXISTS user_command (
                                            user_id           BIGINT       NOT NULL AUTO_INCREMENT,
                                            user_name         VARCHAR(255) NOT NULL,
    user_email        VARCHAR(255) NOT NULL,
    user_picture_url  VARCHAR(500),
    user_google_id    VARCHAR(255) NOT NULL,
    user_role         VARCHAR(30)  NOT NULL,

    PRIMARY KEY (user_id),
    UNIQUE KEY uq_user_command_google_id (user_google_id),
    UNIQUE KEY uq_user_command_email (user_email)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── USER QUERY ─────────────────────────────────────────────────────
-- Entity: UserQueryEntity — @Id SEM @GeneratedValue! O ID é o mesmo
-- do user_command (atribuído manualmente no UserEventConsumer a
-- partir do UserCreatedEvent). NÃO usar AUTO_INCREMENT aqui.
-- Campo "userPicture" (não "userPictureUrl" como no command side).

CREATE TABLE IF NOT EXISTS user_query (
                                          user_id       BIGINT       NOT NULL,   -- SEM AUTO_INCREMENT
                                          user_name     VARCHAR(255) NOT NULL,
    user_email    VARCHAR(255) NOT NULL,
    user_picture  VARCHAR(500) NOT NULL,
    user_role     VARCHAR(30)  NOT NULL,

    PRIMARY KEY (user_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── CATEGORY QUERY ─────────────────────────────────────────────────
-- Entity: CategoryQueryEntity — @Id SEM @GeneratedValue (mesmo ID do
-- category_command). categoryName sem @Column(nullable=false) explícito
-- na entidade — mantemos NOT NULL por prática de integridade, já que
-- é sempre preenchido pelo consumer.

CREATE TABLE IF NOT EXISTS category_query (
                                              category_id    BIGINT       NOT NULL,   -- SEM AUTO_INCREMENT
                                              category_name  VARCHAR(255) NOT NULL,

    PRIMARY KEY (category_id)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── PRODUCT COMMAND ────────────────────────────────────────────────
-- Entity: ProductCommandEntity — @Table(name="product_command").
-- FK column é literalmente "category" (não "category_id") por causa
-- de @JoinColumn(name = "category") explícito na entity.
-- productPrice sem precision/scale explícitos no Java — usamos
-- DECIMAL(19,2), default do Hibernate para BigDecimal sem @Column
-- especificado. Recomendo adicionar precision=10,scale=2 explícito
-- no Java para consistência com o resto do projeto (orders, payments
-- já usam 10,2).

CREATE TABLE IF NOT EXISTS product_command (
                                               product_id     BIGINT        NOT NULL AUTO_INCREMENT,
                                               product_name   VARCHAR(255)  NOT NULL,
    product_code   VARCHAR(255)  NOT NULL,
    product_price  DECIMAL(19,2) NOT NULL,
    product_image  VARCHAR(500)  NOT NULL,
    category        BIGINT        NOT NULL,   -- nome literal da FK

    PRIMARY KEY (product_id),
    INDEX idx_product_command_category (category),
    CONSTRAINT fk_product_command_category FOREIGN KEY (category)
    REFERENCES category_command(category_id) ON DELETE RESTRICT,
    CONSTRAINT chk_product_command_price_positive CHECK (product_price > 0.00)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ── PRODUCT QUERY ──────────────────────────────────────────────────
-- Entity: ProductQueryEntity — @Id SEM @GeneratedValue (mesmo ID do
-- product_command). categoryId é um Long solto — SEM relação JPA,
-- SEM FK (denormalizado de propósito no lado de leitura). Não
-- adicionamos constraint de FK aqui para não contradizer o design.

CREATE TABLE IF NOT EXISTS product_query (
                                             product_id     BIGINT        NOT NULL,   -- SEM AUTO_INCREMENT
                                             product_name   VARCHAR(255)  NOT NULL,
    product_code   VARCHAR(255)  NOT NULL,
    product_price  DECIMAL(19,2) NOT NULL,
    product_image  VARCHAR(500)  NOT NULL,
    category_id    BIGINT        NOT NULL,   -- sem FK — denormalizado

    PRIMARY KEY (product_id),
    INDEX idx_product_query_category (category_id),
    CONSTRAINT chk_product_query_price_positive CHECK (product_price > 0.00)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;