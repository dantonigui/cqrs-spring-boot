CREATE TABLE user_command (
 user_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 user_name VARCHAR(255), user_email VARCHAR(255), user_picture_url VARCHAR(255), user_google_id VARCHAR(255), user_role VARCHAR(255),
 UNIQUE KEY uq_user_google_id (user_google_id)
);
CREATE TABLE category_command (category_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, category_name VARCHAR(255) NOT NULL);
CREATE TABLE product_command (
 product_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
 product_name VARCHAR(255) NOT NULL, product_code VARCHAR(255) NOT NULL, product_price DECIMAL(10,2) NOT NULL,
 product_image VARCHAR(255) NOT NULL, category BIGINT NOT NULL, stock INT NOT NULL DEFAULT 0,
 CONSTRAINT fk_product_category FOREIGN KEY (category) REFERENCES category_command(category_id),
 CONSTRAINT chk_product_price CHECK (product_price > 0), CONSTRAINT chk_product_stock CHECK (stock >= 0)
);
CREATE TABLE order_command (
 order_id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL, status VARCHAR(30) NOT NULL,
 total_amount DECIMAL(10,2) NOT NULL, created_at DATETIME(6), updated_at DATETIME(6),
 INDEX idx_order_user(user_id), CONSTRAINT chk_order_total CHECK (total_amount > 0)
);
CREATE TABLE order_command_items (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, product_id BIGINT NOT NULL,
 product_name VARCHAR(255) NOT NULL, unit_price DECIMAL(10,2) NOT NULL, quantity INT NOT NULL,
 CONSTRAINT fk_order_item_order FOREIGN KEY(order_id) REFERENCES order_command(order_id),
 CONSTRAINT fk_order_item_product FOREIGN KEY(product_id) REFERENCES product_command(product_id),
 CONSTRAINT chk_item_quantity CHECK(quantity > 0), CONSTRAINT chk_item_price CHECK(unit_price > 0)
);
CREATE TABLE payments (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL,
 payment_method VARCHAR(30) NOT NULL, payment_type VARCHAR(30) NOT NULL, payment_status VARCHAR(30) NOT NULL,
 mp_payment_id VARCHAR(100), transaction_amount DECIMAL(10,2) NOT NULL, installments INT NOT NULL,
 in_person_method VARCHAR(30), pix_qr_code TEXT, pix_qr_code_base64 TEXT, pix_expiration DATETIME(6),
 card_last_four VARCHAR(4), card_brand VARCHAR(20), created_at DATETIME(6), updated_at DATETIME(6),
 active_payment_sentinel BIGINT GENERATED ALWAYS AS (CASE WHEN payment_status IN ('PENDING','APPROVED','IN_PROCESS') THEN order_id ELSE NULL END) VIRTUAL,
 CONSTRAINT fk_payment_order FOREIGN KEY(order_id) REFERENCES order_command(order_id),
 UNIQUE KEY uq_active_payment(active_payment_sentinel), UNIQUE KEY uq_mp_payment(mp_payment_id),
 CONSTRAINT chk_payment_amount CHECK(transaction_amount > 0), CONSTRAINT chk_payment_installments CHECK(installments > 0)
);
CREATE TABLE outbox_events (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, topic VARCHAR(255) NOT NULL, event_key VARCHAR(255) NOT NULL,
 event_type VARCHAR(255) NOT NULL, payload LONGTEXT NOT NULL, created_at DATETIME(6) NOT NULL, published_at DATETIME(6),
 INDEX idx_outbox_pending(published_at, id)
);