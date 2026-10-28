-- A retired provider keeps its row; payments store its id and a copy of its name, with no foreign key.
CREATE TABLE instore.wallet_provider (
    id          uuid         PRIMARY KEY,
    name        varchar(60)  NOT NULL,
    active      boolean      NOT NULL DEFAULT true,
    created_at  timestamptz  NOT NULL,
    updated_at  timestamptz  NOT NULL,
    version     bigint       NOT NULL DEFAULT 0,
    CONSTRAINT wallet_provider_name_check CHECK (name = btrim(name) AND name <> '')
);

CREATE UNIQUE INDEX wallet_provider_active_name_key ON instore.wallet_provider (lower(name)) WHERE active;

INSERT INTO instore.wallet_provider (id, name, created_at, updated_at) VALUES
    ('01999999-0000-7000-8000-000000000101', 'Yape', now(), now()),
    ('01999999-0000-7000-8000-000000000102', 'Plin', now(), now());
