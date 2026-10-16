CREATE SCHEMA shared;
GRANT USAGE ON SCHEMA shared TO app;

-- The response columns stay null between registering the key and storing the result, inside one transaction.
CREATE TABLE shared.idempotency_key (
    actor_id        uuid         NOT NULL,
    key             uuid         NOT NULL,
    request_hash    char(64)     NOT NULL,
    response_status integer,
    response_body   text,
    created_at      timestamptz  NOT NULL,
    expires_at      timestamptz  NOT NULL,
    PRIMARY KEY (actor_id, key)
);

CREATE INDEX idempotency_key_expires_at_idx ON shared.idempotency_key (expires_at);
