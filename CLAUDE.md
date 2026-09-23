# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## What this is

Monorepo for a juice bar omnichannel platform: online ordering and in-store tickets sharing **one catalog, one stock, and one preparation board**. It is also the practice project for a GitHub Actions course, so CI/CD, multi-environment delivery, supply-chain security, and rollback are first-class goals, not afterthoughts.

Source of truth:

- `docs/PRD.md` — **what** is built and why. Requirement IDs: `FR-*`, `NFR-*`, `BR-*`.
- `docs/tech-spec.md` — **how** it is built. Decisions at a glance in §1, rejected alternatives in §13.
- `docs/implementation-plan.md` — **in which order**: milestones split into work packages (WP), with status. Work only on the current WP; update its status in the same PR.

Read the relevant spec section before designing anything. PR descriptions reference the requirement IDs and the work package they implement; code and tests do not. If an implementation needs to deviate from the tech-spec, update the spec in the same PR.

## Repository layout

Each area has its own `CLAUDE.md` with local rules — read it before working there:

| Path | Contents | Local guide |
|------|----------|-------------|
| `backend/` | Spring Boot modular monolith (Java 25) | `backend/CLAUDE.md` |
| `frontend/` | Angular 22 app with hybrid SSR | `frontend/CLAUDE.md` |
| `db/` | Flyway migrations (`db/migration/<module>/`) and database conventions | `db/CLAUDE.md` |
| `infra/` | Terraform for AWS foundation resources | `infra/CLAUDE.md` |
| `docs/` | PRD, tech-spec, runbooks | — |
| `.github/` | Workflows, composite actions (planned, tech-spec §10) | — |
| `e2e/` | Playwright tests (planned) | — |
| `compose.yaml` | Local PostgreSQL 18 + Mailpit, started by Spring Boot's Docker Compose support | — |

**Current state:** early scaffold. CI and the backend and frontend skeletons exist; most of the tech-spec (business modules, CD workflows, Terraform, `e2e/`) is not implemented yet; tech-spec §3 "Scaffold changes at M0" lists the pending setup. Never assume a file described in the spec exists — check first.

## Deployment overview

Two deployables — `backend` and `frontend` — each built as one Docker image and run on AWS ECS Express Mode (Fargate), behind **one ALB shared by `test` and `prod`**, with one RDS PostgreSQL 18 instance per environment. Local development uses Docker PostgreSQL, Mailpit, and a real S3 dev bucket (tech-spec §8.5). Architecture details belong to each area's guide.

## Branching and delivery

```
feature/<name> ── PR (squash) ──▶ develop ── push ──▶ TEST  (automatic, no approval)
                                     │
                                     └── PR (merge commit) ──▶ main ── push ──▶ PROD  (manual approval)
                                                                ▲
hotfix/<name> ─────────── PR (squash) ──────────────────────────┘
                          then: PR main → develop (merge commit)
```

| Branch | Created from | Merges into | Merge method | Deploys to |
|--------|--------------|-------------|--------------|------------|
| `feature/*`, `fix/*`, `chore/*`, `docs/*`, `refactor/*` | `develop` | `develop` | Squash | — |
| `develop` (long-lived) | — | `main` via release PR | **Merge commit** | `test` on every push |
| `main` (long-lived) | — | `develop` via back-merge PR after a hotfix | **Merge commit** | `prod` on every push, after approval |
| `hotfix/*` | `main` | `main` | Squash | — |

Rules:

- Work branches are short-lived (≈ 2 days) and always target `develop`. Only `hotfix/*` and release PRs from `develop` target `main`.
- **Never squash `develop → main` or `main → develop`.** Squashing between long-lived branches rewrites their commits, so both branches diverge and every following release PR conflicts.
- After every hotfix merged into `main`, open the `main → develop` back-merge PR immediately; otherwise the next release reverts the fix.
- Both `develop` and `main` are protected: PR only, required check `ci-ok` green, no force-push, no deletion. `main` also requires the PR to come from `develop` or `hotfix/*` (checked in CI).
- **`develop` is the default branch**, so PRs, Dependabot (config and security updates), and scheduled workflows use it.
- **Build once, promote the digest.** Images are built on `develop` (tagged with the commit SHA) and verified on `test`. A push to `main` deploys the digests already verified for the merged `develop` commit (`HEAD^2`) when its tree is identical to `main`'s; it builds new images only when the trees differ (hotfix). `prod` never gets an image that was rebuilt from the same code.

## CI/CD in the monorepo

Apps are tested, built, and deployed **independently**: `backend` (`backend/**` + `db/**`), `frontend` (`frontend/**` + `backend/api/openapi.json`), `infra` (`infra/**`). A change that touches only the frontend runs only frontend CI and deploys only the frontend image.

- **One CI workflow, job-level filters.** `ci.yml` has a `changes` job that detects affected areas; area jobs (calling reusable `_ci-backend.yml`, `_ci-frontend.yml`, `_ci-infra.yml`) run with `if:` on its outputs. Never use `on.pull_request.paths` on a workflow that must be a required check: when it does not start, GitHub waits for it forever and the PR cannot merge.
- **Single required check: `ci-ok`.** It needs every job, runs with `if: always()`, and fails when any needed job is `failure` or `cancelled`. Without `if: always()`, a failed job makes `ci-ok` skipped, a skipped job reports success, and broken code merges.
- Changes to `.github/**` run all area jobs.
- **Deploy only what differs.** `cd-test.yml` compares each app with the commit last deployed successfully to `test` (not with the previous commit, so failed runs are retried). `cd-prod.yml` compares each app's digest with the one running in `prod`, because a release PR bundles many commits.
- E2E tests on `test` always exercise the whole system, whichever app was deployed.
- Hotfixes reach `prod` without passing through `test`; they get the full PR CI plus post-deploy smoke tests, and must stay minimal.
- GitHub environments: `test` accepts deployments only from `develop` (`cd-test.yml`); `prod` only from `main` (`cd-prod.yml`), with a required reviewer.
- **Environments are off by default** (power mode `on-demand`, tech-spec §8.1): deploy workflows start them, `env-control.yml` starts them manually, `env-autostop.yml` stops them. `prod` becomes `always-on` at launch. Expect a few minutes of startup when an environment is off.

## General rules

- **Language:** code, identifiers, comments, docs, and commits in English. End-user UI text in Spanish via Angular i18n.
- **TDD:** a failing test precedes each production change (tech-spec §11).
- **Code comments:** minimal and only when necessary — explain a non-obvious *why* (a constraint, a workaround, a surprising rule) in one short line. No comments that restate the code, no long explanatory blocks, and no references to documentation (no "see tech-spec §4.3", requirement IDs, or links). Names and tests carry the meaning; design rationale lives in `docs/`.
- **Commits and PR titles:** Conventional Commits (validated in CI).
- **Unfinished work** hides behind feature flags stored in the database (tech-spec §9.2), never behind long-lived work branches.
- **Secrets** live in AWS SSM Parameter Store under `/jugueria/<env>/…`, injected at runtime. Never in the repo, images, or GitHub secrets (GitHub holds only role ARNs and non-sensitive variables).
- **API contract:** the backend OpenAPI spec is committed as `backend/api/openapi.json`; the frontend TypeScript types are generated from it. API changes are always explicit in the PR diff.
- **Budget:** `test` + `prod` must stay under USD 130/month (NFR-13). Any new AWS resource needs a cost justification.

## Dependencies

Full policy in tech-spec §3.

- **Never add a dependency (Maven, npm, or GitHub Action) on your own.** Propose it to the owner with: the problem, why the platform (JDK, Spring Boot starters, Angular, Web APIs) is not enough, alternatives, license, maintenance activity, and — for the frontend — bundle size.
- Prefer the platform: `Intl`, `crypto.randomUUID()`, `java.time`, `RestClient`.
- Backend versions come from BOMs; unmanaged ones are declared once in `<properties>`. No version ranges, `SNAPSHOT`, or milestones on `develop`/`main`.
- Frontend: `package-lock.json` is committed; CI uses `npm ci`.
- Licenses: direct dependencies MIT, MIT-0, Apache-2.0, BSD, or ISC, with no exceptions; transitive ones may use the other permissive licenses listed in tech-spec §3. PrimeNG is used in its last MIT line (21); PrimeNG 22+ is not MIT, so never upgrade to it without owner approval (tech-spec §6.7).
- Already rejected, do not propose again: jjwt, MapStruct, H2, Redis/broker clients, NgRx or any global store, runtime OpenAPI client generators, `uuid`, `lodash`, `moment`.
