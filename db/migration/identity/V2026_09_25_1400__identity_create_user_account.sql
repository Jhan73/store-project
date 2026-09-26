CREATE SCHEMA identity;
GRANT USAGE ON SCHEMA identity TO app;

CREATE TABLE identity.user_account (
    id              uuid          PRIMARY KEY,
    email           varchar(320)  NOT NULL,
    password_hash   varchar(255)  NOT NULL,
    role            varchar(20)   NOT NULL,
    active          boolean       NOT NULL DEFAULT true,
    failed_attempts integer       NOT NULL DEFAULT 0,
    locked_until    timestamptz,
    created_at      timestamptz   NOT NULL,
    updated_at      timestamptz   NOT NULL,
    version         bigint        NOT NULL DEFAULT 0
);

-- Case-insensitive: two accounts differing only by case would otherwise both match at login.
CREATE UNIQUE INDEX user_account_email_key ON identity.user_account (lower(email));
