CREATE SCHEMA audit;
GRANT USAGE ON SCHEMA audit TO app;

-- Partitioned by year on occurred_at, so the partition key joins id in the primary key.
CREATE TABLE audit.audit_log (
    id             uuid          NOT NULL,
    occurred_at    timestamptz   NOT NULL,
    actor_id       uuid,
    actor_role     varchar(20)   NOT NULL,
    action         varchar(60)   NOT NULL,
    entity_type    varchar(60)   NOT NULL,
    entity_id      uuid          NOT NULL,
    before         jsonb,
    after          jsonb,
    reason         varchar(500),
    correlation_id varchar(100),
    client_ip      varchar(45),
    user_agent     varchar(300),
    PRIMARY KEY (id, occurred_at)
) PARTITION BY RANGE (occurred_at);

CREATE TABLE audit.audit_log_2026 PARTITION OF audit.audit_log
    FOR VALUES FROM ('2026-01-01T00:00:00Z') TO ('2027-01-01T00:00:00Z');
CREATE TABLE audit.audit_log_2027 PARTITION OF audit.audit_log
    FOR VALUES FROM ('2027-01-01T00:00:00Z') TO ('2028-01-01T00:00:00Z');
-- Catches any row outside the pre-created ranges, so a late partition migration never blocks a write.
CREATE TABLE audit.audit_log_default PARTITION OF audit.audit_log DEFAULT;

-- Indexes on the partitioned parent propagate to every current and future partition.
CREATE INDEX audit_log_entity_idx ON audit.audit_log (entity_type, entity_id, occurred_at DESC);
CREATE INDEX audit_log_actor_idx ON audit.audit_log (actor_id, occurred_at DESC);

-- Default privileges grant DML on every new table, so each partition repeats this revoke too.
REVOKE UPDATE, DELETE, TRUNCATE ON audit.audit_log FROM app;
REVOKE UPDATE, DELETE, TRUNCATE ON audit.audit_log_2026 FROM app;
REVOKE UPDATE, DELETE, TRUNCATE ON audit.audit_log_2027 FROM app;
REVOKE UPDATE, DELETE, TRUNCATE ON audit.audit_log_default FROM app;
