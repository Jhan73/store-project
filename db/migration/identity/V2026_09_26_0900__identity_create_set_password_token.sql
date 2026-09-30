CREATE TABLE identity.set_password_token (
    id           uuid          PRIMARY KEY,
    user_id      uuid          NOT NULL REFERENCES identity.user_account (id),
    token_hash   varchar(64)   NOT NULL,
    issued_at    timestamptz   NOT NULL,
    expires_at   timestamptz   NOT NULL,
    used_at      timestamptz,
    revoked_at   timestamptz
);

-- Looked up on every set-password attempt; never store the raw token, only this hash.
CREATE UNIQUE INDEX set_password_token_token_hash_key ON identity.set_password_token (token_hash);
-- A new token invalidates every earlier unused token of the same user in one statement.
CREATE INDEX set_password_token_user_id_idx ON identity.set_password_token (user_id);
