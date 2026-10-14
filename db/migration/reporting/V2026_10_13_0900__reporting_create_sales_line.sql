CREATE SCHEMA reporting;
GRANT USAGE ON SCHEMA reporting TO app;

-- One row per line of a recorded sale, and one negative row per line of a voided sale, so sums net out.
-- Day, hour and weekday are in the store time zone at the moment of the fact; names and prices are snapshots.
CREATE TABLE reporting.sales_line (
    id                uuid          PRIMARY KEY,
    source_id         uuid          NOT NULL,
    line_id           uuid          NOT NULL,
    ticket_id         uuid          NOT NULL,
    occurred_at       timestamptz   NOT NULL,
    business_date     date          NOT NULL,
    hour              smallint      NOT NULL,
    weekday           smallint      NOT NULL,
    channel           varchar(20)   NOT NULL,
    ticket_type       varchar(10)   NOT NULL,
    quick_sale        boolean       NOT NULL,
    product_id        uuid          NOT NULL,
    product_name      varchar(120)  NOT NULL,
    category_id       uuid,
    category_name     varchar(120),
    modifiers         jsonb         NOT NULL DEFAULT '[]'::jsonb,
    quantity          integer       NOT NULL,
    unit_price_amount numeric(12,2) NOT NULL,
    line_total_amount numeric(12,2) NOT NULL,
    currency          char(3)       NOT NULL,
    voided            boolean       NOT NULL DEFAULT false,
    comped            boolean       NOT NULL DEFAULT false,
    waste             boolean       NOT NULL DEFAULT false,
    refunded          boolean       NOT NULL DEFAULT false,
    employee_id       uuid,
    CONSTRAINT sales_line_hour_check CHECK (hour BETWEEN 0 AND 23),
    CONSTRAINT sales_line_weekday_check CHECK (weekday BETWEEN 1 AND 7),
    CONSTRAINT sales_line_channel_check CHECK (channel IN ('IN_STORE', 'ONLINE')),
    CONSTRAINT sales_line_ticket_type_check CHECK (ticket_type IN ('TABLE', 'COUNTER'))
);

-- The effect of a delivery: a redelivered event finds its rows already there.
CREATE UNIQUE INDEX sales_line_source_line_key ON reporting.sales_line (source_id, line_id);

CREATE INDEX sales_line_date_idx ON reporting.sales_line (business_date, weekday, hour);

CREATE INDEX sales_line_product_idx ON reporting.sales_line (product_id, business_date);
