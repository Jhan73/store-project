-- Serves the check that a product no stock reservation refers to can be deleted.
CREATE INDEX stock_reservation_product_idx ON inventory.stock_reservation (product_id);
