-- Idempotent Receiver: JDBC message ID repository
CREATE TABLE IF NOT EXISTS camel_messageprocessed (
    processorName VARCHAR(255) NOT NULL,
    messageId     VARCHAR(255) NOT NULL,
    createdAt     TIMESTAMP    NOT NULL DEFAULT NOW(),
    PRIMARY KEY (processorName, messageId)
);

-- Outbox Pattern: payments schema
CREATE SCHEMA IF NOT EXISTS payments;

CREATE TABLE payments.payments (
    id         SERIAL PRIMARY KEY,
    order_id   VARCHAR(64)    NOT NULL,
    amount     DECIMAL(12,2)  NOT NULL,
    status     VARCHAR(20)    NOT NULL DEFAULT 'PENDING',
    created_at TIMESTAMP      NOT NULL DEFAULT NOW()
);

CREATE TABLE payments.outbox (
    event_id     VARCHAR(255) PRIMARY KEY,
    event_type   VARCHAR(64)  NOT NULL,
    aggregate_id VARCHAR(64)  NOT NULL,
    payload      TEXT         NOT NULL,
    published    BOOLEAN      NOT NULL DEFAULT false,
    created_at   TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_outbox_unpublished
    ON payments.outbox (created_at)
    WHERE published = false;
