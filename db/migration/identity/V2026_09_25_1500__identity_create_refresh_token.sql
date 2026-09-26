CREATE TABLE identity.refresh_token (
    id                  uuid          PRIMARY KEY,
    family_id           uuid          NOT NULL,
    user_id             uuid          NOT NULL REFERENCES identity.user_account (id),
    token_hash          varchar(64)   NOT NULL,
    issued_at           timestamptz   NOT NULL,
    expires_at          timestamptz   NOT NULL,
    -- Shared by every token in a family; bounds the family's absolute lifetime regardless of rotation.
    family_started_at   timestamptz   NOT NULL,
    used_at             timestamptz,
    revoked_at          timestamptz
);

-- Looked up on every refresh; never store the raw token, only this hash.
CREATE UNIQUE INDEX refresh_token_token_hash_key ON identity.refresh_token (token_hash);
-- Reuse detection revokes every token sharing a family in one statement.
CREATE INDEX refresh_token_family_id_idx ON identity.refresh_token (family_id);
-- Deactivation revokes every token of a user in one statement.
CREATE INDEX refresh_token_user_id_idx ON identity.refresh_token (user_id);
