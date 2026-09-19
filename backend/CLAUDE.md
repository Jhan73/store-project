# CLAUDE.md — backend

Spring Boot modular monolith. Repo-wide rules are in the root `CLAUDE.md`; design details in `docs/tech-spec.md` §4, §5, §7, §11 (rejected alternatives in §13).

## Architecture: modular monolith

One deployable split into Spring Modulith modules, each owning its domain, its public API, and its own PostgreSQL schema:

`identity` · `store` · `catalog` · `inventory` · `ordering` · `instore` · `preparation` · `payments` · `notifications` · `reporting` · `audit` · `shared`

Principles behind every rule below:

1. **Boundaries are enforced, not suggested.** Modules talk through public API types and domain events, never through each other's repositories, entities, or tables. `ApplicationModules.verify()` fails the build otherwise.
2. **Extraction-ready.** Events via outbox + schema-per-module + references by ID mean a module (e.g. `payments`, `notifications`) can become a service later without a rewrite. Do not introduce shortcuts that break this.
3. **The backend is the only real enforcement.** Frontend validation and role guards are UX; every rule is re-checked here.
4. **REST is the source of truth; WebSocket messages are signals.** Clients re-fetch on reconnect.
5. **PostgreSQL is the coordination layer.** Concurrency, idempotency, jobs across tasks, and cross-instance fan-out use the database (conditional updates, `SKIP LOCKED`, `LISTEN/NOTIFY`) — no Redis, broker, or lock library.
6. **Layered by default; ports-and-adapters only where an external system exists** (payment provider, email, storage). See "Internal style per module".

## Stack

Java 25 · Spring Boot 4.1 · Spring Modulith 2.1 · Spring Security 7 · Spring Data JPA (Hibernate 7) · Flyway · PostgreSQL 18 · Lombok. Virtual threads enabled.

Not yet in `pom.xml` (tech-spec §3): Modulith `-starter-jdbc`, `spring-boot-starter-security-oauth2-resource-server`, `java-uuid-generator` (UUID v7), ArchUnit, Validation, Cache + Caffeine, springdoc-openapi, Bucket4j, WireMock, AWS SDK. Add each with the first work package that needs it, not speculatively.

## Commands

```bash
./mvnw spring-boot:run                       # run (Windows: mvnw.cmd)
./mvnw test                                  # unit tests only (*Test, no Spring context)
./mvnw verify                                # unit + integration tests (*IT) — what CI runs
./mvnw test -Dtest=ClassName#method          # single unit test (class or method)
./mvnw verify -Dit.test=ClassName -Dtest=NONE -Dsurefire.failIfNoSpecifiedTests=false   # single integration test
```

`*IT` classes run through the Maven Failsafe plugin and need Docker (Testcontainers). Without Docker, run `./mvnw verify -DskipITs`.

Local run (tech-spec §8.5): `./mvnw spring-boot:run` uses the `local` profile, and Spring Boot starts PostgreSQL and Mailpit from the root `compose.yaml` (Docker required; Mailpit UI at http://localhost:8025). Product images go to the real bucket `jugueria-dev-media` using short-lived credentials — `aws sso login --profile jugueria-dev`, then start with `AWS_PROFILE=jugueria-dev`. Never put AWS access keys in `.env`.

Profiles: none is hardcoded. `local` for development (set by the Maven plugin for `spring-boot:run`), `test`/`prod` through `SPRING_PROFILES_ACTIVE` in each ECS service; tests run with no profile and get PostgreSQL from Testcontainers. `test`/`prod` read `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` (role `app`) and `DB_MIGRATOR_USERNAME`, `DB_MIGRATOR_PASSWORD` (role `migrator`, used by Flyway). Profile files hold only non-secret differences.

Security: until identity is built (M1-B2), `identity/internal/security` exposes only `/actuator/health/**` and denies every other request.

Flyway migrations are **not** in this folder: they live in the root `db/migration/<module>/` and are packaged onto the classpath at build time. Schema and migration rules are in `db/CLAUDE.md`.

## Module structure

Base package `com.jhanantezana.jugueria`. Each direct sub-package is a module:

```
catalog/
├── CatalogApi.java        ← public: queries/commands other modules may call
├── ProductChanged.java    ← public: domain event
├── internal/              ← private: entities, services, repositories
└── web/                   ← private: REST controllers, request/response DTOs
payments/
├── PaymentsApi.java
├── internal/
│   ├── PaymentGateway.java   ← port (only because an external system exists)
│   └── mercadopago/          ← adapter: the only code that knows Mercado Pago
└── web/
```

## Internal style per module

The unit of decision is the **dependency**, not the module:

| Dependency | Approach |
|------------|----------|
| PostgreSQL | **No port.** Spring Data repositories and explicit SQL in `internal/`. The conditional updates, `SKIP LOCKED`, and partial unique indexes *are* business rules; do not hide them behind a database-agnostic interface |
| External system (payment provider, email, object storage) | **Port + adapter.** The port lives in `internal/`; the adapter in its own sub-package (`internal/mercadopago/`) and is the only code that knows the provider |
| Another module | Neither: call its public `XxxApi` or react to its events |

A module with one port is still a layered module (e.g. `payments`). Do not add ports "for flexibility" or "for testability" — tests run against real PostgreSQL with Testcontainers, so an in-memory adapter would test something that never runs in production.

**Layered does not mean anemic.** Pure business rules go in entities and value objects with no Spring or I/O: `Money`, pricing with modifiers, the estimate formula, board state transitions, bill-split rounding. Unit-test them with plain JUnit.

**Promoting a module to full hexagonal** requires a concrete signal, never anticipation:

1. Services mix so many rules with I/O that testing a rule needs the database, or
2. The same domain logic is triggered from several inputs (REST, events, jobs) and starts being duplicated.

Likely candidates: `ordering`, `instore`. Expected to stay layered: `catalog`, `store`, `audit`, `reporting`. The promotion is an internal refactor — the public surface (`XxxApi`, events, private `internal/` and `web/`) does not change — and must be recorded in `docs/tech-spec.md` §4.2 with the signal that justified it. Do not promote a module on your own initiative; propose it.

## Rules

**Module boundaries**
- Only the top-level package of a module is public. Never import another module's `internal` or `web` types.
- Cross-module reactions use domain events with `@ApplicationModuleListener` (async, after commit, persisted in the Modulith JDBC outbox).
- Exception: `audit` listens with a plain synchronous `@EventListener` so an audit failure rolls back the business change.
- Exception: commands needing an atomic decision across two modules (voiding a line, cancelling an order vs board state) call the other module's API synchronously in the same transaction. Keep this list short and documented in the module's API.
- Every command that changes an audited entity (PRD FR-AUD-01) publishes a domain event, even with no other consumer.
- No cyclic dependencies between modules.

**Consistency and concurrency**
- Stock, board state, and slot capacity change through **conditional atomic updates** (`UPDATE … WHERE <precondition>`; 0 rows ⇒ business rejection). No read-check-write in Java.
- Lock multiple products in ascending `product_id` order to avoid deadlocks.
- `preparation.board_order.status` arbitrates races between staff and customer commands.
- Mutable aggregates use `@Version` optimistic locking.
- Scheduled jobs run on every task and claim work with `SELECT … FOR UPDATE SKIP LOCKED LIMIT 100`. No scheduler lock library.
- Commands that move money or stock require an `Idempotency-Key` header. See "Idempotency" below.

**Money**
- `Money` value object (`numeric(12,2)` + ISO-4217 currency). Never `double`/`float`.
- Prices are tax-inclusive; order and ticket lines store price snapshots so refunds and reports never depend on the current catalog.

**API** (full conventions in tech-spec §5.3)
- REST + JSON under literal `/api/v1/**` in controller mappings. Plural kebab-case paths; non-CRUD actions as verb sub-resources (`POST /tickets/{id}/send`).
- Create → `201` + `Location` + resource. Update/command → `200` + updated resource. Delete → `204`.
- JSON `camelCase`; enums as `UPPER_SNAKE_CASE` strings; optional values as `null`, collections never `null`; moments ISO-8601 UTC with `Z`; durations as integers with the unit in the name (`deliveryMinutes`).
- Money in JSON: `{ "amount": "12.50", "currency": "PEN" }` (amount as string).
- Pagination `?page=&size=` (max 100) returning `PageResponse<T>` from `shared` — never Spring Data's `Page`. Sorting only on explicitly allowed fields. `ETag`/`If-Match` on catalog and settings updates.
- Bean Validation on every request DTO. Controllers never expose entities.
- Document each endpoint's success response and possible error `code`s in OpenAPI.
- The generated OpenAPI spec is committed as `api/openapi.json`; CI fails on drift. Regenerate and commit it with any API change.

**External systems**
- Payment webhooks are never trusted: verify the signature, then fetch the payment from the provider API. Dedupe by provider event id.
- HTTP clients (`RestClient`) always have timeouts. Resilience via Spring Framework 7 `@Retryable` / `@ConcurrencyLimit`.

**Real-time**
- STOMP over `/ws` with the in-memory broker. Cross-instance fan-out: publish `NOTIFY app_events` after commit; each task's `LISTEN` connection forwards to local subscribers.
- Payloads carry only type + IDs; clients re-fetch via REST.

**Logging**
- Structured JSON with `correlation_id`; never log passwords, tokens, or full addresses.

**Metrics** (tech-spec §12)
- A `MeterFilter` in `shared` exports **only an allowlist**, and only in `prod` (≤ 15 series; `test` exports nothing). Adding a metric or tag to the allowlist requires stating its series count in the PR (why: tech-spec §12).
- Names: lowercase dot-separated `<domain>.<fact>` (`orders.paid`), snake_case inside a segment, base units, no unit in the name.
- Counter for facts, timer for durations, gauge for current values.
- Tags only with bounded, low cardinality (`channel`, `reason`, `provider`, `outcome`). **Never** IDs, emails, amounts, or free text.
- Record business metrics in the owning module's services or listeners, with name constants in that module.

**User-facing text**
- API responses never contain user-facing text (tech-spec §5.1). Emails are the only exception: templates in `notifications`, one file per message and locale, never strings in Java.
- Email transport (tech-spec D16): SMTP adapter to Mailpit locally; SES API v2 adapter with the ECS task role in `test`/`prod`. Never the SES SMTP interface or any stored mail password. In `test`, only allowlisted recipients receive email.

## Transactions and events

Full rules in tech-spec §4.11.

- `@Transactional` **only** on application service methods in `internal/`: one use case, one transaction. Queries use `readOnly = true`. Never on controllers, domain objects, or repositories.
- **Never call an external system inside a transaction** (Mercado Pago, SES, S3). The pool has 10 connections; a slow provider exhausts it. Commit → call → record the result in a new transaction.
- Calling a `@Transactional` method from the same class skips the proxy and runs without the transaction. Move it to another bean.
- **Every `@ApplicationModuleListener` is idempotent.** Delivery is at-least-once, so the same event can arrive twice. Guard with state (`UPDATE … WHERE status = …`) or a unique constraint on the effect — never "check then insert" in Java. Never rely on ordering between different events. Each listener has a test delivering the same event twice.
- Events are past-tense records carrying IDs, `occurredAt` (from the `Clock`), and what consumers need — never entities. One event per business fact, in the publishing module's top-level package, published by application services (not entities).
- **Never rename or move an event class, or remove a field**, while publications may be pending: re-delivery deserializes by class name. Only add fields.
- A listener class lives in the consumer's `internal/`, handles one event type, and is named after what it does (`ReserveStockOnOrderPlaced`).

## Code conventions

Full rules in tech-spec §4.13.

- **Three model types, never reused as one another:** entities (`internal/`), module API records (top-level package, for other modules: `OrderView`, `PlaceOrderCommand`), and REST DTO records (`web/`, for HTTP: `CreateOrderRequest`, `OrderResponse`, `OrderSummaryResponse`).
- Records for DTOs, commands, events, projections, and value objects. Lombok only on entities.
- Mapping is manual (`OrderResponse.from(order)` or a small mapper in `web/`). No MapStruct.
- **Jackson 3** (Boot 4): import `tools.jackson.*`; annotations stay `com.fasterxml.jackson.annotation.*`. Customize with a `JsonMapper` bean or `@JacksonComponent`, never `ObjectMapper`/`@JsonComponent`.
- Deployment config: one `@ConfigurationProperties` record per module, prefix `jugueria.<module>`, `@Validated`. No scattered `@Value`.
- Business parameters the store tunes (estimates, thresholds, capacity, feature flags) live in `store_settings` in the database, never in properties files.

## Idempotency

Full design in tech-spec §5.2.

- Implement it **inside the use-case transaction** through `shared`'s idempotency API. Never as a servlet filter or MVC interceptor: they run outside the transaction.
- The controller validates the `Idempotency-Key` header (UUID, required) and passes it to the service; the service's first statement registers it with actor ID + request hash.
- Keys are scoped by `actor_id`. Replays return the stored response with `Idempotent-Replayed: true`; a different body with the same key is `422`. Rejected commands roll back their key.
- Payment webhooks dedupe by provider event ID instead.

## Persistence (JPA)

Full table in tech-spec §4.11. The rules agents most often get wrong:

- `spring.jpa.open-in-view=false` and `spring.jpa.hibernate.ddl-auto=validate` in every profile. Flyway owns the schema.
- Entities: `@Table(schema = "<module>")`, extend `shared`'s base entity (`Persistable<UUID>`), ID from `Ids.newId()` at construction. equals/hashCode on ID only.
- Lombok on entities: `@Getter` and `@NoArgsConstructor(access = PROTECTED)` only. **Never** `@Data`, `@EqualsAndHashCode`, `@ToString`, `@Setter`. Lombok elsewhere is unnecessary: DTOs, events, and value objects are records.
- Every `@ManyToOne`/`@OneToOne` is `fetch = LAZY`. Associations only inside an aggregate; other modules are referenced by a plain `UUID` column, never an entity.
- `@Enumerated(EnumType.STRING)` always.
- `Money` is `@Embeddable`; compare with `compareTo`, never `BigDecimal.equals`; every operation that can exceed two decimals states its rounding.
- Lists and reports return record projections; avoid N+1 with fetch joins or `@EntityGraph`.
- Conditional updates (`JdbcClient` or `@Modifying`) return the row count; `0` ⇒ `BusinessException`. Don't load the same row as an entity in that transaction (or use `clearAutomatically = true`).

## Time

Full rules in tech-spec §4.12.

- Inject `Clock` (one UTC bean in `shared`) and use `Instant.now(clock)`. `Instant.now()`, `LocalDate.now()`, `LocalDateTime.now()`, `new Date()`, `System.currentTimeMillis()` are forbidden in `src/main` (ArchUnit rule).
- Persist and transmit `Instant`. "Today" and the business day use `LocalDate` in the store `ZoneId` from `store_settings`, never the JVM default zone.
- Tests use a fixed or manually advanced clock, never `Thread.sleep`.

## Authentication and authorization

Full design in tech-spec §7.1. Rules:

- **No JWT library, no custom JWT filter.** Tokens are issued with Spring Security's `NimbusJwtEncoder` and validated by `spring-boot-starter-security-oauth2-resource-server` (Boot 4 name; the old `spring-boot-starter-oauth2-resource-server` is deprecated). Never add jjwt or similar.
- **One `SecurityFilterChain`**, in `identity/internal/security`. Stateless, deny by default. The public-route allowlist lives only there; adding a public endpoint means editing that list, deliberately.
- The filter chain decides only public vs authenticated. Put each check in exactly one layer:

| Check | Where | How |
|-------|-------|-----|
| Is there a valid user? | Filter chain | Deny by default + allowlist |
| May this role call this endpoint? | Controller method in `web/` | `@PreAuthorize("hasAnyRole(...)")` per the PRD §4 matrix |
| May this actor act on this resource in its current state? | Service / domain in `internal/` | Business rule with `CurrentActor`, throws `BusinessException` |

- **Every controller method has `@PreAuthorize`** unless its route is in the allowlist (an ArchUnit test fails the build otherwise).
- Never put `@PreAuthorize` on module APIs or services: event listeners and jobs call them without a user.
- Rules that depend on data (e.g. SERVER may void a line only while `PENDING`) are domain rules, never SpEL.
- Ownership is part of the query (`findByIdAndCustomerId`). Someone else's resource is `404`, not `403`.
- Read the actor only through `CurrentActor` (`shared`). Code in `internal/` never touches `SecurityContextHolder`. Jobs and `@ApplicationModuleListener`s get `SYSTEM`; events that need attribution carry `actorId`/`actorRole` explicitly.
- Refresh tokens are hashed, rotated, and family-revoked on reuse. Lockout counters live in PostgreSQL; Bucket4j is only coarse throttling.
- Tests: one allowed-role and one denied-role test per endpoint (`jwt()` post-processor from `spring-security-test`).

## Error handling

Full model and status table in tech-spec §5.1. Rules:

- Every error is RFC 9457 Problem Details with `code` (`<module>.<kebab-case-reason>`) and `correlationId`. Codes are public API: never rename, remove, or reuse one.
- Throw `BusinessException(ErrorCode, properties)`. Each module declares its codes in a public enum implementing `ErrorCode`, with the wire code written explicitly.
- **409 vs 422:** could the same request succeed later because someone else changes the state? `409`. Must the request itself change? `422`. A conditional update that affected 0 rows is a `409` with a module-specific code.
- Error bodies are built **only** by the global `@RestControllerAdvice` in `shared` plus the Security `AuthenticationEntryPoint`/`AccessDeniedHandler`. Never write `@ExceptionHandler`/`@ControllerAdvice` in a module, never catch an exception to build a `ResponseEntity` error, never use `ResponseStatusException`.
- Translate expected persistence exceptions to a code inside the module (unique email → `identity.email-already-registered`); let unexpected ones become `500`.
- `title`/`detail` are English and for developers; the backend never returns user-facing (Spanish) text. `detail` never contains stack traces, SQL, secrets, or personal data.
- Log `4xx` at `INFO` without stack trace, `5xx` at `ERROR` with it.
- Every new code gets a test asserting status, `code`, and any extra properties.

## Testing

| Level | Tooling |
|-------|---------|
| Unit | JUnit + AssertJ + Mockito — domain rules, state machines, pricing, estimate formula |
| Module integration | `@ApplicationModuleTest` + Testcontainers PostgreSQL (`@ServiceConnection`); assert events with `PublishedEvents` |
| Architecture | `ApplicationModules.of(<MainClass>.class).verify()` |
| Adapters | WireMock for Mercado Pago (approved/rejected only, partial refunds, timeouts, bad signature) |
| Races | Module integration tests with parallel threads for each race listed in tech-spec §11 |

Coverage gate: ≥ 80% lines on `internal` packages. Tests run against real PostgreSQL (Testcontainers), not H2 — the design relies on PostgreSQL-specific behavior.

Conventions (tech-spec §11.1):

- `*Test` = no Spring context (Surefire, `mvn test`). `*IT` = any Spring context or container (Failsafe, `mvn verify`). Same package as the code under test.
- Method names describe behavior (`rejectsVoidWhenLineIsReady()`); body as given / when / then.
- Test data from per-module builders (`OrderFixtures.aPaidOrder()`), never shared across modules.
- One `postgres:18` Testcontainers container per JVM via `@ServiceConnection` in a shared `@TestConfiguration`.
- **Integration tests are never `@Transactional`**: the rollback prevents the commit, so after-commit listeners never run and the test passes for the wrong reason. Clean up the tables you wrote instead.
- Async events: Modulith `Scenario` (`stimulate(…).andWaitForEventOfType(…)`), never `Thread.sleep`.
- Mock only external-system ports and other modules' APIs, with `@MockitoBean` (`@MockBean` no longer exists in Boot 4). Never mock repositories.
- Tests never call real AWS: S3/SES adapters are tested with WireMock via the SDK endpoint override.
