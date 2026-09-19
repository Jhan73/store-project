# CLAUDE.md — frontend

Angular app with hybrid SSR. Repo-wide rules are in the root `CLAUDE.md`; design details in `docs/tech-spec.md` §6.

**Angular coding rules live in `.claude/CLAUDE.md`** (standalone components without `standalone: true`, `input()`/`output()`, OnPush, signals, native control flow, `inject()`, no `ngClass`/`ngStyle`, `host` object instead of `@HostBinding`/`@HostListener`, `NgOptimizedImage`). Follow them for all code here. This file adds the project-specific rules.

## Stack

Angular 22 · zoneless · signals · SSR with Express (`src/server.ts`) · Vitest · Node 24 LTS (`.nvmrc`) · SCSS.

## UI and theming (tech-spec §6.7)

**Component library:** **PrimeNG 21** (`primeng` pinned to the exact `21.1.x` community release — never a `-lts` version, which is commercial), `@primeuix/themes` 2.x, and `@angular/cdk` 22. All MIT; no license key. No other component library.
- PrimeNG 21 declares Angular 21 peers. It is installed on Angular 22 through `overrides` in `package.json`, scoped to `primeng` only — never with a global `legacy-peer-deps`.
- This combination is not supported by the vendor. If a PrimeNG component misbehaves, check first whether it is an Angular 22 incompatibility, and report it to the owner instead of patching around it: the fallback is migrating to PrimeNG 22 (tech-spec §6.7).
- **Do not upgrade to PrimeNG 22** without the owner's approval: it uses a different license (PrimeUI, key required).
- Styled mode with one custom preset (`definePreset`). The preset and the app's semantic tokens share one palette; do not restyle PrimeNG components with ad-hoc CSS.
- PrimeNG's `darkModeSelector` is the same `<html>` class used for the app's dark mode, so both switch together.

**Icons:** Tabler, through `@tabler/icons-angular` (official, MIT). Import each icon individually (tree-shaking); no icon fonts, no other icon sets. Decorative icons are `aria-hidden="true"`; icon-only buttons need an i18n `aria-label`.

**Light and dark mode:**
- Both modes are supported everywhere. Default follows `prefers-color-scheme`; the user's choice is stored in `localStorage` and applied as a class on `<html>`.
- The class is applied by an inline script in `index.html` before first paint, so SSR pages never flash the wrong theme.
- Every screen meets WCAG AA contrast in **both** modes.

### Styling

- Use CSS variables for all theme tokens (colors, radius, shadows, spacing).
- Prefer semantic tokens (`--background`, `--foreground`, `--primary`, etc.).
- Avoid hardcoded colors.
- Tokens are defined once, in the global theme stylesheet, with a light and a dark value each. Components only consume `var(--token)`; they never define colors of their own.

Pending (tech-spec §3): angular-eslint, removing leftover Karma/Jasmine packages from `package.json`, replacing the `**` prerender route, `withEventReplay()` → `withIncrementalHydration()`, and `openapi-typescript` with the `api:generate` script (§6.5).

## Commands

```bash
npm start                                   # ng serve on http://localhost:4200
npm run build                               # production SSR build → dist/frontend
npm run serve:ssr:frontend                  # run the built SSR server
npm test                                    # Vitest via @angular/build:unit-test
npx ng test --include src/app/app.spec.ts   # single spec file
npx ng extract-i18n                         # extract UI text
```

Tests use Vitest (`vitest/globals`). Do not write new tests against Jasmine APIs. Prettier config is in `package.json` (printWidth 100, single quotes).

## Structure (screaming architecture)

```
src/app/
├── core/        auth (token store, interceptors, guards), api (generated types), realtime (STOMP), error handling
├── shared/ui/   presentational components — no injected services
└── features/    one folder per business capability, lazy-loaded
    ├── storefront/  cart/  checkout/  account/  orders/
    ├── staff/       tables, tickets, quick-sale, board, register-shift
    ├── display/     customer-facing Preparing/Ready screen
    └── admin/       dashboard, catalog, tables, reasons, stock, users, settings, reports, audit
```

Folders are named after business features, never after technical types (`components/`, `services/`).

**File and class naming** (Angular style guide v20+, tech-spec §6.2):
- No type suffix for components, directives, and services: `order-list.ts` → `class OrderList`. Never `order-list.component.ts` / `OrderListComponent`.
- Other types use a hyphenated suffix: `auth-guard.ts`, `price-pipe.ts`, `error-interceptor.ts`.
- Services are named by role: `<Feature>Api` for HTTP data access (`orders-api.ts` → `OrdersApi`), `<Feature>Store` for signal state (`cart-store.ts` → `CartStore`).
- Tests: `*.spec.ts` next to the file.

## Rules

**Rendering (`app.routes.server.ts`)**

| Routes | Mode |
|--------|------|
| `/`, `/legal/*` | `Prerender` |
| `/menu`, `/menu/:category` | `Server` (SEO + fresh data; `@defer` with incremental hydration) |
| `/cart`, `/checkout`, `/account/**`, `/orders/**`, `/staff/**`, `/display`, `/admin/**` | `Client` |

SSR never renders authenticated content, so tokens never exist on the SSR server.

**Components and state**
- Container/presentational: route components orchestrate; `shared/ui` components only receive `input()` and emit `output()`.
- Feature state lives in signals inside **feature-scoped services** provided in the feature route's `providers`. Only cross-feature services (auth, API client, realtime) use `providedIn: 'root'`. This intentionally narrows the default in `.claude/CLAUDE.md`.
- No global store library.
- Reactive Forms (Signal Forms only once stable).
- The product configurator is one shared component used by storefront and ticket editor, driven by modifier-group rules (required, min/max).

**Data and API** (tech-spec §6.5)
- API types are generated from `backend/api/openapi.json` with `openapi-typescript` into `src/app/core/api/schema.d.ts` (`npm run api:generate`) and committed. Never hand-write a DTO type; after a backend API change, regenerate and commit (CI fails on drift).
- Feature data-access services call `HttpClient` with those types. Do not add a runtime client generator.
- WebSocket (STOMP) messages are signals only: on any message or reconnect, re-fetch from REST.

**Money**
- Money arrives as `{ amount: string, currency }`. Never `parseFloat`/`Number()` an amount.
- The frontend never computes what the customer pays: totals, fees, discounts, and splits come from the API. The cart (in `localStorage`) is re-priced at checkout.
- Previews (e.g. cart subtotal) use the `Money` utility in `core/`: integer minor units (`"12.50"` → `1250`), integer addition, formatting with `Intl.NumberFormat` and the API currency.

**Time**
- Moments arrive as ISO-8601 UTC. Display them in the **store** time zone from store settings, never the browser's.
- Elapsed-time screens (board age colors, countdowns) correct device clock drift with an offset from the API's `Date` response header.

**Auth** (tech-spec §6.4)
- Access token in memory only. Refresh token is a `__Host-` HttpOnly cookie handled by the browser; call `POST /auth/refresh` with `credentials: 'include'` and the `X-Requested-With` header on load.
- One auth interceptor in `core/auth`: attaches the bearer token **only** to API-origin requests; on `401` runs a **single-flight** refresh (concurrent `401`s share one refresh), retries once, and on failure clears the session and redirects to login. Never retries `/auth/*` calls. `403` never triggers a refresh.
- Role guards per feature with `canMatch`. They are UX only — the backend enforces.

**Errors** (tech-spec §5.1)
- All API errors are Problem Details with a stable `code`. One interceptor in `core/` turns them into a typed error; components and feature services never parse `HttpErrorResponse` themselves.
- User-facing text comes from a central `code` → message map in `core/errors` (Angular i18n, Spanish), typed against the codes in the OpenAPI spec. Unknown codes fall back to a generic message per status. Never display the backend's `title`/`detail`.
- `common.validation-failed` → map `errors[].field` to the matching form controls.
- Generic error screens show the `correlationId` so support can find the logs.

**Idempotency**
- Commands that require `Idempotency-Key` generate one UUID **per user intent** (e.g. per "Pay" click) and reuse it on every retry of that intent. Never generate a new key per retry.

**Staff and board screens**
- Sound requires a user gesture: board starts with an "Enable sound" action and warns while sound is off.
- Screen Wake Lock on board and display routes, re-acquired on visibility change.
- On STOMP disconnect: full-width banner and disabled actions until state is re-fetched.
- Undo = 5 s client-side delay before sending the command; flush pending commands on page close.
- Age colors computed client-side from `sent_at`/`fire_at`; always paired with text (elapsed minutes).
- Layouts: board/display for 1080p landscape readable at 2 m; ticket editor and table grid for 10" tablets.

**i18n** (tech-spec §6.6)
- Source locale is Spanish: write Spanish text directly in templates and `$localize`. Release 1 ships only Spanish, but everything is externalized for future translation.
- **Every user-facing text has a custom, stable ID**: `i18n="@@<feature>.<screen>.<element>"` (`@@checkout.summary.payButton`), `` $localize`:@@<id>:Pagar` `` in TypeScript. Rewording keeps the ID.
- Mark readable attributes too: `i18n-aria-label`, `i18n-title`, `i18n-placeholder`, `i18n-alt`.
- Add a translator description to short or ambiguous texts: `i18n="Button that confirms the payment|@@checkout.summary.payButton"`.
- Plurals with ICU (`{count, plural, =1 {…} other {…}}`); never concatenate translated fragments.
- Format numbers, currency, and dates with locale-aware pipes or `Intl`, never by hand.
- Error messages use the ID `@@error.<code>`.
- `@angular-eslint/template/i18n` (with `checkId`) fails CI on unmarked text or missing IDs; the `ng extract-i18n` output is committed.

**Accessibility**
- Semantic HTML, labelled controls, focus management in dialogs, WCAG AA contrast, never color alone.

## Testing

- Unit: Vitest + Angular testing utilities for services, signals, and components. Coverage gate ≥ 80% lines on `core` and `features`.
- Test public behavior: rendered DOM for components, signal values for stores. Never test private methods.
- `*Api` services: `provideHttpClient()` + `provideHttpClientTesting()` with `HttpTestingController`.
- Timers (undo window, countdowns): `vi.useFakeTimers()`.
- E2E: Playwright in the root `e2e/` folder (planned), run against the `test` environment, with axe-core and device viewports.
