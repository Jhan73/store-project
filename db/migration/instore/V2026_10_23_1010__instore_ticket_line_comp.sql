-- A comped line was served free of charge: it is not charged and counts as waste. Only constraints are loosened and
-- columns added, so an older image keeps working until it reads a comped row.
ALTER TABLE instore.ticket_line DROP CONSTRAINT ticket_line_state_check;

ALTER TABLE instore.ticket_line ADD CONSTRAINT ticket_line_state_check
    CHECK (state IN ('UNSENT', 'SENT', 'VOIDED', 'COMPED'));

ALTER TABLE instore.ticket_line DROP CONSTRAINT ticket_line_waste_check;

ALTER TABLE instore.ticket_line ADD CONSTRAINT ticket_line_waste_check
    CHECK (NOT waste OR state IN ('VOIDED', 'COMPED'));

ALTER TABLE instore.ticket_line
    ADD COLUMN comp_reason_code varchar(60),
    ADD COLUMN comp_note        varchar(280),
    ADD COLUMN comped_by        uuid,
    ADD COLUMN comped_at        timestamptz;
