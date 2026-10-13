-- Payments are never updated: a correction is a payment_void row. None of these tables has a foreign key, so the
-- append-only rows never constrain the tickets and shifts they point at.
-- paid_amount is what the payment settles on the bill; for cash received_amount is what the customer handed over.
CREATE TABLE instore.in_store_payment (
    id               uuid          PRIMARY KEY,
    ticket_id        uuid          NOT NULL,
    shift_id         uuid          NOT NULL,
    method           varchar(10)   NOT NULL,
    currency         char(3)       NOT NULL,
    paid_amount      numeric(12,2) NOT NULL,
    received_amount  numeric(12,2),
    change_amount    numeric(12,2) NOT NULL DEFAULT 0,
    recorded_by      uuid          NOT NULL,
    recorded_at      timestamptz   NOT NULL,
    CONSTRAINT in_store_payment_method_check CHECK (method IN ('CASH', 'CARD', 'WALLET', 'TRANSFER')),
    CONSTRAINT in_store_payment_amount_check CHECK (paid_amount > 0),
    CONSTRAINT in_store_payment_cash_check CHECK (
        (method = 'CASH' AND received_amount IS NOT NULL AND change_amount >= 0
            AND received_amount - paid_amount = change_amount)
        OR (method <> 'CASH' AND received_amount IS NULL AND change_amount = 0))
);

CREATE INDEX in_store_payment_ticket_idx ON instore.in_store_payment (ticket_id, recorded_at, id);
CREATE INDEX in_store_payment_shift_idx ON instore.in_store_payment (shift_id);

-- The lines a payment settles, when the bill is split by items.
CREATE TABLE instore.payment_line (
    payment_id  uuid NOT NULL,
    line_id     uuid NOT NULL,
    PRIMARY KEY (payment_id, line_id)
);

CREATE INDEX payment_line_line_idx ON instore.payment_line (line_id);

-- shift_id is the shift whose drawer paid the money back; null when the voider had no open shift (an ADMIN).
CREATE TABLE instore.payment_void (
    id           uuid         PRIMARY KEY,
    payment_id   uuid         NOT NULL,
    ticket_id    uuid         NOT NULL,
    shift_id     uuid,
    sale_void_id uuid,
    reason_code  varchar(60)  NOT NULL,
    note         varchar(280),
    voided_by    uuid         NOT NULL,
    voided_at    timestamptz  NOT NULL
);

-- A payment is voided once, however often the command is retried.
CREATE UNIQUE INDEX payment_void_payment_key ON instore.payment_void (payment_id);
CREATE INDEX payment_void_ticket_idx ON instore.payment_void (ticket_id);
CREATE INDEX payment_void_shift_idx ON instore.payment_void (shift_id) WHERE shift_id IS NOT NULL;

-- A recorded sale is voided once.
CREATE TABLE instore.sale_void (
    id           uuid         PRIMARY KEY,
    ticket_id    uuid         NOT NULL,
    reason_code  varchar(60)  NOT NULL,
    note         varchar(280),
    voided_by    uuid         NOT NULL,
    voided_at    timestamptz  NOT NULL
);

CREATE UNIQUE INDEX sale_void_ticket_key ON instore.sale_void (ticket_id);

REVOKE UPDATE, DELETE, TRUNCATE ON instore.in_store_payment FROM app;
REVOKE UPDATE, DELETE, TRUNCATE ON instore.payment_line FROM app;
REVOKE UPDATE, DELETE, TRUNCATE ON instore.payment_void FROM app;
REVOKE UPDATE, DELETE, TRUNCATE ON instore.sale_void FROM app;
