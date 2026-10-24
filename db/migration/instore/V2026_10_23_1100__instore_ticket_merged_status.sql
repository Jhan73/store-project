-- A ticket merged into another keeps its row and its history; its lines now belong to the surviving ticket.
ALTER TABLE instore.ticket DROP CONSTRAINT ticket_status_check;

ALTER TABLE instore.ticket ADD CONSTRAINT ticket_status_check CHECK (status IN ('OPEN', 'CLOSED', 'CANCELLED', 'MERGED'));

ALTER TABLE instore.ticket ADD COLUMN merged_into_id uuid REFERENCES instore.ticket (id);

ALTER TABLE instore.ticket ADD CONSTRAINT ticket_merged_check CHECK ((status = 'MERGED') = (merged_into_id IS NOT NULL));
