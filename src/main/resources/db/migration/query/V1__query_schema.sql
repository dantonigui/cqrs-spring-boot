CREATE TABLE user_query (user_id BIGINT NOT NULL PRIMARY KEY, user_name VARCHAR(255) NOT NULL, user_email VARCHAR(255) NOT NULL, user_picture VARCHAR(255) NOT NULL, user_role VARCHAR(255) NOT NULL);
CREATE TABLE category_query (category_id BIGINT NOT NULL PRIMARY KEY, category_name VARCHAR(255));
CREATE TABLE product_query (product_id BIGINT NOT NULL PRIMARY KEY, product_name VARCHAR(255), product_code VARCHAR(255), product_price DECIMAL(38,2), product_image VARCHAR(255), category_id BIGINT, stock INT DEFAULT 0);
CREATE TABLE order_query (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, user_id BIGINT NOT NULL, status VARCHAR(255) NOT NULL,
 total_amount DECIMAL(10,2) NOT NULL, created_at DATETIME(6), updated_at DATETIME(6), UNIQUE KEY uq_order_id(order_id), INDEX idx_order_query_user(user_id)
);
CREATE TABLE order_item_query (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, product_id BIGINT NOT NULL,
 product_name VARCHAR(255) NOT NULL, unit_price DECIMAL(10,2) NOT NULL, quantity INT NOT NULL,
 CONSTRAINT fk_query_order_item FOREIGN KEY(order_id) REFERENCES order_query(order_id)
);
CREATE TABLE payment_query (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, order_id BIGINT NOT NULL, payment_method VARCHAR(30) NOT NULL,
 payment_type VARCHAR(255) NOT NULL, payment_status VARCHAR(30) NOT NULL, mp_payment_id VARCHAR(100), transaction_amount DECIMAL(10,2) NOT NULL,
 installments INT NOT NULL, card_last_four VARCHAR(4), card_brand VARCHAR(20), in_person_method VARCHAR(30), created_at DATETIME(6),
 UNIQUE KEY uq_query_payment(order_id, mp_payment_id)
);
CREATE TABLE processed_events (
 id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY, event_id VARCHAR(255) NOT NULL, topic VARCHAR(255) NOT NULL,
 status VARCHAR(255) NOT NULL, claimed_at DATETIME(6) NOT NULL, updated_at DATETIME(6), retry_count INT NOT NULL, error_message VARCHAR(2000),
 UNIQUE KEY uq_event_topic(event_id, topic)
);