# Implementation Plan — Release 1

| Field | Value |
|-------|-------|
| Status | Draft v1 |
| Owner | Jhan Antezana |
| Last updated | 2026-09-18 |
| Implements | [PRD.md](./PRD.md) §10 milestones · [tech-spec.md](./tech-spec.md) |

This plan turns the PRD milestones into ordered **work packages** (WP). It says **in which order** things are built and **when each one is done**. It does not repeat requirements or design: each WP points to the PRD IDs and tech-spec sections that define it.

## Ordering rule

1. **M0 first, pipeline-first.** The walking skeleton exists so that every later change is built, tested, and deployed by the pipeline from day one (tech-spec R1: ECS Express Mode must be validated here).
2. **From M1 on, inside each milestone: backend → frontend → close.**
   - **Backend**: all backend WPs of the milestone, deployed to `test`, API contract (`backend/api/openapi.json`) committed.
   - **Frontend**: all frontend WPs of the milestone, built against that contract.
   - **Close**: E2E journeys, exit criteria from the PRD, and a release to `prod`.
3. A milestone starts only after the previous one is closed.
4. WPs marked **(S)** contain only "Should" requirements. If the schedule slips, they move to the follow-up release first (PRD C1).

## Overview

| Milestone | Goal | Exit criteria (PRD §10) | Blocking decisions |
|-----------|------|-------------------------|--------------------|
| **M0** Walking skeleton | Repo, pipelines, `test` + `prod`, health endpoint end to end | A merge to `develop` reaches `test` automatically; a release PR to `main` reaches `prod` after approval | — (Q4 answered: `jugueria.jhanantezana.com`) |
| **M1** Catalog & staff | Staff auth, catalog with modifiers and availability, settings, tables, audit | FR-CAT, FR-ADM-01/02, FR-INS-09/14, FR-AUD done | Q1 country/currency |
| **M2** In-store | Tickets, board, register shifts, stock | FR-INS, FR-REG, FR-PRP, FR-STK done; used in the store in parallel with paper for 1 week | Q5 stations · email template engine |
| **M3** Online | Customer accounts, checkout, payments, estimates, notifications | FR-ONL done; 20 real test orders paid and refunded in `test` | Q2 invoicing · payment provider account · SES production access |
| **M4** Dashboard & launch | Reports, dashboard, hardening, load test, restore drill | FR-RPT done; all M items done; NFR-05 and NFR-07 verified | Q3 audit retention |

## How to work a WP

1. Branch `feature/<wp-id>-<short-name>` from `develop` (e.g. `feature/m1-b2-identity`).
2. Optionally plan it with SDD (`/sdd-new <wp-id>`) when the WP needs design decisions not already in the tech-spec.
3. TDD; follow the area `CLAUDE.md`.
4. PR to `develop`; `ci-ok` green; deployed to `test` by `cd-test.yml`.
5. Update the **Status** column here in the same PR (`todo` → `doing` → `done`).

A WP is **done** when:

- [ ] Its "Done when" criteria are met and covered by tests.
- [ ] Deployed to `test` and smoke tests pass.
- [ ] API changes regenerated in `backend/api/openapi.json` (and frontend types, if the frontend is in scope).
- [ ] New error codes, metrics, and i18n IDs follow tech-spec §5.1, §12, §6.6.
- [ ] Any deviation from the tech-spec is written into the tech-spec in the same PR.

---

## M0 — Walking skeleton

M0 is the only milestone that is not backend → frontend: the pipeline comes first so every following PR is gated by it.

| ID | Work package | Spec | Done when | Status |
|----|--------------|------|-----------|--------|
| M0-01 | **Repository and GitHub setup**: push to GitHub, create `develop`, rulesets for `develop`/`main`, environments `test`/`prod` (reviewer on `prod`), CODEOWNERS, PR template, `dependabot.yml` (ignores PrimeNG major updates) | §9.2, §10.3, §10.4 | Direct pushes to `develop`/`main` are rejected; `prod` deploys require approval | done |
| M0-02 | **CI**: `ci.yml` (`changes`, `common`, area jobs, `ci-ok`), reusable `_ci-backend/frontend/infra.yml`, composite actions | §10.2, §10.4 | A frontend-only PR runs only frontend + common jobs; a failing job makes `ci-ok` fail; `ci-ok` is the only required check | done |
| M0-03 | **Backend skeleton**: remove hardcoded profile and committed password; profiles `local`/`test`/`prod`; `ddl-auto=validate`, `open-in-view=false`; Actuator (health only), Flyway packaging `db/migration`, Modulith core + `verify()` test, Testcontainers, Failsafe; `compose.yaml` (PostgreSQL 18 + Mailpit); replace the temporary PostgreSQL service container in `_ci-backend.yml` with Testcontainers; structured JSON logs; Dockerfile (repo-root context); Dependabot `docker` entry | §3, §4.10, §4.11, §11.1, `db/CLAUDE.md` | `./mvnw verify` runs `*Test` and `*IT`; app starts locally against Compose; readiness at `/actuator/health/readiness` | done |
| M0-04 | **Frontend skeleton**: remove Karma/Jasmine, `@types/node` aligned with Node 24, angular-eslint (incl. `template/i18n`), source locale `es`, per-route render modes, `withIncrementalHydration()`, `/healthz` in `server.ts`, Dockerfile; Dependabot `docker` entry | §3, §6.1, §6.6 | `npm test`, lint, and SSR build pass; `/healthz` returns 200 | done |
| M0-05 | **Infrastructure (Terraform)**, in three PRs: (1) `shared` root — backend on the existing state bucket, VPC, ECR, GitHub OIDC provider, budget, Dependabot `terraform` entry (applied); (2) `test`/`prod` roots — RDS PostgreSQL 18 (master password managed by RDS in Secrets Manager, never in state), security groups, non-secret SSM parameters, ECS execution/task roles, plus `jugueria-ci-plan` and `jugueria-infra-apply-<env>` roles in `shared` (plan only until M0-06; the first apply is followed by runbook `docs/runbooks/database-bootstrap.md` creating the `app`/`migrator` roles and their SSM secrets); (3) identity and domain — `jugueria-dev` permission set (8 h sessions, dev bucket only) assigned to the `jugueria-dev` user, `jugueria-dev-media`, SES domain identity (DKIM, MAIL FROM `mail.jugueria…`, DMARC `p=none`), one ACM certificate (`jugueria…`, `*.jugueria…`, `*.test.jugueria…`) validated in the existing `jhanantezana.com` zone | §8, `infra/CLAUDE.md` | `plan` runs on PRs; `apply` only through the protected environment; `db.t4g.micro` confirmed for PostgreSQL 18 ✓ | done |
| M0-06 | **CD**: `_build-image.yml`, `_deploy-ecs.yml`, `cd-test.yml` (per-app, digest recording), `cd-prod.yml` (digest promotion, approval, tag + release), smoke tests, `rollback.yml`; **power modes**: `env-control.yml` + `env-autostop.yml`, both environments `on-demand` | §8.1, §9.3, §10.1–10.5 | Both apps run on ECS Express Mode in `test` and `prod` **behind one shared ALB** (validates D15/R1); a release reuses the `test` digests; rollback to a previous SHA works; deploying to a stopped environment starts it; an environment started for 4 h is stopped automatically; each frontend service sets `NG_ALLOWED_HOSTS` to its own endpoint and, when configured, its custom host | done |
| M0-07 | **Custom domains for `test` and `prod`**: project certificate on the shared ALB listener, custom hosts on the Express host-header rules, Route 53 aliases (`custom_domain_environments`), `CUSTOM_FRONTEND_HOST` in `NG_ALLOWED_HOSTS`, custom-host smoke checks, runbook `docs/runbooks/custom-domains.md` | §2, §8, §10, D15, D18, R1, R10 | `https://test.jugueria.jhanantezana.com/login` and `https://api.test.jugueria.jhanantezana.com/actuator/health/readiness` return 200; a browser login on `test` works with the refresh cookie and `/ws`; a forced redeploy keeps it working (smoke); then the same for `prod` after approval; E2E runs against the custom host; the SES request can use the `prod` URL | doing (`test` wired by hand, DNS in PR #107, `prod` pending) |
| M0-close | **Close M0**: exit criteria; cost validated with the AWS Pricing Calculator; runbook `docs/runbooks/rollback.md` | §8.3, §10.5 | Exit criteria met; estimated cost within NFR-13 | done |

---

## M1 — Catalog & staff

**Before starting:** Q1 answered (country, currency, payment provider) — done: Peru, PEN, Mercado Pago. **During M1:** request SES production access (tech-spec R5).

### Backend

Order matters: foundations and `audit` come first, so every later module is audited from its first command.

| ID | Work package | Scope | Spec | Done when | Status |
|----|--------------|-------|------|-----------|--------|
| M1-B1 | **Shared foundations**: `Money`, `Ids`, `Clock`, base entity, `CurrentActor`, `ErrorCode`/`BusinessException` + global advice, `PageResponse`, correlation ID, ArchUnit rules (`@PreAuthorize`, no `now()`) | — | §4.11–4.13, §5.1, §5.3, §7.1 | Error bodies match §5.1 for MVC, validation, and unexpected errors; ArchUnit rules fail on violations | done |
| M1-B2 | **identity — staff**: login, JWT (`NimbusJwtEncoder`/resource server), refresh rotation cookie, lockout, `SecurityFilterChain`, staff accounts and roles, deactivation revokes sessions, first-admin bootstrap command, set-password email transport (D16: SMTP/Mailpit locally, SES API v2 in `test`/`prod`) | FR-ADM-01 | §7, §7.1, §6.4 | Allowed/denied-role tests per endpoint; deactivated user loses access within 15 min; the set-password email renders and sends through Mailpit locally and through the SES v2 adapter to an allowlisted recipient, both proven by code and tests that never call real AWS — live delivery in `test` is confirmed once the owner applies the Terraform IAM grant and sets the recipient allowlist (`docs/runbooks/ses-setup.md`) | done |
| M1-B3 | **audit**: `audit_log` (partitioned), synchronous listener, append-only grants, search endpoint | FR-AUD-01/02 | §4.7 | Audit failure rolls back the change; `UPDATE/DELETE` on `audit_log` fails for role `app` | done |
| M1-B4 | **store**: settings, opening hours, delivery zones, reason lists, board thresholds | FR-ADM-02, FR-INS-14 | §4.1, §4.8 | Settings editable with `ETag`/`If-Match`; changes audited | done |
| M1-B5 | **notifications — real time**: STOMP `/ws`, `ChannelInterceptor` auth, `LISTEN/NOTIFY` bridge, `/topic/catalog`, `/topic/store-status` | — (enables FR-CAT-03, NFR-04) | §4.4 | Change on instance A reaches a subscriber on instance B in ≤ 5 s (test with two instances) | done |
| M1-B6 | **catalog**: categories, stations, products, modifier groups/options, allergens, availability ("86"), menu endpoint with cache + `ETag`, product images (S3 + CloudFront) | FR-CAT-01..07 | §4.6, §4.8 | Min/max modifier rules enforced; availability visible to subscribers in ≤ 5 s; menu cache evicted on every task | done |
| M1-B7 | **instore — tables**: table configuration and grid endpoint (all tables free until M2) | FR-INS-09 (config) | §4.8 | ADMIN manages tables; grid endpoint returns every active table (all FREE until M2) | done |

Infra in M1: `test`/`prod` S3 media buckets + CloudFront (the local-development bucket and permission set already exist from M0-05) — **done in Terraform** (apply pending, `docs/runbooks/media-setup.md`); custom `media` hostnames are a follow-up pending the owner's decision.

### Frontend

| ID | Work package | Scope | Spec | Done when | Status |
|----|--------------|-------|------|-----------|--------|
| M1-F1 | **Core**: login, auth interceptor (single-flight refresh), role guards, error interceptor + `code` map, API types (`openapi-typescript` + `api:generate`, TypeScript 6 compatibility — tech-spec §6.5), `Money`/time utils, STOMP client, staff/admin shell, i18n setup, theme tokens with light/dark mode, Tabler icons, PrimeNG 21 setup on Angular 22 (scoped `overrides`) | — | §5.1, §6.2–6.7 | Session restores on reload; expired token refreshes once; errors show localized messages; PrimeNG 21 builds, renders with SSR, and passes component smoke tests on Angular 22 (if not, stop and ask the owner) | done |
| M1-F1b | **Set-password page**: public `/set-password` route (client-rendered) for the staff invitation and first-admin links; token read from `?token=` and removed from the URL at once, password policy validated client-side, localized error mapping, `Referrer-Policy: no-referrer` from the SSR server | FR-ADM-01 | §6.4, §7.1 | A new staff member opens the emailed link, sets a password, and signs in; a used, expired, or malformed link shows the invalid-link state | done |
| M1-F2 | **Admin catalog**: categories, products, modifiers, allergens, stations, images, availability | FR-CAT-01, 05–07 | §6.2 | ADMIN creates a product with modifier groups end to end | done |
| M1-F3 | **Admin store & staff**: settings, opening hours, zones, reason lists, tables, staff accounts | FR-ADM-01/02, FR-INS-09/14 | §6.2 | Each setting round-trips; `412` on concurrent edits handled | done |
| M1-F4 | **Staff availability ("86")** screen | FR-CAT-03 | §6.3 | Toggle reflects on another open screen in ≤ 5 s | done |
| M1-F5 | **Audit log viewer** | FR-AUD-02 | §6.2 | Search by date, actor, entity | done |

### Close M1

| ID | Work package | Done when | Status |
|----|--------------|-----------|--------|
| M1-close | E2E: admin builds a product with modifiers; staff toggles availability. Exit criteria. Release to `prod`. | Exit criteria met; release tagged | done |

---

## M2 — In-store

**Before starting:** Q5 answered (stations). **Before M2-B6:** email template engine decided (Thymeleaf, tech-spec §6.6).

### Backend

| ID | Work package | Scope | Spec | Done when | Status |
|----|--------------|-------|------|-----------|--------|
| M2-B1 | **Idempotency and jobs**: `shared` idempotency API, scheduled-job pattern (`SKIP LOCKED`), expired-key cleanup | — | §5.2, §4.3 | Same key sent concurrently to two instances creates one result | done |
| M2-B2 | **inventory**: stock items, entries, adjustments, movement ledger, low-stock list, auto-unavailable at 0, returns; reserve/commit/release API (used by M3) | FR-STK-01..05, BR-10 | §4.3, §4.8 | Parallel sale of the last unit sells it once; every change has a ledger row | done |
| M2-B3 | **preparation — board**: projection, views (tickets, all-day, ready), start/ready/undo/recall/handover, markers + ack, run-out flag, timestamps, `/topic/board`; station filter (S); customer display (S) | FR-PRP-01..10, BR-08 (in-store) | §4.5, §4.3 "Board state" | Races in tech-spec §11 pass; all-day aggregation groups identical items only. The station filter has its staff-readable list in `catalog` (`GET /stations`, `SERVER`, `CASHIER`, `ADMIN`; `/admin/stations` stays `ADMIN` only), and the customer display (FR-PRP-09) is `GET /board/display` for the same roles plus a `/topic/display` signal with no ids: only each order's number and the first word of a counter or pickup name, never a table label; online orders feed it once `ordering` (M3) sends them; undo stays client-side (§4.5) | done |
| M2-B4 | **instore — tickets**: tickets, lines, send, void rules by state, transfer, table grid status; comp (S), merge (S), repeat (S) | FR-INS-01, 02, 05, 07, 09, 10; FR-INS-08/11/13 (S) | §4.3 "In-store tickets" | Void by SERVER after Ready is rejected; transfer to an occupied table is rejected. Comp (FR-INS-08) and merge (FR-INS-11) were not built here; they were carried over and built in M2-B7; repeat (FR-INS-13) built | done |
| M2-B5 | **instore — payments and register**: shifts (open, blind close, force close), cash movements, payments + void, quick sale, sale void, stock deduction at close; split bill (S) | FR-INS-03, 04, 06; FR-REG-01..05; FR-INS-12 (S) | §4.3 "Register shift" | Expected cash is never returned before counted cash; split rounding per spec. Built with the even and by-items split (FR-INS-12); comp and merge, carried over from M2-B4, were built in M2-B7 | done |
| M2-B6 | **reporting — data capture**: listeners filling `sales_line`, `exception_event`, `shift_summary`; shift summary email to ADMIN (S) — first email: email port, SMTP adapter (Mailpit), SES adapter (task role), `test` recipient allowlist | FR-REG-06 (S); data for FR-RPT | §4.9, D16 | Every in-store sale, void, comp, and shift produces read-model rows. Built for sale, void and shift; comp rows are captured since M2-B7. The email port, both adapters and the allowlist already existed from M1-B2; this WP added the Thymeleaf renderer, the shift summary email and the `reporting` module | done |

| M2-B7 | **instore — comp and merge** (carried over from M2-B4): comp a sent line with a `COMP` reason (`CASHIER`, `ADMIN`; waste, stock leaves as waste, not charged), merge two open tickets (floor staff); comp rows in `reporting` | FR-INS-08, FR-INS-11, BR-11 | §4.3 "In-store tickets" | A comp is audited with who, when, reason and amount, leaves the ticket total and balance without the line and reaches `sales_line.comped` and the exceptions; a merge moves every line with its state and card, frees the emptied table, and is refused when the merged ticket has a live payment | done |

> **Why reporting capture is here and not in M4:** the store uses the system from M2 on. Listeners only receive events published while they exist, so sales made before `reporting` exists would never reach the reports. Capture data now; build the report screens in M4.

### Frontend

| ID | Work package | Scope | Spec | Done when | Status |
|----|--------------|-------|------|-----------|--------|
| M2-F1 | **Table grid and ticket editor** (10" tablet), shared **product configurator** | FR-INS-01, 02, 07, 09, 10; (S) 08, 11, 13 | §6.2, §6.3 | Journey 2 up to "lines Ready" works on a tablet viewport. Built: table grid, ticket editor (add lines through the shared configurator, change the quantity of or remove unsent lines, send, repeat the last round, bill request, transfer) and the product configurator. "Ready" is read from `GET /board/tickets` and `GET /board/ready` (a fully ready card leaves the first), matching each board line to its ticket line through the new `sourceLineId` of the board line. Line void is built on a new read endpoint, `GET /api/v1/reasons?type=` (active reasons, `SERVER`, `CASHIER`, `ADMIN`), which M2-F3 reuses for cash-out and payment-void reasons. **Not built:** the comp and merge screens (their backend is M2-B7) | done |
| M2-F2 | **Board**: views, sound, wake lock, disconnected state, undo, age colors, markers, run-out flag; station filter (S); customer display (S) | FR-PRP-01..09 | §6.3, §6.5 | Disconnect blocks actions until state is re-fetched; readable at 2 m on 1080p. Built: `/staff/board` for `SERVER`, `CASHIER`, `ADMIN` with the three views (Tickets, All-day, Ready), the cards (short number, channel, label, elapsed minutes, lines with modifiers, notes and a distinct allergy badge, scheduled time in the store zone), the age levels (normal, warning, late) and that zone from `GET /store/board-thresholds` with the minutes always in text, start, line and order Ready with a 5 s undo that is flushed when the page is hidden or the screen closes, handover, recall, run-out flag, NEW / MODIFIED / VOID markers that stay until acknowledged with three oscillator sounds (an explicit "Enable sound" tap and a warning while it is off), the Screen Wake Lock, and the full-width disconnect banner with actions off until a read on the live connection has been applied. The station filter (S, FR-PRP-08): a "Estación" selector appears once the store has two or more stations (with one there is nothing to filter), lists "Todas las estaciones" first, sends `?station=` on the three views, keeps the choice in this device's `localStorage` (nothing server-side), applies only the latest read, and a remembered station that is no longer listed, or a stations read that fails, means all stations for that session (only a successful read that lacks the station forgets the saved choice; a failed read keeps it). The customer display (S, FR-PRP-09): `/display` behind the same staff login (`SERVER`, `CASHIER`, `ADMIN`; the customer-screen device signs in as one of them), full-screen with no shell, two columns "Preparando" and "Listos para recoger" with the order number and first name in large type (waiting and preparing cards share the first column), the number alone when there is no name, re-read on every `/topic/display` signal and every minute while connected, the Screen Wake Lock, and an "Actualizando…" line while the live connection is down or a read failed (the last numbers stay up). No sound, no buttons, no toasts. Recall is offered on the Ready view only, since a handed-over card is in no view. Scheduled cards are hidden by the API until they fire and nothing signals that moment, so the board re-reads every minute while connected | done |
| M2-F3 | **Register**: shifts, blind close, cash movements, payments, quick sale; split (S) | FR-INS-03, 04, 06; FR-REG-01..05; FR-INS-12 (S) | §6.2, §6.3 | Journeys 4 and 7 work end to end. Built: `/staff/register` for `CASHIER` and `ADMIN` (open a shift with a float, the open shift showing no expected cash, cash in and out with a `CASH_OUT` reason and a note rule, the blind close with the count first, the difference and note step with the count read-only, and the closed shift read-only with expected cash, difference and totals per method), `/staff/register/shifts` for `ADMIN` (open shifts named from the staff accounts, forced close with a reason), a "Cobrar" panel in the ticket editor (method, amount, cash received, several payments, a payment by items, the even split from `GET /tickets/{id}/split`, guards for unsent lines and no open shift with a link to the register, hidden for a `SERVER`), void of a payment on an open ticket and of the sale from the closed ticket view through a shared void dialog, `/staff/quick-sale` (pinned items, items with a required modifier group through the shared configurator, name, method, received amount, number and change from the API), staff-home shortcuts per role and the nav entries "Caja", "Venta rápida" and "Turnos abiertos". No backend change was needed: the menu endpoint gives the modifier groups of the pinned items, the currency comes from the menu prices and the time zone from the board thresholds read. The spec covers journeys 4 and 7 at component level, not end to end (no e2e). **Not built:** a list of closed tickets (a sale is voided from its ticket view: the one held after paying, the one the quick-sale result links to, or one opened by its route), paging of the open shifts (one page of 100 holds them all), the comp and merge screens (their backend is M2-B7) | done |
| M2-F4 | **Admin stock**: entries, adjustments, low-stock list | FR-STK-01, 05 | §6.2 | Low-stock list reflects thresholds. Built: `/admin/stock` for `ADMIN` (nav entry "Inventario" and a link from the admin home) with `StockApi` provided by the route. The list shows the tracked products with available, on hand, reserved and the low-stock threshold, sorted by name, a text "Stock bajo" badge, and a "Solo stock bajo" switch that reads `GET /admin/stock-items/low` (emptiest first). The API rows carry no name or unit, so names are joined in the browser with all pages of `GET /admin/products` (a product the catalog no longer lists shows a shortened id) and quantities are plain integers. Tracking is edited only here: a dialog starts tracking a product picked from the untracked catalog products with its threshold, the threshold is edited from its row, and a switch stops tracking (a `409 inventory.stock-reserved` keeps the toast and puts the switch back). Entries (integer 1 to 1,000,000, optional note) and adjustments (signed non-zero change, a reason from `GET /reasons?type=STOCK_ADJUSTMENT`, the note required for "Otro"; `inventory.insufficient-stock` shows the units available now and re-reads the list) send one `Idempotency-Key` per intent, reused on an unchanged retry and released on success. A movements dialog pages the ledger of a product (20 per page, newest first) with type labels, signed changes, the reference id, reason, note, actors named from the staff accounts and times in the store zone. Out-of-range quantities are reported by the form instead of being clamped, the dialogs cannot be closed while saving, and the adjustment dialog shows the units available now and whether the typed change adds or removes units. The copy says that tracking a product with nothing available makes it unavailable (done by the backend; a manual 86 stays independent). No backend change was needed. The spec covers it at component level, not end to end (no e2e). **Not built:** a server-side name search or paging of the stock list (it is one unpaged read), editing tracking from the catalog product form, and units of measure (the API has none) | done |
| M2-F5 | **Comp and merge screens** in the ticket editor (carried over from M2-F1 and M2-F3): a comp dialog with `COMP` reasons for `CASHIER` and `ADMIN`, a merge action that picks another open ticket, the `COMPED` line state and the `MERGED` ticket status | FR-INS-08, FR-INS-11 | §6.2 | Comp and merge work end to end from the ticket editor. Built: a cashier or admin comps a sent line from a dialog with `COMP` reasons (a server gets no action), a comped line reads "Cortesía" in the editor and on the kitchen card, and "Unir cuenta" picks another open ticket; a screen open on a ticket merged elsewhere follows `mergedIntoId` to the survivor. The kitchen keeps preparing a comped line (`comped` flag on the board line). No Playwright journey: the table-service journeys need the running stack | done |
| M2-U1 | **UI base**: shared form field, form dialog, data table, status tag, money and time inputs; one confirm service and one undo notice; shared section stylesheet; safe money formatting | — | §6.7 | The shared pieces exist with unit tests and are mounted once in the workspace layout; views still use their own markup until the next two WPs. Built: `app-form-field` (label, projected control, always-reserved message slot, `aria-describedby`), `app-form-dialog` (save and cancel, busy state that blocks every way to close), `app-data-table` over `p-table` (sort, per-column filter, global search, paging, fixed row height, lazy mode), `app-status-tag`, `app-money-input`, `app-time-input` (`p-datepicker` in time-only mode over `HH:mm` text), `ConfirmAction`, `UndoNotice` and `app-undo-toast` over the existing `UndoQueue`, the `section` stylesheet mixin that replaces the repeated admin, catalog and register rules, a global vertical `resize` rule for textareas, and `safeMinorUnits` / `formatMoneyOrDash`. The actions cell no longer turns the `td` into a flex container | done |
| M2-U2 | **Admin views on the UI base**: lists, forms and dialogs of the admin area adopt the shared pieces; destructive actions ask through `ConfirmAction` | — | §6.7 | Every admin list is an `app-data-table` (the stock list included, the audit one in lazy mode); forms use `app-form-field`, `app-money-input` and `app-time-input`; deactivating or deleting a record asks with its consequences. Catalog, settings, tables, staff and stock are migrated (small forms in `app-form-dialog`, reversible edits through `UndoableChanges`, deactivating a staff account or a category and deleting a modifier group through `ConfirmAction`); the audit log is an `app-data-table` in lazy mode (no column sorts, as the API takes no sort; the page and page size stay in the URL) with its filters in `app-form-field`, and its date range input no longer collapses | done |
| M2-U3 | **Floor views on the UI base**: register, quick sale, ticket editor and payment forms adopt the shared pieces; removing a ticket or quick-sale line uses the undo notice | — | §6.7 | Payment, checkout and shift forms stay inline but use `app-form-field` and `app-money-input`; static result tables use only the shared stylesheet; removing a line shows an undo notice instead of asking. Built: the float, close count, cash movement, payment and checkout amounts use `app-form-field` and `app-money-input`, and a disabled pay, sell, close or save button says why in text; the cash movements and open shifts are `app-data-table`s; adding a movement, forcing a close, transferring, merging, cancelling a ticket and voiding or comping a line run on `app-form-dialog` (aria-busy, no close while sending); removing a ticket line or a cart line goes through `UndoNotice`, the line stays listed as going away (ticket) or leaves the cart and the total at once (quick sale) | done |

### Close M2

| ID | Work package | Done when | Status |
|----|--------------|-----------|--------|
| M2-close | E2E journeys 2, 3, 4, 7 (two browser contexts). Runbook `docs/runbooks/paper-fallback.md` (R7). Release to `prod`. Switch `prod` to power mode `store-hours`. **Pilot: one week in the store in parallel with paper.** After the pilot, back to `on-demand` until launch. **Done (code and docs):** the four journeys, each with its own seeded data and a sweep that settles what a failed run leaves (`e2e/tests/table-service`, `table-change`, `quick-sale`, `end-of-day`), and the runbook. Journey 2 uses three contexts (server on a 10" tablet viewport, board on 1080p, cashier) and covers the void, the labels on the board, Ready, the split into cash and wallet, the closed ticket and the free table; journey 3 covers the transfer with an unsent, a sent and a ready line, also after a reload, and the board card following the new table; journey 4 covers the three frequent items, the name, the cash received, the change, the board and the customer screen through Ready and handover; journey 7 covers the shift opening, the cash out, the blind close, the locked count, the difference and its note. All four ran green twice against the local stack with the rest of the suite (12 of 12). **As built, differing from the PRD wording:** the added line shows `MODIFICADO` and the first send `NUEVO` (FR-PRP-07 labels, the PRD sentence says NEW for both), the in-store board number is `T<n>` for a ticket and `Q<n>` for a quick sale (not `L-017`), and the customer screen cuts a name to its first 20 characters. **Not covered:** the sounds (Web Audio, not observable), the real `SERVER`, `CASHIER` and `ADMIN` split (one `ADMIN` account plays every role, so the role guards stay at component level), and the end of journey 7 (the summary email to the `ADMIN`, the dashboard and the exceptions report: the reports are M4 and the email is not read). **Pending, the owner's operational steps:** release to `prod`; `prod` power mode `store-hours`; the one-week pilot in parallel with paper (print the paper kit of the runbook first, and add a `VOID` reason "Keyed twice"); back to `on-demand` after it; triage of the pilot issues. Gaps the runbook already names for the pilot list: a paper sale cannot be keyed without a card on the board, and the closed-ticket search exists in the API only (no staff screen). Before the pilot the owner also loads the production initial data below, and has read `docs/runbooks/stuck-register-shift.md` and `docs/runbooks/database-restore.md` (the restore is rehearsed on `test` once). | Exit criteria met; pilot issues triaged | todo |

#### Production initial data (before the pilot opens)

`prod` starts with the migrations' defaults only: one station, the seeded store settings and no menu. The owner loads the rest as an `ADMIN` before the first shift, because some of it cannot be changed later (the currency is locked while a shift or ticket is open) and the paper fallback assumes the reasons exist. Tick each line.

- [ ] **Store settings** (**Configuración**): time zone as an IANA region (`America/Lima`, not an offset), currency `PEN` (set it first; it is locked while any shift or ticket is open), register difference threshold, board warning and late minutes, exception threshold.
- [ ] **Opening hours** (**Configuración**): the seven days. `store-hours` power mode follows them, so a wrong day means the environment is off while the store is open.
- [ ] **Tables** (**Mesas**): every table with its name as printed on the floor, an area and a display order. Names are unique and stay reserved even when a table is deactivated. Delivery zones are only needed for online ordering (M3) and can wait.
- [ ] **Menu** (**Catálogo**): stations (the default is "Main"), categories, modifier groups (size, extras, with their price deltas), then products with price, allergens and display order. Pin the three frequent items for the quick sale. Check the prices and the extras against the printed price list that goes in the paper kit.
- [ ] **Stock** (**Inventario**): start tracking only the products that run out, with a low-stock threshold, and record the opening count as an entry. A product that is not tracked is never made unavailable by stock.
- [ ] **Reasons** (**Configuración**): `VOID` reasons "Changed mind", "Keyed twice" and "Other"; `CASH_OUT` reasons such as "Supplier" and "Other"; `STOCK_ADJUSTMENT` reasons such as "Count correction" and "Other"; `COMP` reasons such as "Frequent customer", "Kitchen mistake", "Promotion" and "Other". "Other" always asks for a note. The paper fallback and the cash sheet use these exact names.
- [ ] **Staff** (**Personal**): one account per cashier and server, and a second `ADMIN` besides the owner (the last active `ADMIN` cannot be removed). Each person sets a password from the emailed link; the SES account is still in the sandbox until production access is granted, so a link to an unverified address does not arrive: resend it from **Personal** after the address is verified.
- [ ] **Dry run, once, before opening day**: open a shift with the real float, quick sale of one item, pay it, void the sale with "Changed mind", run its board card to the end, close the shift with the counted cash. The audit log keeps the trace, which is expected; do not repeat it on the real opening day.
- [ ] **Paper kit** printed as in `docs/runbooks/paper-fallback.md`, with the price list from this menu.

---

## M3 — Online

**Before starting:** Q2 answered (invoicing; if required, add an `invoicing` module first — tech-spec R4), payment provider account and sandbox ready, SES production access granted.

### Backend

| ID | Work package | Scope | Spec | Done when | Status |
|----|--------------|-------|------|-----------|--------|
| M3-B1 | **identity — customers**: registration, email verification, password reset, account deletion with anonymization | FR-ONL-01, BR-07 | §7.1 | Deleted account keeps anonymized orders | todo |
| M3-B2 | **notifications — customer emails**: order, verification, password-reset, and refund templates on the email port (created in M2-B6, or here if M2-B6's email was deferred) | FR-ONL-06 (email) | §4.2, §6.6, D16 | Each customer email renders from a template; no text in Java | todo |
| M3-B3 | **payments**: `PaymentGateway` port, Mercado Pago adapter (Checkout Pro, binary mode), webhook (signature + fetch), reconciliation job, full and partial refunds, late approval; metrics export to CloudWatch (`micrometer-registry-cloudwatch2`, `MeterFilter` allowlist in `shared`, `prod` only, `cloudwatch:PutMetricData` on the `prod` task role) with the first exported metrics `payments.failed` and `webhooks.lag` | FR-ONL-05, 09; BR-02, BR-05 | §4.3 "Online checkout", §12 | WireMock tests: approved/rejected, bad signature, timeouts, partial refunds, late approval; only allowlisted meters reach CloudWatch (≤ 15 series) | todo |
| M3-B4 | **ordering**: checkout (re-pricing, zones, hours, online mode), reservations + expiry job, estimates, run-out preference, status machine, delivery failure/retry, short numbers; min order/free delivery (S), auto-pause (S), scheduling (S), history/re-order (S), customer cancel (S) | FR-ONL-02..04, 08, 10–14; (S) 07, 15–18; FR-STK-03 | §4.3, §5.2 | Checkout never reads from cache; expired unpaid orders release stock | todo |
| M3-B5 | **Board integration for online orders**: `OrderPaid` → board, online run-out resolution (replace / refund / contact), pickup verification, busy mode/pause, `/user/queue/orders` | FR-ONL-12–14, BR-08, BR-09 | §4.3 "Item runs out", §4.4 | Customer cancel vs staff start race passes; refund amount equals removed line snapshots | todo |

### Frontend

| ID | Work package | Scope | Spec | Done when | Status |
|----|--------------|-------|------|-----------|--------|
| M3-F1 | **Storefront**: menu (SSR), product detail with configurator and allergens | FR-CAT-02, 06 (customer view) | §6.1 | Menu LCP ≤ 2.5 s on mobile (NFR-02); WCAG 2.2 AA (axe) | todo |
| M3-F2 | **Cart and checkout**: cart in `localStorage`, zones, estimate, run-out preference, `Idempotency-Key` per intent, payment redirect and return; scheduling (S) | FR-ONL-02..05, 11, 13; (S) 17 | §6.4, §6.5 | Journey 1 up to the payment redirect; retrying "Pay" reuses the key | todo |
| M3-F3 | **Customer account**: register/verify/reset, live order tracking, account deletion; history/re-order (S), cancel (S) | FR-ONL-01, 06, 10; (S) 07, 15 | §6.4 | Status changes appear live without reload | todo |
| M3-F4 | **Staff and admin additions**: busy mode/pause, online run-out resolution, delivery actions, pickup verification; ADMIN order cancel and full/partial refund | FR-ONL-09, 12, 14; BR-08 | §6.3 | Journeys 5 and 6 work end to end; ADMIN refunds a single line with a reason | todo |

### Close M3

| ID | Work package | Done when | Status |
|----|--------------|-----------|--------|
| M3-close | E2E journeys 1, 5, 6. **20 real test orders paid and refunded in `test`.** Runbook `docs/runbooks/payment-webhook-replay.md`. Release to `prod`. | Exit criteria met | todo |

---

## M4 — Dashboard & launch

**Before starting:** Q3 answered (audit retention).

### Backend

| ID | Work package | Scope | Spec | Done when | Status |
|----|--------------|-------|------|-----------|--------|
| M4-B1 | **reporting — queries**: today dashboard, heatmap, sales and shift reports, exceptions by employee; CSV streaming (S) | FR-RPT-01, 02, 04–06; FR-RPT-03 (S) | §4.9 | Same-weekday comparison and business day in store time zone are correct | todo |
| M4-B2 | **Hardening**: OWASP checklist, Trivy/gitleaks clean, rate-limit tuning, alarms, audit retention per Q3 | NFR-08, NFR-09 | §7, §10.4, §12 | No open CRITICAL/HIGH findings; alarms fire in a test | todo |

### Frontend

| ID | Work package | Scope | Spec | Done when | Status |
|----|--------------|-------|------|-----------|--------|
| M4-F1 | **Admin dashboard and reports**: today dashboard, heatmap, reports, exceptions, CSV export (S) | FR-RPT-01..06 | §6.2 | Journey 7's review step works end to end | todo |

### Close M4 — launch

| ID | Work package | Done when | Status |
|----|--------------|-----------|--------|
| M4-close | k6 load test with scale-out step (NFR-05); backup-restore drill (NFR-07); remaining runbooks (`database-restore`, `stuck-register-shift`, `pipeline-debugging`); launch checklist; release to `prod`; **switch `prod` to power mode `always-on`**; raise `budget_limit_usd` from 70 to 120 | FR-RPT done; every M requirement done; NFR-05 and NFR-07 verified; `prod` never stopped by `env-autostop.yml` | todo |
