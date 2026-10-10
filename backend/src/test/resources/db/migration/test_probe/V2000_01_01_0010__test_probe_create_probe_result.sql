-- Test-only: the effect of the idempotent probe command, to count how many times it really ran.
CREATE TABLE test_probe.probe_result (
    id   uuid PRIMARY KEY,
    name text NOT NULL
);
