-- When true, an in-store sale closes even if the shelf cannot cover it and stock goes negative.
ALTER TABLE store.store_settings ADD COLUMN allow_negative_stock_on_sale boolean NOT NULL DEFAULT false;
