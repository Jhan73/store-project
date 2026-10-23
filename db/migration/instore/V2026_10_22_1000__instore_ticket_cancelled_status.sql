-- A ticket with nothing billed on it is cancelled, not closed as a sale: it frees its table and never reaches reports.
ALTER TABLE instore.ticket DROP CONSTRAINT ticket_status_check;

ALTER TABLE instore.ticket ADD CONSTRAINT ticket_status_check CHECK (status IN ('OPEN', 'CLOSED', 'CANCELLED'));
