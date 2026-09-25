-- Test-only schema for shared persistence tests; src/test/resources is never packaged.
CREATE SCHEMA test_probe;
GRANT USAGE ON SCHEMA test_probe TO app;

CREATE TABLE test_probe.probe (
    id             uuid          PRIMARY KEY,
    price_amount   numeric(12,2) NOT NULL,
    price_currency char(3)       NOT NULL
);
