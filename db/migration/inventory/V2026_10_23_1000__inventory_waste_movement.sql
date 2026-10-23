-- Units wasted after the kitchen finished them leave the shelf as a WASTE movement, once per line (the reference).
ALTER TABLE inventory.stock_movement DROP CONSTRAINT stock_movement_type_check;
ALTER TABLE inventory.stock_movement ADD CONSTRAINT stock_movement_type_check
    CHECK (type IN ('ENTRY', 'ADJUSTMENT', 'SALE', 'RETURN', 'WASTE', 'RESERVE', 'COMMIT', 'RELEASE', 'EXPIRE'));

DROP INDEX inventory.stock_movement_effect_key;
CREATE UNIQUE INDEX stock_movement_effect_key ON inventory.stock_movement (type, reference_id, product_id)
    WHERE type IN ('SALE', 'RETURN', 'WASTE') AND reference_id IS NOT NULL;
