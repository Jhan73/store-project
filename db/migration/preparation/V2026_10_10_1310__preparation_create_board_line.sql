-- source_line_id is the line's id in the owning module. modifier_signature is a hash of the sorted modifier option
-- ids, so the all-day view can tell identical items from look-alikes.
CREATE TABLE preparation.board_line (
    id                   uuid          PRIMARY KEY,
    board_order_id       uuid          NOT NULL REFERENCES preparation.board_order (id),
    source_line_id       uuid          NOT NULL,
    position             integer       NOT NULL,
    product_id          uuid          NOT NULL,
    display_name         varchar(200)  NOT NULL,
    quantity             integer       NOT NULL,
    modifiers_text       varchar(500),
    modifier_signature   varchar(64)   NOT NULL,
    note                 varchar(140),
    allergy              boolean       NOT NULL DEFAULT false,
    station_id           uuid,
    state                varchar(20)   NOT NULL,
    waste                boolean       NOT NULL DEFAULT false,
    flagged_unavailable  boolean       NOT NULL DEFAULT false,
    resolution           varchar(20),
    ready_at             timestamptz,
    created_at           timestamptz   NOT NULL,
    updated_at           timestamptz   NOT NULL,
    version              bigint        NOT NULL DEFAULT 0,
    CONSTRAINT board_line_quantity_check CHECK (quantity > 0),
    CONSTRAINT board_line_state_check CHECK (state IN ('PENDING', 'READY', 'VOIDED', 'COMPED')),
    CONSTRAINT board_line_waste_check CHECK (NOT waste OR state IN ('VOIDED', 'COMPED')),
    CONSTRAINT board_line_resolution_check CHECK (resolution IS NULL OR resolution IN ('VOIDED'))
);

-- A repeated send of the same line finds the existing row.
CREATE UNIQUE INDEX board_line_source_key ON preparation.board_line (board_order_id, source_line_id);

CREATE INDEX board_line_order_idx ON preparation.board_line (board_order_id);

-- Serves the all-day view, which only counts lines still to prepare.
CREATE INDEX board_line_pending_idx ON preparation.board_line (product_id, modifier_signature)
    WHERE state = 'PENDING';
