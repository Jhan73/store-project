-- A cashier's working session. counted_amount is stored while the shift is still open once the cashier asked to close
-- over the threshold without a note: the count was revealed to them, so it can no longer change.
CREATE TABLE instore.register_shift (
    id                 uuid          PRIMARY KEY,
    cashier_id         uuid          NOT NULL,
    status             varchar(10)   NOT NULL,
    currency           char(3)       NOT NULL,
    float_amount       numeric(12,2) NOT NULL,
    opened_at          timestamptz   NOT NULL,
    counted_amount     numeric(12,2),
    counted_at         timestamptz,
    expected_amount    numeric(12,2),
    difference_amount  numeric(12,2),
    difference_note    varchar(280),
    close_kind         varchar(10),
    force_reason       varchar(280),
    closed_by          uuid,
    closed_at          timestamptz,
    totals             jsonb,
    updated_at         timestamptz   NOT NULL,
    version            bigint        NOT NULL DEFAULT 0,
    CONSTRAINT register_shift_status_check CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT register_shift_close_kind_check CHECK (close_kind IS NULL OR close_kind IN ('COUNTED', 'FORCED')),
    CONSTRAINT register_shift_amounts_check CHECK (float_amount >= 0 AND (counted_amount IS NULL OR counted_amount >= 0)),
    CONSTRAINT register_shift_closure_check CHECK (
        (status = 'OPEN' AND close_kind IS NULL AND closed_at IS NULL AND closed_by IS NULL AND expected_amount IS NULL
            AND totals IS NULL)
        OR (status = 'CLOSED' AND closed_at IS NOT NULL AND closed_by IS NOT NULL AND expected_amount IS NOT NULL
            AND totals IS NOT NULL
            AND ((close_kind = 'COUNTED' AND counted_amount IS NOT NULL AND difference_amount IS NOT NULL)
                OR (close_kind = 'FORCED' AND force_reason IS NOT NULL))))
);

-- One open shift per cashier: the arbiter of two devices opening a shift at once.
CREATE UNIQUE INDEX register_shift_open_key ON instore.register_shift (cashier_id) WHERE status = 'OPEN';

CREATE INDEX register_shift_opened_idx ON instore.register_shift (opened_at DESC, id DESC);
