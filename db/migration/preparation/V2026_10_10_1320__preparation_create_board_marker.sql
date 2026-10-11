-- NEW / MODIFIED / VOID labels that stay on the board until a staff member acknowledges them. line_id is null for
-- a marker about the whole order.
CREATE TABLE preparation.board_marker (
    id               uuid         PRIMARY KEY,
    board_order_id   uuid         NOT NULL REFERENCES preparation.board_order (id),
    line_id          uuid         REFERENCES preparation.board_line (id),
    kind             varchar(20)  NOT NULL,
    created_at       timestamptz  NOT NULL,
    acknowledged_by  uuid,
    acknowledged_at  timestamptz,
    CONSTRAINT board_marker_kind_check CHECK (kind IN ('NEW', 'MODIFIED', 'VOID')),
    CONSTRAINT board_marker_acknowledged_check CHECK (acknowledged_by IS NULL OR acknowledged_at IS NOT NULL)
);

-- Serves the lookup of what is still unacknowledged on a card.
CREATE INDEX board_marker_open_idx ON preparation.board_marker (board_order_id) WHERE acknowledged_at IS NULL;
