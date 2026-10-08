# CLAUDE.md — e2e

Playwright journeys that drive the real frontend and backend through the browser. Repo-wide rules are in the root `CLAUDE.md`; test strategy in `docs/tech-spec.md` §11.

## Stack

`@playwright/test` pinned to an exact version (Apache-2.0) · TypeScript strict · Chromium · Node 24. It is the only dependency; do not add another without the owner's approval (root `CLAUDE.md`). `package-lock.json` is committed; use `npm ci`.

## Configuration (environment variables only)

| Variable | Default | Purpose |
|----------|---------|---------|
| `E2E_BASE_URL` | `http://localhost:4200` | Frontend origin |
| `E2E_API_URL` | `http://localhost:8080` | Backend origin, used only for seeding and cleanup |
| `E2E_ADMIN_EMAIL` | none, required | An existing `ADMIN` account |
| `E2E_ADMIN_PASSWORD` | none, required | Its password |

The run fails before starting a browser when either credential is missing. **Credentials never go in a file**: not in `.env`, config, fixtures, docs, or commit messages. Export them in a shell session from a password manager (never typed inline on the command line, which lands in shell history and the process list), or take them from the CI secret source (in CI the password comes from SSM Parameter Store, see below).

## Run locally

```bash
docker compose up -d                        # from the repo root: PostgreSQL + Mailpit
cd backend && ./mvnw spring-boot:run        # http://localhost:8080
cd frontend && npm start                    # http://localhost:4200
cd e2e && npm ci && npx playwright install chromium
export E2E_ADMIN_EMAIL=... E2E_ADMIN_PASSWORD=...   # from a password manager
npx playwright test
```

`tsconfig.json` strictness is editor-only: there is no typecheck script and `typescript` is not a dependency.

Output is the terminal only (`list` locally, `github` and `line` in CI). Stop the servers when done. `npm run check:config` fails if the config enables a step-persisting reporter, trace, video or screenshot, or drops `PLAYWRIGHT_NO_COPY_PROMPT`; it reads the config text with regular expressions, so it only sees literal settings.

## In CI

`cd-test.yml` runs the suite in the `e2e` job after `smoke`, and `record` waits for it, so a red suite blocks promotion. The job runs only when the `test` environment variable `E2E_ENABLED` is `true`. It reads the password of a dedicated `ADMIN` account from the SSM SecureString `/jugueria/test/e2e/admin-password` with the `test` deploy role, masks it, and exports it only for the `playwright test` step; `E2E_BASE_URL`, `E2E_API_URL` and `E2E_ADMIN_EMAIL` are `test` environment variables. Setup: `docs/runbooks/e2e-setup.md`.

## Layout

```
playwright.config.ts   projects: setup, then chromium (journeys)
support/               env, session (sign-in), api (seed and cleanup), select (p-select helper)
tests/auth.setup.ts    login smoke only: signs in through /login and saves no state
tests/env.spec.ts      pure tests of the target guard; no browser or server needed
tests/*.journey.ts     one file per journey
```

## Rules

- **Sessions.** The refresh token is a rotating cookie and reusing a rotated one revokes the whole session family, so a saved `storageState` cannot be shared by two contexts or by two runs. Every context signs in through `/login` (`openAdminPage`). An `ADMIN` can do floor-staff work, so one account covers every journey.
- **No secrets in artifacts.** Playwright records every typed value as a step title (`Fill "<value>"`), and html, json and blob reports and traces persist those steps even with tracing off. So only `list` (local) and `github`/`line` (CI) reporters are used, trace, video and screenshot are off, and `test-results` and reports must never be uploaded. `PLAYWRIGHT_NO_COPY_PROMPT` is set in the config because the failure snapshot of the login page would include the typed password. Do not turn any of this on for projects that sign in, and do not assert on the login page with a matcher that attaches a page snapshot.
- **Selectors.** Role, label and visible text (Spanish copy as shown). No CSS classes. `p-select` options are `role=option` in a body-appended overlay; use `pickOption`. Availability switches are `role=switch` named after the item; click the switch, not its label.
- **Waiting.** Web-first assertions only; no `waitForTimeout`. The 5 s propagation window of the availability journey is the requirement: do not loosen it.
- **Data.** Every name starts with `e2e-<timestamp>-` (`uniqueName`). Each test creates what it needs, through the UI or through the API with a separate admin sign-in (`AdminApi`).
- **Target guard.** The run refuses a non-local `E2E_BASE_URL` or `E2E_API_URL` unless `E2E_ALLOW_REMOTE=1`, and refuses production hosts even then (`support/target.ts`, tested by `tests/env.spec.ts`).
- **Cleanup.** `afterAll` calls `AdminApi.cleanUp()`, which handles everything whose name matches `E2E_NAME` (`^e2e-\d{13}-`, case-sensitive), whichever run left it. Items are processed independently and failures are thrown together at the end; a group still attached to a product the suite did not create is skipped with a warning. The groups and categories endpoints are unpaged. Handled: products are detached from their modifier groups and deactivated, modifier groups are deleted, categories are deactivated. Products and categories cannot be deleted through the API, so inactive `e2e-*` rows accumulate in the target environment; never run two suites against the same environment at once, because the sweep is by prefix.
- **Flakiness.** `workers: 1`; one retry in CI only. A retry reopens its own sessions and creates fresh data, so it is safe.
