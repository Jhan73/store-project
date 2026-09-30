CREATE TABLE catalog.modifier_group (
    id          uuid          PRIMARY KEY,
    name        varchar(120)  NOT NULL,
    required    boolean       NOT NULL,
    min_choices integer       NOT NULL,
    max_choices integer       NOT NULL,
    created_at  timestamptz   NOT NULL,
    updated_at  timestamptz   NOT NULL,
    version     bigint        NOT NULL DEFAULT 0,
    CONSTRAINT modifier_group_choices_check CHECK (
        min_choices >= 0 AND max_choices >= 1 AND min_choices <= max_choices
        AND ((required AND min_choices >= 1) OR (NOT required AND min_choices = 0))
    )
);

CREATE UNIQUE INDEX modifier_group_name_key ON catalog.modifier_group (lower(name));

CREATE TABLE catalog.modifier_option (
    id                    uuid          PRIMARY KEY,
    group_id              uuid          NOT NULL REFERENCES catalog.modifier_group (id) ON DELETE CASCADE,
    name                  varchar(120)  NOT NULL,
    price_delta_amount    numeric(12,2) NOT NULL,
    price_delta_currency  char(3)       NOT NULL,
    display_order         integer       NOT NULL,
    available             boolean       NOT NULL DEFAULT true,
    CONSTRAINT modifier_option_price_check CHECK (price_delta_amount >= 0)
);

-- Option names are unique within a group by domain rule, not by index: Hibernate flushes inserts before
-- deletes, so replacing an option by a new one with the same name would trip an index mid-flush.
CREATE INDEX modifier_option_group_idx ON catalog.modifier_option (group_id);

CREATE TABLE catalog.option_allergen (
    option_id     uuid         NOT NULL REFERENCES catalog.modifier_option (id) ON DELETE CASCADE,
    allergen_code varchar(30)  NOT NULL REFERENCES catalog.allergen (code),
    PRIMARY KEY (option_id, allergen_code)
);
