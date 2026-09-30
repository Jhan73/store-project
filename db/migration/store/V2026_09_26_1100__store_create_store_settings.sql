CREATE SCHEMA store;
GRANT USAGE ON SCHEMA store TO app;

-- Singleton row: singleton_guard is always true and unique, so a second row can never be inserted.
-- opening_hours_version is its own counter (not the row's own version) since opening_hour is a separate resource.
CREATE TABLE store.store_settings (
    id                                      uuid          PRIMARY KEY,
    time_zone                               varchar(50)   NOT NULL,
    currency                                char(3)       NOT NULL,
    base_prep_minutes                       integer       NOT NULL,
    queue_minutes_per_order                 integer       NOT NULL,
    busy_mode_minutes                       integer       NOT NULL,
    board_warning_minutes                   integer       NOT NULL,
    board_late_minutes                      integer       NOT NULL,
    register_difference_threshold_amount    numeric(12,2) NOT NULL,
    register_difference_threshold_currency  char(3)       NOT NULL,
    exception_threshold                     integer       NOT NULL,
    online_capacity_limit                   integer       NOT NULL,
    created_at                              timestamptz   NOT NULL,
    updated_at                              timestamptz   NOT NULL,
    version                                 bigint        NOT NULL DEFAULT 0,
    opening_hours_version                   bigint        NOT NULL DEFAULT 0,
    singleton_guard                         boolean       NOT NULL DEFAULT true,
    CONSTRAINT store_settings_singleton_key UNIQUE (singleton_guard),
    CONSTRAINT store_settings_singleton_check CHECK (singleton_guard),
    CONSTRAINT store_settings_minutes_check CHECK (
        base_prep_minutes > 0 AND queue_minutes_per_order >= 0 AND busy_mode_minutes >= 0
        AND board_warning_minutes > 0 AND board_late_minutes > board_warning_minutes
    ),
    CONSTRAINT store_settings_thresholds_check CHECK (
        register_difference_threshold_amount >= 0 AND exception_threshold > 0 AND online_capacity_limit > 0
    )
);

-- The store must work out of the box; ADMIN tunes these afterwards.
INSERT INTO store.store_settings (
    id, time_zone, currency, base_prep_minutes, queue_minutes_per_order, busy_mode_minutes,
    board_warning_minutes, board_late_minutes, register_difference_threshold_amount,
    register_difference_threshold_currency, exception_threshold, online_capacity_limit,
    created_at, updated_at
) VALUES (
    '0191f2c4-0000-7000-8000-000000000001', 'America/Lima', 'PEN', 10, 2, 15, 5, 10, 20.00, 'PEN', 3, 20,
    now(), now()
);
