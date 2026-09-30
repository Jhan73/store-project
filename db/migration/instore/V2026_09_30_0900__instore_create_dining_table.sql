CREATE SCHEMA instore;
GRANT USAGE ON SCHEMA instore TO app;

CREATE TABLE instore.dining_table (
    id            uuid         PRIMARY KEY,
    name          varchar(60)  NOT NULL,
    area          varchar(60),
    display_order integer      NOT NULL DEFAULT 0,
    active        boolean      NOT NULL DEFAULT true,
    created_at    timestamptz  NOT NULL,
    updated_at    timestamptz  NOT NULL,
    version       bigint       NOT NULL DEFAULT 0,
    CONSTRAINT dining_table_display_order_check CHECK (display_order >= 0)
);

CREATE UNIQUE INDEX dining_table_name_key ON instore.dining_table (lower(name));
