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
| M0-06 | **CD**: `_build-image.yml`, `_deploy-ecs.yml`, `cd-test.yml` (per-app, digest recording), `cd-prod.yml` (digest promotion, approval, tag + release), smoke tests, `rollback.yml`; **power modes**: `env-control.yml` + `env-autostop.yml`, both environments `on-demand` | §8.1, §9.3, §10.1–10.5 | Both apps run on ECS Express Mode in `test` and `prod` **behind one shared ALB** (validates D15/R1); a release reuses the `test` digests; rollback to a previous SHA works; deploying to a stopped environment starts it; an environment started for 4 h is stopped automatically; each frontend service sets `NG_ALLOWED_HOSTS` to its own host | doing |
| M0-close | **Close M0**: exit criteria; cost validated with the AWS Pricing Calculator; runbook `docs/runbooks/rollback.md` | §8.3, §10.5 | Exit criteria met; estimated cost within NFR-13 | todo |

---

## M1 — Catalog & staff

**Before starting:** Q1 answered (country, currency, payment provider). **During M1:** request SES production access (tech-spec R5).

### Backend

Order matters: foundations and `audit` come first, so every later module is audited from its first command.

| ID | Work package | Scope | Spec | Done when | Status |
|----|--------------|-------|------|-----------|--------|
| M1-B1 | **Shared foundations**: `Money`, `Ids`, `Clock`, base entity, `CurrentActor`, `ErrorCode`/`BusinessException` + global advice, `PageResponse`, correlation ID, `MeterFilter` allowlist, ArchUnit rules (`@PreAuthorize`, no `now()`) | — | §4.11–4.13, §5.1, §5.3, §7.1, §12 | Error bodies match §5.1 for MVC, validation, and unexpected errors; ArchUnit rules fail on violations | todo |
| M1-B2 | **identity — staff**: login, JWT (`NimbusJwtEncoder`/resource server), refresh rotation cookie, lockout, `SecurityFilterChain`, staff accounts and roles, deactivation revokes sessions, first-admin bootstrap command | FR-ADM-01 | §7, §7.1, §6.4 | Allowed/denied-role tests per endpoint; deactivated user loses access within 15 min | todo |
| M1-B3 | **audit**: `audit_log` (partitioned), synchronous listener, append-only grants, search endpoint | FR-AUD-01/02 | §4.7 | Audit failure rolls back the change; `UPDATE/DELETE` on `audit_log` fails for role `app` | todo |
| M1-B4 | **store**: settings, opening hours, delivery zones, reason lists, board thresholds | FR-ADM-02, FR-INS-14 | §4.1, §4.8 | Settings editable with `ETag`/`If-Match`; changes audited | todo |
| M1-B5 | **notifications — real time**: STOMP `/ws`, `ChannelInterceptor` auth, `LISTEN/NOTIFY` bridge, `/topic/catalog`, `/topic/store-status` | — (enables FR-CAT-03, NFR-04) | §4.4 | Change on instance A reaches a subscriber on instance B in ≤ 5 s (test with two instances) | todo |
| M1-B6 | **catalog**: categories, stations, products, modifier groups/options, allergens, availability ("86"), menu endpoint with cache + `ETag`, product images (S3 + CloudFront) | FR-CAT-01..07 | §4.6, §4.8 | Min/max modifier rules enforced; availability visible to subscribers in ≤ 5 s; menu cache evicted on every task | todo |
| M1-B7 | **instore — tables**: table configuration and grid endpoint (all tables free until M2) | FR-INS-09 (config) | §4.8 | ADMIN manages tables; grid endpoint returns every table | todo |

Infra in M1: `test`/`prod` S3 media buckets + CloudFront (the local-development bucket and permission set already exist from M0-05).

### Frontend

| ID | Work package | Scope | Spec | Done when | Status |
|----|--------------|-------|------|-----------|--------|
| M1-F1 | **Core**: login, auth interceptor (single-flight refresh), role guards, error interceptor + `code` map, API types (`openapi-typescript` + `api:generate`, TypeScript 6 compatibility — tech-spec §6.5), `Money`/time utils, STOMP client, staff/admin shell, i18n setup, theme tokens with light/dark mode, Tabler icons, PrimeNG 21 setup on Angular 22 (scoped `overrides`) | — | §5.1, §6.2–6.7 | Session restores on reload; expired token refreshes once; errors show localized messages; PrimeNG 21 builds, renders with SSR, and passes component smoke tests on Angular 22 (if not, stop and ask the owner) | todo |
| M1-F2 | **Admin catalog**: categories, products, modifiers, allergens, stations, images, availability | FR-CAT-01, 05–07 | §6.2 | ADMIN creates a product with modifier groups end to end | todo |
| M1-F3 | **Admin store & staff**: settings, opening hours, zones, reason lists, tables, staff accounts | FR-ADM-01/02, FR-INS-09/14 | §6.2 | Each setting round-trips; `412` on concurrent edits handled | todo |
| M1-F4 | **Staff availability ("86")** screen | FR-CAT-03 | §6.3 | Toggle reflects on another open screen in ≤ 5 s | todo |
| M1-F5 | **Audit log viewer** | FR-AUD-02 | §6.2 | Search by date, actor, entity | todo |

### Close M1

| ID | Work package | Done when | Status |
|----|--------------|-----------|--------|
| M1-close | E2E: admin builds a product with modifiers; staff toggles availability. Exit criteria. Release to `prod`. | Exit criteria met; release tagged | todo |

---

## M2 — In-store

**Before starting:** Q5 answered (stations). **Before M2-B6:** email template engine decided (tech-spec §6.6).

### Backend

| ID | Work package | Scope | Spec | Done when | Status |
|----|--------------|-------|------|-----------|--------|
| M2-B1 | **Idempotency and jobs**: `shared` idempotency API, scheduled-job pattern (`SKIP LOCKED`), expired-key cleanup | — | §5.2, §4.3 | Same key sent concurrently to two instances creates one result | todo |
| M2-B2 | **inventory**: stock items, entries, adjustments, movement ledger, low-stock list, auto-unavailable at 0, returns; reserve/commit/release API (used by M3) | FR-STK-01..05, BR-10 | §4.3, §4.8 | Parallel sale of the last unit sells it once; every change has a ledger row | todo |
| M2-B3 | **preparation — board**: projection, views (tickets, all-day, ready), start/ready/undo/recall/handover, markers + ack, run-out flag, timestamps, `/topic/board`; station filter (S); customer display (S) | FR-PRP-01..10, BR-08 (in-store) | §4.5, §4.3 "Board state" | Races in tech-spec §11 pass; all-day aggregation groups identical items only | todo |
| M2-B4 | **instore — tickets**: tickets, lines, send, void rules by state, transfer, table grid status; comp (S), merge (S), repeat (S) | FR-INS-01, 02, 05, 07, 09, 10; FR-INS-08/11/13 (S) | §4.3 "In-store tickets" | Void by SERVER after Ready is rejected; transfer to an occupied table is rejected | todo |
| M2-B5 | **instore — payments and register**: shifts (open, blind close, force close), cash movements, payments + void, quick sale, sale void, stock deduction at close; split bill (S) | FR-INS-03, 04, 06; FR-REG-01..05; FR-INS-12 (S) | §4.3 "Register shift" | Expected cash is never returned before counted cash; split rounding per spec | todo |
| M2-B6 | **reporting — data capture**: listeners filling `sales_line`, `exception_event`, `shift_summary`; shift summary email to ADMIN (S) — first email: email port, SMTP adapter (Mailpit), SES adapter (task role), `test` recipient allowlist | FR-REG-06 (S); data for FR-RPT | §4.9, D16 | Every in-store sale, void, comp, and shift produces read-model rows | todo |

> **Why reporting capture is here and not in M4:** the store uses the system from M2 on. Listeners only receive events published while they exist, so sales made before `reporting` exists would never reach the reports. Capture data now; build the report screens in M4.

### Frontend

| ID | Work package | Scope | Spec | Done when | Status |
|----|--------------|-------|------|-----------|--------|
| M2-F1 | **Table grid and ticket editor** (10" tablet), shared **product configurator** | FR-INS-01, 02, 07, 09, 10; (S) 08, 11, 13 | §6.2, §6.3 | Journey 2 up to "lines Ready" works on a tablet viewport | todo |
| M2-F2 | **Board**: views, sound, wake lock, disconnected state, undo, age colors, markers, run-out flag; customer display (S) | FR-PRP-01..09 | §6.3, §6.5 | Disconnect blocks actions until state is re-fetched; readable at 2 m on 1080p | todo |
| M2-F3 | **Register**: shifts, blind close, cash movements, payments, quick sale; split (S) | FR-INS-03, 04, 06; FR-REG-01..05 | §6.2 | Journeys 4 and 7 work end to end | todo |
| M2-F4 | **Admin stock**: entries, adjustments, low-stock list | FR-STK-01, 05 | §6.2 | Low-stock list reflects thresholds | todo |

### Close M2

| ID | Work package | Done when | Status |
|----|--------------|-----------|--------|
| M2-close | E2E journeys 2, 3, 4, 7 (two browser contexts). Runbook `docs/runbooks/paper-fallback.md` (R7). Release to `prod`. Switch `prod` to power mode `store-hours`. **Pilot: one week in the store in parallel with paper.** After the pilot, back to `on-demand` until launch. | Exit criteria met; pilot issues triaged | todo |

---

## M3 — Online

**Before starting:** Q2 answered (invoicing; if required, add an `invoicing` module first — tech-spec R4), payment provider account and sandbox ready, SES production access granted.

### Backend

| ID | Work package | Scope | Spec | Done when | Status |
|----|--------------|-------|------|-----------|--------|
| M3-B1 | **identity — customers**: registration, email verification, password reset, account deletion with anonymization | FR-ONL-01, BR-07 | §7.1 | Deleted account keeps anonymized orders | todo |
| M3-B2 | **notifications — customer emails**: order, verification, password-reset, and refund templates on the email port (created in M2-B6, or here if M2-B6's email was deferred) | FR-ONL-06 (email) | §4.2, §6.6, D16 | Each customer email renders from a template; no text in Java | todo |
| M3-B3 | **payments**: `PaymentGateway` port, Mercado Pago adapter (Checkout Pro, binary mode), webhook (signature + fetch), reconciliation job, full and partial refunds, late approval | FR-ONL-05, 09; BR-02, BR-05 | §4.3 "Online checkout" | WireMock tests: approved/rejected, bad signature, timeouts, partial refunds, late approval | todo |
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
