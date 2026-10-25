-- A retired station keeps its row, so categories and the board history that point at it stay readable.
ALTER TABLE catalog.station ADD COLUMN active boolean NOT NULL DEFAULT true;
