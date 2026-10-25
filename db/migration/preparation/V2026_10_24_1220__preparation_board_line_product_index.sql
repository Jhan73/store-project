-- Serves the check that a product no board card refers to can be deleted; the existing index covers pending lines only.
CREATE INDEX board_line_product_idx ON preparation.board_line (product_id);
