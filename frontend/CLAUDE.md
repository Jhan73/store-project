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
- **Every form control is a PrimeNG component:** `p-select`, `pInputText`, `pTextarea`, `p-checkbox` or `p-toggleswitch` (immediate on/off actions), `p-inputnumber`, `p-password` (or `pPassword` when the screen needs its own show/hide button), `p-fileupload`. Native `<select>`, `<textarea>`, checkbox, radio, file, number and unstyled `<input>` are not allowed in new code. Exceptions kept: time-of-day fields are `pInputText type="time"` (the values are store-local `HH:mm`; a date picker would round-trip them through `Date`), and money amounts parsed by `parseAmount` are text `pInputText`, not `p-inputnumber`.
- Every `p-select` has the search filter on: `[filter]="true"`, `filterBy` set to the option label field (omit it for plain string options), `[resetFilterOnHide]="true"` and the shared i18n `filterPlaceholder` (`@@shared.select.filterPlaceholder`). The single exception is the sidebar theme select (`inputId="shell-theme"`): a search box over three fixed options is noise. `select-conventions.spec.ts` enforces the filter on every other `p-select` and keeps the exception in an allow-list keyed by `inputId`.
- `p-select`, `p-inputnumber` and `p-password` bound to `[invalid]` must also carry `[pt]` from `shared/forms/aria-invalid` (`selectAriaInvalid`, `numberAriaInvalid`, `passwordAriaInvalid`) so the focusable element announces `aria-invalid`; PrimeNG only adds the `p-invalid` class.
- Styled mode with one custom preset (`definePreset`). The preset and the app's semantic tokens share one palette; do not restyle PrimeNG components with ad-hoc CSS.
- Initial-bundle warning budget is 600 kB (`angular.json`), raised from 500 kB because PrimeNG and its preset alone use about 506 kB; the 1 MB error budget is unchanged.
- PrimeNG's `darkModeSelector` is the same `<html>` class used for the app's dark mode, so both switch together.

**Icons:** Tabler, through `@tabler/icons-angular` (official, MIT). Import each icon individually (tree-shaking); no icon fonts, no other icon sets. Decorative icons are `aria-hidden="true"`; icon-only buttons need an i18n `aria-label`.

**Buttons:** every button (`pButton`, `p-button`, `p-fileupload`) has a Tabler icon before its label (the label goes in its own `<span i18n>` so the icon stays out of the message), a `pTooltip` with `tooltipPosition="top"` and an i18n description of the action (`i18n-pTooltip`, or a `$localize` string when the text depends on state), and, when icon-only, an i18n `aria-label`. The same action uses the same icon everywhere. A button with `[loading]` wraps its icon in `@if (!<same signal>())` so only PrimeNG's spinner shows while loading, and a `p-button` label span carries `pButtonLabel` (keeps `p-button-label` styling). `a[pButton]` has no underline: a global rule in `styles.scss` removes it, so do not add per-component fixes. `button-conventions.spec.ts` enforces this. Tooltips do not show on disabled buttons, so never put the only explanation of an action in one.

**Responsive:**
- Mobile-first: write base styles for the smallest screen and enhance upward with `min-width` queries only, never `max-width` overrides. Breakpoints are defined once, in `src/styles/_breakpoints.scss` (`tablet-up` 48rem, `desktop-up` 64.0625rem = 1025px); layout tokens (`--page-padding`, `--touch-target`) live in `styles.scss`.
- Navigation (`shared/ui/app-shell`): up to and including 1024px there is a top bar with the brand and a hamburger button that opens the navigation, theme switch, role and sign-out in a `p-drawer`. Above 1024px (`desktop-up`) there is no top bar and no hamburger: a fixed left sidebar holds the brand and its toggle at the top, the role-filtered navigation in the middle, and the theme select, role and sign-out at the bottom. The drawer keeps the theme as three icon buttons (there is room, and one tap beats opening a list); the sidebar uses one `p-select` instead, which in the rail shrinks to the current theme's icon and keeps the label in its tooltip. Both structures are in the template and CSS shows one (`display: none` hides the other from assistive technology too).
- The sidebar has two modes. The **rail** (default, 4.5rem) shows icons only; every link has an `aria-label` and a right-hand tooltip. **Expanded** (15rem) adds the labels, the role and a pin button. The toggle button expands and collapses it. Unpinned, an expansion is a temporary overlay: it floats over the content, which keeps its rail offset, and collapses on Escape, focus leaving it, the pointer leaving it, or a followed link; while the theme select's overlay (appended to the body) is open those events are ignored, and when it closes the expansion is dismissed unless the pointer or focus is still inside. Crossing the 1024/1025px boundary closes the drawer, the theme overlay and any temporary expansion; the shell reads the width from the `--bp-desktop` custom property that `styles.scss` derives from `_breakpoints.scss`. **Pinned** docks it expanded and pushes the content; collapsing a pinned sidebar unpins it. State lives in `core/layout/sidebar-store.ts`; only `pinned` is persisted in `localStorage` (SSR-safe, failures ignored), a temporary expansion never is.
- Forms are one column on phones and multi-column from the tablet breakpoint up. Interactive targets are at least 44 px (`--touch-target`).
- Every data table sits inside `app-table-scroll` (`shared/ui/table-scroll`), a focusable labelled region that scrolls horizontally; `table-conventions.spec.ts` enforces it.
- No horizontal page overflow at 360 px wide.

**Light and dark mode:**
- Both modes are supported everywhere. Default follows `prefers-color-scheme`; the user's choice is stored in `localStorage` and applied as a class on `<html>`.
- The class is applied by an inline script in `index.html` before first paint, so SSR pages never flash the wrong theme.
- Every screen meets WCAG AA contrast in **both** modes.

### Styling

- Use CSS variables for all theme tokens (colors, radius, shadows, spacing).
- Prefer semantic tokens (`--background`, `--foreground`, `--primary`, etc.).
- Avoid hardcoded colors.
- Tokens are defined once, in the global theme stylesheet, with a light and a dark value each. Components only consume `var(--token)`; they never define colors of their own.

`openapi-typescript` 7.13.0 declares a TypeScript 5 peer while Angular 22 uses TypeScript 6, so `package.json` carries an `overrides` entry scoped to it (like the one for `primeng`). The generated types compile and the output is deterministic; if a future release breaks under TypeScript 6, report it instead of widening the override.

## Commands

```bash
npm start                                   # ng serve on http://localhost:4200
npm run build                               # production SSR build → dist/frontend
NG_ALLOWED_HOSTS=localhost npm run serve:ssr:frontend   # run the built SSR server
npm test                                    # Vitest via @angular/build:unit-test
npm run lint                                # angular-eslint, including the i18n rule
npx ng test --include src/app/app.spec.ts   # single spec file
npx ng extract-i18n                         # extract UI text into src/locale/messages.xlf (committed; CI fails on drift)
npm run api:generate                        # regenerate src/app/core/api/schema.d.ts from ../backend/api/openapi.json (CI fails on drift)
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
    └── admin/       dashboard, catalog, tables, settings (general, hours, zones, reasons), stock, users, reports, audit
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
| `/cart`, `/checkout`, `/account/**`, `/orders/**`, `/login`, `/forbidden`, `/set-password`, `/staff/**`, `/display`, `/admin/**` | `Client` |

SSR never renders authenticated content, so tokens never exist on the SSR server.

**SSR host allowlist:** Angular rejects SSR requests whose `Host` is not allowed (SSRF protection, HTTP 400). The list comes **only** from the runtime variable `NG_ALLOWED_HOSTS` (comma-separated), set per ECS service — never from `security.allowedHosts` in `angular.json`, because the same image is promoted from `test` to `prod`. `/healthz` is handled by Express before Angular, so ALB health checks (which use the task IP as `Host`) pass.

**Staff and admin shell**
- `/staff` and `/admin` are lazy child routes behind `canMatch: [roleGuard(...)]` and share `features/workspace/workspace-layout` (role-filtered navigation, theme, sign-out, toast host) built on the presentational `shared/ui/app-shell` (top bar with a drawer up to 1024px, left sidebar above). Each work package that adds a screen adds its entry to `features/workspace/workspace-nav.ts`, so the menu never links to a page that does not exist.
- `/login` is the single sign-in page (staff and, later, customers); it only follows a `returnUrl` that is an in-app path.
- `/set-password` is the public page the staff invitation and first-admin links open (`?token=`). It keeps the token only in the component, replaces the URL without it on load, and never stores or logs the password; the SSR server sends `Referrer-Policy: no-referrer` for it. Do not add analytics or third-party scripts to it.
- Errors raised by a feature are shown with `ErrorNotifier.show(error)` (toast with the localized message and the correlation id).
- **Admin catalog** (`features/admin/catalog`): `CatalogApi` is provided by the catalog route, not root. Every catalog update sends the item's own `etag` (from the list or the read) as `If-Match`; on `412`, `404`, or `common.concurrent-modification` the screen re-reads the item and keeps the toast. Availability ("86") switches act immediately and take no `If-Match`; on failure the switch goes back to the server's value. Typed prices go through `parseAmount` (`core/money`) and carry the store currency from `GET /admin/settings`; the form never computes an amount. The product image is chosen with `p-fileupload` and validated client-side (PNG/JPEG/WebP, 2 MB) only for fast feedback.
- **Admin store and staff** (`features/admin/settings`, `tables`, `users`): each area provides its own `*Api` in its route. The singletons (store settings incl. board thresholds, opening hours) are read with `observe: 'response'` into `Versioned<T>` (`value` + the `ETag` of that response) and written with that ETag as `If-Match`; a response without an `ETag` is an error, so a save is never sent unguarded. After `412`, `404`, or `common.concurrent-modification` the screen keeps the toast, re-reads, and refreshes the form to the server's values, so the next save carries the new ETag. Zones, reasons and tables send the item's own `etag`; when a reload finds the item being edited the form is re-pointed at the fresh item, and when it is gone the form closes. Toggles and other immediate actions are disabled while their request is in flight (`PendingIds`) and a refused control goes back to the value the list holds now. Lists with several request sources apply only the latest response. Opening hours are the store's local times (never converted); staff accounts are invited by email and role only: the admin never types a password, and the invitation or resend link is never shown.
- **Staff availability** (`features/staff/availability`): the route provides `AvailabilityApi`; `AvailabilityStore` is provided by the screen component, not the route, because route-level providers outlive navigation and the store owns a realtime subscription. It reads `GET /catalog/menu`, re-reads on every `/topic/catalog` signal (latest response wins), and keeps the toggles disabled until a read that started on the live connection has been applied; a refused switch goes back to the value the store holds, and the store re-reads. `PendingIds` lives in `shared/state`.
- **Admin audit log** (`features/admin/audit`, ADMIN only): read-only search over `GET /audit-entries`; the route provides `AuditApi`. The URL is the source of truth: filters (`from`, `to` as store-local `YYYY-MM-DD` days, `actorId`, `entityType`, `entityId`, `action`) and paging (`page` 0-based, `size`) are query params, Search/Clear/paginator only navigate, and the screen reacts to every URL change (shared links, reload and the back button work). Days become instants with `storeDayStart`/`storeDayEnd` (`core/time/store-day`): the API's `to` is inclusive, so the last day ends at its last microsecond; a single picked day means that whole day; `from` later than `to` and an `entityId` that is not a UUID are refused before any request. Actors are named from the staff accounts (all pages of `GET /staff`, as the rows only carry an id); an unknown id shows shortened with the full value in its tooltip, and a missing actor shows its role (`SYSTEM`, `ANONYMOUS`). Entity types and actions are free text in the API, so the selects offer the values the backend writes today plus any other one found in the URL. `before`/`after` are rendered as pretty-printed text only (never as HTML) in a dialog. Results are applied only for the latest request; the paginator (`p-paginator`) is the server-side pager for lists with a total (older lists keep their previous/next buttons).

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
- Access token in memory only. Refresh token is a `__Host-` HttpOnly cookie handled by the browser. The session is restored lazily: the first role guard and the login page await one `POST /auth/refresh` (`credentials: 'include'`, `X-Requested-With`); public pages make no call.
- One auth interceptor in `core/auth`: attaches the bearer token **only** to API-origin requests; on `401` runs a **single-flight** refresh (concurrent `401`s share one refresh), and retries once. Only a `401` from the refresh ends the session (clear it, redirect to login once); an outage (network, `5xx`, 10 s timeout) keeps the session and fails the request with that outage. Never retries `/auth/*` calls. `403` never triggers a refresh. Tabs serialize refreshes with the Web Locks API.
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
- `@angular-eslint/template/i18n` (with `checkId`) fails CI on unmarked text or missing IDs; the `ng extract-i18n` output is committed. The CI drift check ignores the `location` notes (file and line numbers), so editing code does not require re-extracting, but adding, removing or rewording a message does: run `npx ng extract-i18n` and commit `src/locale/messages.xlf`.

**Accessibility**
- Semantic HTML, labelled controls, focus management in dialogs, WCAG AA contrast, never color alone.

## Testing

- Unit: Vitest + Angular testing utilities for services, signals, and components. Coverage gate ≥ 80% lines on `core` and `features`.
- Test public behavior: rendered DOM for components, signal values for stores. Never test private methods.
- `*Api` services: `provideHttpClient()` + `provideHttpClientTesting()` with `HttpTestingController`.
- Timers (undo window, countdowns): `vi.useFakeTimers()`.
- E2E: Playwright in the root `e2e/` folder (planned), run against the `test` environment, with axe-core and device viewports.
