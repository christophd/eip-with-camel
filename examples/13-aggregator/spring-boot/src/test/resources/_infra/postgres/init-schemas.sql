CREATE TABLE camel_aggregation (
    id       VARCHAR(255) NOT NULL PRIMARY KEY,
    exchange BYTEA        NOT NULL,
    version  BIGINT       NOT NULL
);

CREATE TABLE camel_aggregation_completed (
    id       VARCHAR(255) NOT NULL PRIMARY KEY,
    exchange BYTEA        NOT NULL,
    version  BIGINT       NOT NULL
);
