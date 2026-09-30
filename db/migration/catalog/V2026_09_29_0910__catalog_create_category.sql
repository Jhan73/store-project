CREATE TABLE catalog.category (
    id            uuid          PRIMARY KEY,
    station_id    uuid          NOT NULL REFERENCES catalog.station (id),
    name          varchar(120)  NOT NULL,
    display_order integer       NOT NULL DEFAULT 0,
    active        boolean       NOT NULL DEFAULT true,
    created_at    timestamptz   NOT NULL,
    updated_at    timestamptz   NOT NULL,
    version       bigint        NOT NULL DEFAULT 0,
    CONSTRAINT category_display_order_check CHECK (display_order >= 0)
);

CREATE UNIQUE INDEX category_name_key ON catalog.category (lower(name));

CREATE INDEX category_station_idx ON catalog.category (station_id);
