-- Schema base do meli-orders-service

CREATE TABLE inventory
(
    product_id VARCHAR(100) PRIMARY KEY,
    name       VARCHAR(255) NOT NULL,
    quantity   INTEGER      NOT NULL
);

CREATE TABLE orders
(
    id           BIGSERIAL PRIMARY KEY,
    customer_id  VARCHAR(100)   NOT NULL,
    status       VARCHAR(50)    NOT NULL,
    total_amount NUMERIC(10, 2) NOT NULL,
    created_at   TIMESTAMP      NOT NULL
);

CREATE TABLE order_items
(
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT         NOT NULL REFERENCES orders (id),
    product_id   VARCHAR(100)   NOT NULL,
    product_name VARCHAR(255),
    quantity     INTEGER        NOT NULL,
    unit_price   NUMERIC(10, 2) NOT NULL
);

CREATE INDEX idx_orders_customer_id ON orders (customer_id);
CREATE INDEX idx_order_items_order_id ON order_items (order_id);

