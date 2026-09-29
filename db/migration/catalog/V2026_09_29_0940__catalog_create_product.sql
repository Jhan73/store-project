CREATE TABLE catalog.product (
    id                uuid          PRIMARY KEY,
    category_id       uuid          NOT NULL REFERENCES catalog.category (id),
    name              varchar(120)  NOT NULL,
    description       varchar(500),
    price_amount      numeric(12,2) NOT NULL,
    price_currency    char(3)       NOT NULL,
    display_order     integer       NOT NULL DEFAULT 0,
    active            boolean       NOT NULL DEFAULT true,
    available         boolean       NOT NULL DEFAULT true,
    quick_sale_pinned boolean       NOT NULL DEFAULT false,
    image_key         varchar(200),
    created_at        timestamptz   NOT NULL,
    updated_at        timestamptz   NOT NULL,
    version           bigint        NOT NULL DEFAULT 0,
    CONSTRAINT product_price_check CHECK (price_amount > 0),
    CONSTRAINT product_display_order_check CHECK (display_order >= 0)
);

CREATE UNIQUE INDEX product_category_name_key ON catalog.product (category_id, lower(name));

CREATE INDEX product_category_idx ON catalog.product (category_id);

CREATE TABLE catalog.product_allergen (
    product_id    uuid         NOT NULL REFERENCES catalog.product (id) ON DELETE CASCADE,
    allergen_code varchar(30)  NOT NULL REFERENCES catalog.allergen (code),
    PRIMARY KEY (product_id, allergen_code)
);

-- Keyed by position, not by group: Hibernate reorders an ordered collection with in-place updates, which
-- would trip a key on (product_id, group_id) halfway. The service rejects a group listed twice.
CREATE TABLE catalog.product_modifier_group (
    product_id    uuid     NOT NULL REFERENCES catalog.product (id) ON DELETE CASCADE,
    display_order integer  NOT NULL,
    group_id      uuid     NOT NULL REFERENCES catalog.modifier_group (id),
    PRIMARY KEY (product_id, display_order)
);

CREATE INDEX product_modifier_group_group_idx ON catalog.product_modifier_group (group_id);
