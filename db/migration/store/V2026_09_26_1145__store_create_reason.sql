CREATE TABLE store.reason (
    id         uuid         PRIMARY KEY,
    type       varchar(20)  NOT NULL,
    code       varchar(60)  NOT NULL,
    active     boolean      NOT NULL DEFAULT true,
    created_at timestamptz  NOT NULL,
    updated_at timestamptz  NOT NULL,
    version    bigint       NOT NULL DEFAULT 0
);

-- Unconditional (not WHERE active): a deactivated code stays reserved so historical entries referencing it stay unambiguous.
CREATE UNIQUE INDEX reason_type_code_key ON store.reason (type, lower(code));
