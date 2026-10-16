-- A quick sale is a counter ticket paid upfront whose customer name is optional.
ALTER TABLE instore.ticket ADD COLUMN quick_sale boolean NOT NULL DEFAULT false;

ALTER TABLE instore.ticket DROP CONSTRAINT ticket_place_check;

ALTER TABLE instore.ticket ADD CONSTRAINT ticket_place_check CHECK (
    (type = 'TABLE' AND table_id IS NOT NULL AND counter_name IS NULL AND NOT quick_sale)
    OR (type = 'COUNTER' AND table_id IS NULL AND (counter_name IS NOT NULL OR quick_sale)));
