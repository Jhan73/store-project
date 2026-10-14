-- One row per register shift. The opened and closed halves are written by separate events that can arrive in any
-- order, so every column outside the key is nullable until its half arrives.
CREATE TABLE reporting.shift_summary (
    shift_id              uuid          PRIMARY KEY,
    cashier_id            uuid          NOT NULL,
    currency              char(3)       NOT NULL,
    opened_at             timestamptz,
    opening_float_amount  numeric(12,2) NOT NULL,
    closed_at             timestamptz,
    closed_by             uuid,
    forced                boolean,
    counted_amount        numeric(12,2),
    expected_amount       numeric(12,2),
    difference_amount     numeric(12,2),
    note                  varchar(280),
    cash_received_amount  numeric(12,2),
    change_given_amount   numeric(12,2),
    voided_cash_amount    numeric(12,2),
    cash_in_amount        numeric(12,2),
    cash_out_amount       numeric(12,2),
    by_method             jsonb
);

CREATE INDEX shift_summary_closed_idx ON reporting.shift_summary (closed_at DESC);

CREATE INDEX shift_summary_cashier_idx ON reporting.shift_summary (cashier_id, closed_at DESC);

-- The claim that makes the shift summary email go out once per shift.
CREATE TABLE reporting.shift_summary_email (
    shift_id uuid        PRIMARY KEY,
    sent_at  timestamptz NOT NULL
);
