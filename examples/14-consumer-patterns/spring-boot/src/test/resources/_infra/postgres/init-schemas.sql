CREATE SCHEMA IF NOT EXISTS orders;

CREATE TABLE orders.orders (
    id          SERIAL PRIMARY KEY,
    customer_id VARCHAR(64)    NOT NULL,
    item_sku    VARCHAR(64)    NOT NULL,
    quantity    INTEGER        NOT NULL,
    amount      DECIMAL(12,2)  NOT NULL,
    status      VARCHAR(20)    NOT NULL DEFAULT 'PLACED',
    created_at  TIMESTAMP      NOT NULL DEFAULT NOW()
);
