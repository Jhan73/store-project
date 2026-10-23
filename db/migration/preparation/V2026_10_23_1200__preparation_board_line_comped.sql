-- A comp is a billing decision: the kitchen still prepares the line, so it keeps its PENDING or READY state and is
-- flagged instead. Only a constraint is loosened and a column added, so an older image keeps working.
ALTER TABLE preparation.board_line
    ADD COLUMN comped boolean NOT NULL DEFAULT false;

UPDATE preparation.board_line SET state = 'READY', comped = true WHERE state = 'COMPED';

ALTER TABLE preparation.board_line DROP CONSTRAINT board_line_waste_check;

ALTER TABLE preparation.board_line
    ADD CONSTRAINT board_line_waste_check CHECK (NOT waste OR comped OR state = 'VOIDED');
