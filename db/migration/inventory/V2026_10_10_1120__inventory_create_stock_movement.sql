-- The ledger: on_hand is the sum of on_hand_delta and reserved the sum of reserved_delta. No foreign key to
-- stock_item, so the append-only rows never constrain it.
CREATE TABLE inventory.stock_movement (
    id             uuid         PRIMARY KEY,
    product_id     uuid         NOT NULL,
    type           varchar(20)  NOT NULL,
    on_hand_delta  integer      NOT NULL,
    reserved_delta integer      NOT NULL,
    reference_id   uuid,
    reason_code    varchar(60),
    note           varchar(300),
    actor_id       uuid,
    actor_role     varchar(20),
    occurred_at    timestamptz  NOT NULL,
    CONSTRAINT stock_movement_type_check
        CHECK (type IN ('ENTRY', 'ADJUSTMENT', 'SALE', 'RETURN', 'RESERVE', 'COMMIT', 'RELEASE', 'EXPIRE')),
    CONSTRAINT stock_movement_changes_stock_check CHECK (on_hand_delta <> 0 OR reserved_delta <> 0)
);

CREATE INDEX stock_movement_product_idx ON inventory.stock_movement (product_id, occurred_at DESC, id DESC);

-- A sale or a return is applied once per reference and product, however often its event is delivered.
CREATE UNIQUE INDEX stock_movement_effect_key ON inventory.stock_movement (type, reference_id, product_id)
    WHERE type IN ('SALE', 'RETURN') AND reference_id IS NOT NULL;

REVOKE UPDATE, DELETE, TRUNCATE ON inventory.stock_movement FROM app;
