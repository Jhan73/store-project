-- Things staff did that the owner wants to watch: voids, waste, cash out and register differences.
-- source_id is the ID of the thing that happened (line, sale void, payment, cash movement, shift).
CREATE TABLE reporting.exception_event (
    id            uuid          PRIMARY KEY,
    type          varchar(20)   NOT NULL,
    source_id     uuid          NOT NULL,
    occurred_at   timestamptz   NOT NULL,
    business_date date          NOT NULL,
    employee_id   uuid,
    reason_code   varchar(60),
    note          varchar(280),
    amount_amount numeric(12,2),
    currency      char(3),
    ticket_id     uuid,
    shift_id      uuid,
    CONSTRAINT exception_event_type_check
        CHECK (type IN ('LINE_VOID', 'WASTE', 'SALE_VOID', 'PAYMENT_VOID', 'CASH_OUT', 'SHIFT_DIFFERENCE')),
    CONSTRAINT exception_event_amount_check CHECK ((amount_amount IS NULL) = (currency IS NULL))
);

CREATE UNIQUE INDEX exception_event_type_source_key ON reporting.exception_event (type, source_id);

CREATE INDEX exception_event_employee_idx ON reporting.exception_event (employee_id, business_date);
