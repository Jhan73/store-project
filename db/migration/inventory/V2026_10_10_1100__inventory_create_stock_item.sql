CREATE SCHEMA inventory;
GRANT USAGE ON SCHEMA inventory TO app;

-- The product is a plain reference: no cross-schema foreign key. A row with tracked = false keeps its history
-- once a product stops being stock-tracked.
CREATE TABLE inventory.stock_item (
    product_id          uuid         PRIMARY KEY,
    tracked             boolean      NOT NULL DEFAULT true,
    on_hand             integer      NOT NULL DEFAULT 0,
    reserved            integer      NOT NULL DEFAULT 0,
    low_stock_threshold integer      NOT NULL DEFAULT 0,
    created_at          timestamptz  NOT NULL,
    updated_at          timestamptz  NOT NULL,
    version             bigint       NOT NULL DEFAULT 0,
    CONSTRAINT stock_item_on_hand_check CHECK (on_hand >= 0),
    CONSTRAINT stock_item_reserved_check CHECK (reserved >= 0),
    -- The backstop behind the conditional updates: reserved units are never sold.
    CONSTRAINT stock_item_reserved_within_on_hand_check CHECK (reserved <= on_hand),
    CONSTRAINT stock_item_low_stock_threshold_check CHECK (low_stock_threshold >= 0)
);
