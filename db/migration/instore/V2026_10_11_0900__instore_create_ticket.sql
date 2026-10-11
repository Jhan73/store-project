CREATE SEQUENCE instore.ticket_number_seq;

-- board_source_id is the card the ticket currently feeds on the board: the ticket's own id for the first round, a new
-- id for each round opened once the previous card was handed over or cancelled.
CREATE TABLE instore.ticket (
    id               uuid          PRIMARY KEY,
    number           bigint        NOT NULL DEFAULT nextval('instore.ticket_number_seq'),
    type             varchar(10)   NOT NULL,
    table_id         uuid          REFERENCES instore.dining_table (id),
    counter_name     varchar(100),
    note             varchar(140),
    status           varchar(10)   NOT NULL,
    bill_requested   boolean       NOT NULL DEFAULT false,
    board_source_id  uuid,
    board_rounds     integer       NOT NULL DEFAULT 0,
    currency         char(3)       NOT NULL,
    opened_by        uuid          NOT NULL,
    closed_at        timestamptz,
    created_at       timestamptz   NOT NULL,
    updated_at       timestamptz   NOT NULL,
    version          bigint        NOT NULL DEFAULT 0,
    CONSTRAINT ticket_type_check CHECK (type IN ('TABLE', 'COUNTER')),
    CONSTRAINT ticket_status_check CHECK (status IN ('OPEN', 'CLOSED', 'MERGED')),
    CONSTRAINT ticket_place_check CHECK (
        (type = 'TABLE' AND table_id IS NOT NULL AND counter_name IS NULL)
        OR (type = 'COUNTER' AND table_id IS NULL AND counter_name IS NOT NULL)),
    CONSTRAINT ticket_rounds_check CHECK (board_rounds >= 0)
);

-- One open ticket per table: the arbiter of two staff members opening or transferring onto the same table.
CREATE UNIQUE INDEX ticket_open_table_key ON instore.ticket (table_id) WHERE status = 'OPEN';

CREATE INDEX ticket_open_idx ON instore.ticket (created_at) WHERE status = 'OPEN';

-- A line is a snapshot: the product name, the chosen options with their price deltas and the unit price, so a later
-- catalog change never reaches an existing ticket. board_source_id is the card the line was sent to.
CREATE TABLE instore.ticket_line (
    id                   uuid          PRIMARY KEY,
    ticket_id            uuid          NOT NULL REFERENCES instore.ticket (id),
    position             integer       NOT NULL,
    product_id           uuid          NOT NULL,
    product_name         varchar(200)  NOT NULL,
    station_id           uuid          NOT NULL,
    quantity             integer       NOT NULL,
    unit_price_amount    numeric(12,2) NOT NULL,
    unit_price_currency  char(3)       NOT NULL,
    modifiers            jsonb         NOT NULL DEFAULT '[]',
    note                 varchar(140),
    allergy              boolean       NOT NULL DEFAULT false,
    state                varchar(10)   NOT NULL,
    board_source_id      uuid,
    sent_at              timestamptz,
    waste                boolean       NOT NULL DEFAULT false,
    void_reason_code     varchar(60),
    void_note            varchar(280),
    voided_by            uuid,
    voided_at            timestamptz,
    created_at           timestamptz   NOT NULL,
    updated_at           timestamptz   NOT NULL,
    version              bigint        NOT NULL DEFAULT 0,
    CONSTRAINT ticket_line_quantity_check CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT ticket_line_state_check CHECK (state IN ('UNSENT', 'SENT', 'VOIDED', 'COMPED')),
    CONSTRAINT ticket_line_sent_check CHECK (state = 'UNSENT' OR board_source_id IS NOT NULL),
    CONSTRAINT ticket_line_waste_check CHECK (NOT waste OR state IN ('VOIDED', 'COMPED'))
);

CREATE INDEX ticket_line_ticket_idx ON instore.ticket_line (ticket_id, position);
