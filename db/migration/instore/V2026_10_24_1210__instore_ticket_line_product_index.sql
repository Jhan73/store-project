-- Serves the check that a product no ticket line refers to can be deleted.
CREATE INDEX ticket_line_product_idx ON instore.ticket_line (product_id);
