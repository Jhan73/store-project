-- A name of use shown to people, never a credential: sign-in stays by email. Nullable because accounts created
-- before it existed have none until an admin names them.
ALTER TABLE identity.user_account ADD COLUMN username varchar(60);

ALTER TABLE identity.user_account ADD CONSTRAINT user_account_username_not_blank
    CHECK (username IS NULL OR (length(btrim(username)) > 0 AND username = btrim(username)));

-- Case-insensitive and only among active accounts: a deactivated account frees its name.
CREATE UNIQUE INDEX user_account_username_active_key ON identity.user_account (lower(username))
    WHERE active AND username IS NOT NULL;
