-- One row per day of week; closesAt < opensAt on the same row means the span crosses midnight.
CREATE TABLE store.opening_hour (
    id          uuid         PRIMARY KEY,
    day_of_week varchar(10)  NOT NULL,
    closed      boolean      NOT NULL DEFAULT false,
    opens_at    time,
    closes_at   time,
    version     bigint       NOT NULL DEFAULT 0,
    CONSTRAINT opening_hour_day_key UNIQUE (day_of_week),
    CONSTRAINT opening_hour_times_check CHECK (
        (closed AND opens_at IS NULL AND closes_at IS NULL)
        OR (NOT closed AND opens_at IS NOT NULL AND closes_at IS NOT NULL AND opens_at <> closes_at)
    )
);

INSERT INTO store.opening_hour (id, day_of_week, closed, opens_at, closes_at) VALUES
    ('0191f2c4-0000-7000-8000-000000000011', 'MONDAY', false, '08:00', '22:00'),
    ('0191f2c4-0000-7000-8000-000000000012', 'TUESDAY', false, '08:00', '22:00'),
    ('0191f2c4-0000-7000-8000-000000000013', 'WEDNESDAY', false, '08:00', '22:00'),
    ('0191f2c4-0000-7000-8000-000000000014', 'THURSDAY', false, '08:00', '22:00'),
    ('0191f2c4-0000-7000-8000-000000000015', 'FRIDAY', false, '08:00', '23:00'),
    ('0191f2c4-0000-7000-8000-000000000016', 'SATURDAY', false, '08:00', '23:00'),
    ('0191f2c4-0000-7000-8000-000000000017', 'SUNDAY', false, '08:00', '22:00');
