-- Cash in or out of the drawer during a shift. No foreign key to the shift, so the append-only rows never constrain it.
CREATE TABLE instore.cash_movement (
    id           uuid          PRIMARY KEY,
    shift_id     uuid          NOT NULL,
    type         varchar(3)    NOT NULL,
    currency     char(3)       NOT NULL,
    amount       numeric(12,2) NOT NULL,
    reason_code  varchar(60),
    note         varchar(280),
    reference    varchar(100),
    recorded_by  uuid          NOT NULL,
    recorded_at  timestamptz   NOT NULL,
    CONSTRAINT cash_movement_type_check CHECK (type IN ('IN', 'OUT')),
    CONSTRAINT cash_movement_amount_check CHECK (amount > 0),
    CONSTRAINT cash_movement_reason_check CHECK (
        (type = 'OUT' AND reason_code IS NOT NULL) OR (type = 'IN' AND note IS NOT NULL))
);

CREATE INDEX cash_movement_shift_idx ON instore.cash_movement (shift_id, recorded_at, id);

REVOKE UPDATE, DELETE, TRUNCATE ON instore.cash_movement FROM app;
