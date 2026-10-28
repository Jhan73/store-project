-- The check forbids a provider on other methods only: an image from before this change inserts wallet payments
-- without one and must keep working, so the service is what requires the provider for a wallet payment.
ALTER TABLE instore.in_store_payment
    ADD COLUMN wallet_provider_id   uuid,
    ADD COLUMN wallet_provider_name varchar(60);

ALTER TABLE instore.in_store_payment
    ADD CONSTRAINT in_store_payment_wallet_provider_check CHECK (
        method = 'WALLET' OR (wallet_provider_id IS NULL AND wallet_provider_name IS NULL));
