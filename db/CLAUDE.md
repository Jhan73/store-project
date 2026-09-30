# CLAUDE.md — db

Database conventions for PostgreSQL 18. Repo-wide rules are in the root `CLAUDE.md`; design details in `docs/tech-spec.md` §4.3, §4.8, §4.10, §4.7.

## Where things live

- **Flyway migrations live here:** `db/migration/<module>/`. This folder is the single source of truth for the database schema.
- Flyway runs at backend startup (tech-spec §4.10), so the backend build must package `db/migration` onto its classpath as `classpath:db/migration` (Maven resource pointing at `../db` in `backend/pom.xml`). Consequences:
  - The backend Docker build context must include `db/` (build from the repo root, not from `backend/`).
  - Backend CI path filters must include `db/**`, so a migration-only PR still runs the backend tests.
- Local database: PostgreSQL 18 from the root `compose.yaml` (database, user, and password `jugueria`), on a random host port — find it with `docker compose port postgres 5432`.

## Schema ownership

- One PostgreSQL schema per backend module: `identity`, `store`, `catalog`, `inventory`, `ordering`, `instore`, `preparation`, `payments`, `reporting`, `audit`, `shared`, plus `modulith` (event publication registry).
- Plus `flyway`, which holds only `flyway_schema_history`. It is not a module and no module may read it. `migrator` has `CREATE ON DATABASE` but no rights on `public`, so Flyway cannot use its default location; `spring.flyway.schemas=flyway` makes it create and own this one. That also makes `flyway` the default schema for migrations, so every migration must qualify its objects with its own schema — which the one-schema-per-module rule already requires.
- A module reads and writes **only its own schema**.
- **No cross-schema foreign keys.** Reference other modules' rows by ID only. This is what keeps modules extractable.

## Migrations (Flyway)

- File name: `V<yyyy>_<MM>_<dd>_<HHmm>__<module>_<description>.sql`, e.g. `V2026_09_17_1030__catalog_create_product.sql`. Timestamps avoid version collisions between modules.
- One module per migration file, under that module's folder: `db/migration/catalog/V2026_09_17_1030__catalog_create_product.sql`.
- **Expand/contract only.** A release may add tables/columns; dropping or renaming happens in a later release once no deployed version uses them. Image rollback depends on this.
- Never edit a migration once it is merged to `develop` — it has already run on `test`, and Flyway's checksum validation would fail. Add a new one.
- `spring.jpa.hibernate.ddl-auto=validate` in **every** profile, including local — Flyway owns DDL. Never `update`/`create` locally: Hibernate cannot generate partial unique indexes, `CHECK` constraints, grants, or roles, so the local schema would silently differ from `test`/`prod` and from the Testcontainers schema used by `*IT` tests.

### Local workflow

- A migration that is **not merged to `develop` yet** is yours: edit it freely instead of stacking fix-up migrations.
- After editing it, reset the local database: `docker compose down -v` and start again. Flyway re-applies everything on startup.
- To avoid writing DDL from scratch, let Hibernate write a **draft** without touching the database, then turn it into a migration by hand (module schema, partial indexes, constraints, naming):
  ```properties
  spring.jpa.properties.jakarta.persistence.schema-generation.scripts.action=create
  spring.jpa.properties.jakarta.persistence.schema-generation.scripts.create-target=build/schema-draft.sql
  ```
  Enable it only temporarily in your local run; never commit it to a profile file.

## Roles and privileges

| Role | Used by | Privileges |
|------|---------|------------|
| `migrator` | Flyway | DDL |
| `app` | Application | DML only |

Append-only tables are enforced by grants, not by convention — `app` has `INSERT, SELECT` and **no** `UPDATE/DELETE/TRUNCATE` on:
`audit.audit_log`, `inventory.stock_movement`, `ordering.order_status_history`, `instore.in_store_payment`, `instore.cash_movement`.
Corrections are new rows (e.g. `payment_void`), never updates. Default privileges grant DML on every new table `migrator`
creates, so a partitioned append-only table needs the `REVOKE` repeated for the parent **and every partition**, in the
same migration that creates them (`audit.audit_log`'s migration is the reference).

Credentials come from SSM; never commit real passwords.

## Column conventions

| Concern | Convention |
|---------|------------|
| Primary keys | `uuid`, UUID v7 generated in the application |
| Money | `<attribute>_amount numeric(12,2)` + `<attribute>_currency char(3)` (ISO-4217), e.g. `unit_price_amount`, `unit_price_currency` |
| Timestamps | `timestamptz`, stored in UTC |
| Optimistic locking | `version` column on mutable aggregates |
| Snapshots | Order/ticket lines store product name, chosen options with price deltas, and line total |
| Naming | `snake_case`, singular table names (`user_account`, `stock_item`) |

## Patterns the design relies on

- **Conditional atomic updates** decide business outcomes (0 rows ⇒ rejected):
  `UPDATE inventory.stock_item SET reserved = reserved + :qty, version = version + 1 WHERE product_id = :id AND on_hand - reserved >= :qty;`
- **Partial unique indexes** for invariants, e.g. one open ticket per table: `ticket(table_id) WHERE status = 'OPEN'`.
- **Singleton row**: a constant boolean guard column with a `UNIQUE`/`CHECK` constraint pair (`guard boolean NOT NULL DEFAULT true`, `UNIQUE (guard)`, `CHECK (guard)`) makes a second row impossible, e.g. `store.store_settings`. The row is seeded by migration with working defaults; the application only updates it.
- **Job claiming** with `SELECT … FOR UPDATE SKIP LOCKED LIMIT 100`.
- **Idempotency** rows in `shared.idempotency_key` (unique on `actor_id` + `key`, `request_hash`, `response`, `expires_at`), inserted with `ON CONFLICT DO NOTHING` inside the use-case transaction (tech-spec §5.2).
- **Idempotent listeners** rely on unique constraints on the effect (e.g. one `stock_movement` per reason + reference + product) or on state-guarded updates (tech-spec §4.11).
- **Fan-out** with `NOTIFY app_events, '<json>'` — payload is type + IDs only, under 8 KB.
- **Range-partitioned append-only tables**: `audit.audit_log` is `PARTITION BY RANGE` on its timestamp column, one
  partition per year, pre-created by migration, plus a `DEFAULT` partition so a late partition migration never blocks
  a write instead of failing loudly. The partition key must be part of any primary key, so the table's PK is
  `(id, occurred_at)` rather than `id` alone — `id` stays globally unique (UUID v7) without a DB constraint enforcing
  it. New yearly partitions arrive as an ordinary migration (`app` has no DDL rights to create one itself at runtime).
  `AuditPartitionCoverageIT` and `AuditAppendOnlyGrantsIT` (backend test suite) fail in CI, a year ahead of time, if a
  partition or its revoke is ever missed; `docs/runbooks/audit-partitions.md` covers adding one and recovering rows
  that already landed in `DEFAULT`.

## Capacity

Hikari pool 10 + 1 dedicated `LISTEN` connection per backend task → max 44 connections at 4 tasks on `db.t4g.micro`. Account for this before raising pool sizes or task counts.
