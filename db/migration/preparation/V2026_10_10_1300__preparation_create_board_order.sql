CREATE SCHEMA preparation;
GRANT USAGE ON SCHEMA preparation TO app;

-- One card on the board. source + source_id is the owning order or ticket in another module: a plain reference,
-- no cross-schema foreign key. status is the arbiter of the races between staff and the sales channels.
CREATE TABLE preparation.board_order (
    id               uuid         PRIMARY KEY,
    source           varchar(20)  NOT NULL,
    source_id        uuid         NOT NULL,
    short_number     varchar(20)  NOT NULL,
    channel          varchar(20)  NOT NULL,
    label            varchar(100),
    status           varchar(20)  NOT NULL,
    fire_at          timestamptz,
    sent_at          timestamptz  NOT NULL,
    started_at       timestamptz,
    ready_at         timestamptz,
    handed_over_at   timestamptz,
    cancelled_at     timestamptz,
    created_at       timestamptz  NOT NULL,
    updated_at       timestamptz  NOT NULL,
    version          bigint       NOT NULL DEFAULT 0,
    CONSTRAINT board_order_source_check CHECK (source IN ('ONLINE', 'TICKET', 'QUICK_SALE')),
    CONSTRAINT board_order_channel_check CHECK (channel IN ('TABLE', 'COUNTER', 'PICKUP', 'DELIVERY')),
    CONSTRAINT board_order_status_check
        CHECK (status IN ('WAITING', 'PREPARING', 'READY', 'HANDED_OVER', 'CANCELLED'))
);

-- Handing the same order or ticket over twice finds the existing card.
CREATE UNIQUE INDEX board_order_source_key ON preparation.board_order (source, source_id);

-- Serves the live views, which only look at cards that are not finished.
CREATE INDEX board_order_live_idx ON preparation.board_order (status, sent_at)
    WHERE status IN ('WAITING', 'PREPARING', 'READY');
