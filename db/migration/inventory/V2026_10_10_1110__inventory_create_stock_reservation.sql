-- One row per reference (an online order) and product. A released or expired row stays as history, so a
-- reference can reserve the same product again.
CREATE TABLE inventory.stock_reservation (
    id           uuid         PRIMARY KEY,
    reference_id uuid         NOT NULL,
    product_id   uuid         NOT NULL,
    quantity     integer      NOT NULL,
    status       varchar(20)  NOT NULL,
    expires_at   timestamptz  NOT NULL,
    created_at   timestamptz  NOT NULL,
    updated_at   timestamptz  NOT NULL,
    CONSTRAINT stock_reservation_quantity_check CHECK (quantity > 0),
    CONSTRAINT stock_reservation_status_check CHECK (status IN ('RESERVED', 'COMMITTED', 'RELEASED', 'EXPIRED'))
);

-- A repeated reserve for the same reference and product finds the live row and does nothing.
CREATE UNIQUE INDEX stock_reservation_live_key ON inventory.stock_reservation (reference_id, product_id)
    WHERE status IN ('RESERVED', 'COMMITTED');

CREATE INDEX stock_reservation_reference_idx ON inventory.stock_reservation (reference_id);

-- Serves the expiry sweep, which only looks at reservations still holding stock.
CREATE INDEX stock_reservation_due_idx ON inventory.stock_reservation (expires_at) WHERE status = 'RESERVED';
