# Add the next yearly audit_log partition

## What this does

`audit.audit_log` is partitioned by year on `occurred_at`. Two yearly partitions and a `DEFAULT`
catch-all exist at any time. `AuditPartitionCoverageIT` fails once the year one full year ahead of
today has no partition of its own, so this runbook is meant to be run calmly, a year before it is
needed — never under pressure the day the gap actually opens.

You end up with a new `audit.audit_log_<year>` partition, with `app`'s DML rights on it correctly
restricted to `INSERT`/`SELECT`.

## Add the partition (the normal case)

One migration, `db/migration/audit/V<timestamp>__audit_add_audit_log_<year>_partition.sql`:

```sql
CREATE TABLE audit.audit_log_<year> PARTITION OF audit.audit_log
    FOR VALUES FROM ('<year>-01-01T00:00:00Z') TO ('<year+1>-01-01T00:00:00Z');

REVOKE UPDATE, DELETE, TRUNCATE ON audit.audit_log_<year> FROM app;
```

The `REVOKE` is not optional: default privileges grant `app` full DML on every table `migrator`
creates, including this one. `AuditAppendOnlyGrantsIT` fails the build if it is missing.

Deploy it like any other migration. Nothing else changes: the parent table, its indexes, and the
application code already route rows to whichever partition matches their `occurred_at`.

## If the DEFAULT partition already holds rows for that year

This happens only if the runbook above was run late: some rows for `<year>` were written to
`audit.audit_log_default` before the dedicated partition existed. Moving them out is online (no
downtime) but takes a few manual steps, run as `migrator` with a direct `psql` session (see
`database-bootstrap.md` for how to open one against `test`/`prod`).

1. **Detach the DEFAULT partition.** While detached it is a normal standalone table, so new writes
   for unmapped dates fail loudly instead of landing in it — do this in a low-traffic window:

   ```sql
   ALTER TABLE audit.audit_log DETACH PARTITION audit.audit_log_default;
   ```

2. **Create the missing partition**, exactly as in the normal case above:

   ```sql
   CREATE TABLE audit.audit_log_<year> PARTITION OF audit.audit_log
       FOR VALUES FROM ('<year>-01-01T00:00:00Z') TO ('<year+1>-01-01T00:00:00Z');

   REVOKE UPDATE, DELETE, TRUNCATE ON audit.audit_log_<year> FROM app;
   ```

3. **Move the misplaced rows.** Inserting into the parent routes each row to the partition that
   now matches it; `migrator` may delete from the detached table, since it is no longer the
   append-only partition `app` is restricted from:

   ```sql
   INSERT INTO audit.audit_log
   SELECT * FROM audit.audit_log_default
   WHERE occurred_at >= '<year>-01-01T00:00:00Z' AND occurred_at < '<year+1>-01-01T00:00:00Z';

   DELETE FROM audit.audit_log_default
   WHERE occurred_at >= '<year>-01-01T00:00:00Z' AND occurred_at < '<year+1>-01-01T00:00:00Z';
   ```

4. **Reattach the DEFAULT partition:**

   ```sql
   ALTER TABLE audit.audit_log ATTACH PARTITION audit.audit_log_default DEFAULT;
   ```

Run steps 1–4 as one transaction if the row count is small enough to make the detach window brief;
otherwise accept a short period (between steps 1 and 4) where a row outside every pre-created range
is rejected rather than silently absorbed.

## Verify

```sql
SELECT relname, pg_get_expr(relpartbound, oid) FROM pg_class
WHERE relnamespace = 'audit'::regnamespace AND relispartition;
```

Expected: one row per partition, the new one showing the correct `FOR VALUES FROM ... TO ...`
range, and `audit_log_default` still present with `DEFAULT`.

```sql
SELECT count(*) FROM audit.audit_log_default
WHERE occurred_at >= '<year>-01-01T00:00:00Z' AND occurred_at < '<year+1>-01-01T00:00:00Z';
```

Expected: `0`, once the recovery procedure above ran.

`AuditPartitionCoverageIT` and `AuditAppendOnlyGrantsIT` (backend test suite) exercise both the
coverage and the grant on every build; a green `./mvnw verify` after deploying is the real
confirmation.
