CREATE SCHEMA IF NOT EXISTS system;

CREATE TABLE IF NOT EXISTS system.message_store (
    message_id VARCHAR(255) NOT NULL PRIMARY KEY,
    route_id   VARCHAR(255),
    timestamp  TIMESTAMP    DEFAULT NOW(),
    payload    TEXT
);
