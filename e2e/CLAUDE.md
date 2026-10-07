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

The run fails before starting a browser when either credential is missing. **Credentials never go in a file**: not in `.env`, config, fixtures, docs, or commit messages. Pass them on the command line or from the CI secret source.

## Run locally

```bash
docker compose up -d                        # from the repo root: PostgreSQL + Mailpit
cd backend && ./mvnw spring-boot:run        # http://localhost:8080
cd frontend && npm start                    # http://localhost:4200
cd e2e && npm ci && npx playwright install chromium
E2E_ADMIN_EMAIL=... E2E_ADMIN_PASSWORD=... npx playwright test
```

Reports: terminal list plus `playwright-report/` (HTML, not opened automatically). Stop the servers when done.

## Layout

```
playwright.config.ts   projects: setup, then chromium (journeys)
support/               env, session (sign-in), api (seed and cleanup), select (p-select helper)
tests/auth.setup.ts    signs in through /login once; the journeys depend on it
tests/*.journey.ts     one file per journey
```

## Rules

- **Sessions.** The refresh token is a rotating cookie and reusing a rotated one revokes the whole session family, so a saved `storageState` cannot be shared by two contexts or by two runs. Every context signs in through `/login` (`openAdminPage`). An `ADMIN` can do floor-staff work, so one account covers every journey.
- **No secrets in artifacts.** Traces, videos and screenshots are off because they record typed values, and `PLAYWRIGHT_NO_COPY_PROMPT` is set in the config because the failure snapshot of the login page would include the typed password. Do not turn any of them on for projects that sign in, and do not assert on the login page with a matcher that attaches a page snapshot.
- **Selectors.** Role, label and visible text (Spanish copy as shown). No CSS classes. `p-select` options are `role=option` in a body-appended overlay; use `pickOption`. Availability switches are `role=switch` named after the item; click the switch, not its label.
- **Waiting.** Web-first assertions only; no `waitForTimeout`. The 5 s propagation window of the availability journey is the requirement: do not loosen it.
- **Data.** Every name starts with `e2e-<timestamp>-` (`uniqueName`). Each test creates what it needs, through the UI or through the API with a separate admin sign-in (`AdminApi`).
- **Cleanup.** `afterAll` calls `AdminApi.cleanUp()`, which handles everything named `e2e-*`, whichever run left it: products are detached from their modifier groups and deactivated, modifier groups are deleted, categories are deactivated. Products and categories cannot be deleted through the API, so inactive `e2e-*` rows accumulate in the target environment; never run two suites against the same environment at once, because the sweep is by prefix.
- **Flakiness.** `workers: 1`; one retry in CI only. A retry reopens its own sessions and creates fresh data, so it is safe.
