CREATE TABLE store.delivery_zone (
    id                                uuid          PRIMARY KEY,
    name                              varchar(120)  NOT NULL,
    fee_amount                        numeric(12,2) NOT NULL,
    fee_currency                      char(3)       NOT NULL,
    delivery_minutes                  integer       NOT NULL,
    minimum_order_amount              numeric(12,2),
    minimum_order_currency            char(3),
    free_delivery_threshold_amount    numeric(12,2),
    free_delivery_threshold_currency  char(3),
    active                            boolean       NOT NULL DEFAULT true,
    created_at                        timestamptz   NOT NULL,
    updated_at                        timestamptz   NOT NULL,
    version                           bigint        NOT NULL DEFAULT 0,
    CONSTRAINT delivery_zone_fee_check CHECK (fee_amount >= 0 AND delivery_minutes > 0),
    CONSTRAINT delivery_zone_minimum_order_check CHECK (minimum_order_amount IS NULL OR minimum_order_amount >= 0),
    CONSTRAINT delivery_zone_free_threshold_check
        CHECK (free_delivery_threshold_amount IS NULL OR free_delivery_threshold_amount >= 0)
);

-- A deactivated zone frees its name for reuse.
CREATE UNIQUE INDEX delivery_zone_active_name_key ON store.delivery_zone (lower(name)) WHERE active;
