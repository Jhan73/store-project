CREATE SCHEMA catalog;
GRANT USAGE ON SCHEMA catalog TO app;

CREATE TABLE catalog.station (
    id              uuid         PRIMARY KEY,
    name            varchar(80)  NOT NULL,
    default_station boolean      NOT NULL DEFAULT false,
    created_at      timestamptz  NOT NULL,
    updated_at      timestamptz  NOT NULL,
    version         bigint       NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX station_name_key ON catalog.station (lower(name));

-- At most one default station: the one a category lands on when its request names none.
CREATE UNIQUE INDEX station_default_key ON catalog.station (default_station) WHERE default_station;

INSERT INTO catalog.station (id, name, default_station, created_at, updated_at)
VALUES ('01999999-0000-7000-8000-000000000001', 'Main', true, now(), now());
