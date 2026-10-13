-- A store may sell what the shelf cannot cover (store.store_settings.allow_negative_stock_on_sale), so on hand can go
-- below zero. Reserved units still never exceed what is on hand, and none can be reserved while it is not positive.
ALTER TABLE inventory.stock_item DROP CONSTRAINT stock_item_on_hand_check;
ALTER TABLE inventory.stock_item DROP CONSTRAINT stock_item_reserved_within_on_hand_check;
ALTER TABLE inventory.stock_item ADD CONSTRAINT stock_item_reserved_within_on_hand_check
    CHECK (reserved <= greatest(on_hand, 0));
