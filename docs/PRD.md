# PRD — Juice Bar Omnichannel Platform (Release 1)

| Field | Value |
|-------|-------|
| Status | Draft v2 |
| Owner | Jhan Antezana |
| Last updated | 2026-09-17 |
| Companion doc | [tech-spec.md](./tech-spec.md) (how it will be built) |

This document defines **what** will be built and **why**. It deliberately avoids implementation choices; those live in the tech-spec. Requirements marked as market practice were validated against established platforms (§13).

## 1. Summary

A single platform for one juice bar that sells through **two channels sharing one catalog and one stock**:

- **Online**: customers browse the menu, pay online, and receive their order by delivery or pick it up.
- **In-store**: staff take orders at tables or the counter, and the cashier **records** each payment that was collected outside the system (cash, card terminal, mobile wallet). A full POS with hardware integration is out of scope for Release 1.

Both channels feed **one preparation board** designed for drinks: consolidated counts of identical items, clear change markers, and age colors.

The project has a second, explicit purpose: to be the real-world vehicle for applying the `github-actions-course` (CI/CD, multi-environment delivery, supply-chain security, rollback). Engineering-delivery goals are therefore first-class success metrics (see §9).

## 2. Problem

| Today | Consequence |
|-------|-------------|
| Orders arrive by phone/chat and are written on paper | Lost or duplicated orders, no status visibility for customers |
| Order changes are shouted across the counter | Wrong drinks prepared, waste nobody tracks |
| Payments collected in-store are not recorded in one place | End-of-day cash reconciliation is manual and error-prone; no audit trail |
| Stock and "sold out" items are known only by staff | Customers order items that are not available; online and in-store sell the same last unit |
| No sales data by channel, product, hour, or employee | Decisions (menu, pricing, staffing) are made blind; abuse goes unnoticed |

## 3. Goals and non-goals

### Goals (Release 1)

- **G1** — Customers can order and pay online, know when their order will be ready, and track it in real time.
- **G2** — Every in-store sale is recorded against a cash-register shift, with reconciliation at close.
- **G3** — Online and in-store channels never sell unavailable items (shared catalog, availability, and stock).
- **G4** — Every change to money, stock, prices, orders, and user roles is traceable (who, what, when, before/after, why).
- **G5** — Staff prepare orders faster and with fewer mistakes: every order and every change is visible on one board.
- **G6** — The owner sees today's performance at a glance and detects exceptions (voids, comps, refunds, cash differences) by employee.
- **G7** — The product is delivered through a fully automated, auditable pipeline with TEST and PROD environments (`test` and `prod` in the tech-spec).

### Non-goals (Release 1)

- POS hardware integration (card terminals, receipt printers, cash drawers).
- Offline operation (see A2 for the Release 1 behavior when the connection drops).
- Electronic invoicing / tax-authority integration.
- Ingredient-level inventory and recipes (see §5.2 for how stock works in Release 1).
- Multiple stores / franchises.
- Loyalty programs, coupons, promotions, discounts (comps with reason are allowed, FR-INS-08).
- Cash on delivery for online orders.
- Guest checkout (an account is required to order online).
- Delivery fleet management, route optimization, map-based address validation.
- Native mobile apps (the web app must be mobile-first instead).

## 4. Users and roles

| Role | Who | Primary jobs |
|------|-----|--------------|
| `CUSTOMER` | Public buyer with an account | Browse menu, place and pay online orders, track status, view order history |
| `SERVER` | Floor staff | Manage tables and tickets, send items to the board, void lines not yet prepared, prepare and hand over orders |
| `CASHIER` | Register staff | Everything a SERVER does, plus: register shifts, payments, quick sales, comps, cash movements, busy mode, run-out line removals |
| `ADMIN` | Owner / manager | Everything, plus: catalog, prices, stock, tables, zones, settings, staff accounts, reports, audit log, refunds |

Permission matrix (✅ allowed, — not allowed):

| Capability | CUSTOMER | SERVER | CASHIER | ADMIN |
|------------|:-------:|:------:|:-------:|:-----:|
| Browse catalog | ✅ | ✅ | ✅ | ✅ |
| Place online order; cancel own order before preparation (FR-ONL-15) | ✅ | — | — | — |
| Create / edit / send in-store ticket; transfer, merge, repeat lines | — | ✅ | ✅ | ✅ |
| Void a sent line **before** it is Ready (FR-INS-07) | — | ✅ | ✅ | ✅ |
| Void a line **after** it is Ready (waste) / comp a line | — | — | ✅ | ✅ |
| View and operate the preparation board (start, bump, recall, 86) | — | ✅ | ✅ | ✅ |
| Flag a run-out line / substitute with same-price item (BR-08) | — | ✅ | ✅ | ✅ |
| Confirm removal of a run-out online line (system refunds that line) | — | — | ✅ | ✅ |
| Advance delivery status, verify pickup | — | ✅ | ✅ | ✅ |
| Open / close register shift, record cash in / cash out | — | — | ✅ | ✅ |
| Record in-store payment / quick sale; split payments; void a payment on an open ticket | — | — | ✅ | ✅ |
| Void a recorded sale (with reason) | — | — | ✅ (own open shift) | ✅ |
| Busy mode / pause online ordering | — | — | ✅ | ✅ |
| Toggle item availability | — | ✅ | ✅ | ✅ |
| Manage catalog, modifiers, prices, stock, tables, zones, reason lists, settings | — | — | — | ✅ |
| Cancel / refund paid online order (any amount) | — | — | — | ✅ |
| Manage staff accounts and roles | — | — | — | ✅ |
| View dashboard, reports, and audit log | — | — | — | ✅ |

A staff member has exactly one role. `ADMIN` accounts can only be created by another `ADMIN` (the first one is provisioned at installation).

## 5. Scope — Release 1 functional requirements

Priority: **M** = Must (release blocker), **S** = Should (included if on schedule, otherwise first follow-up).

### 5.1 Catalog, modifiers, and availability

| ID | Requirement | P |
|----|-------------|---|
| FR-CAT-01 | ADMIN manages categories and products (name, description, photo, price, category, display order, active flag). | M |
| FR-CAT-02 | Customers and staff see only active products, grouped by category, with current price, availability, and allergens. | M |
| FR-CAT-03 | Any staff role can mark a product as **unavailable/available** ("86") instantly, from the catalog or the board; the change is visible in both channels within 5 seconds. Already paid or sent items follow BR-08. | M |
| FR-CAT-04 | Price changes follow BR-03 (existing orders and tickets keep their prices). | M |
| FR-CAT-05 | ADMIN defines **modifier groups** (e.g., Size, Base, Fruits, Boosters, Sweetener) and attaches them to products. Each group is required or optional with a min/max number of choices; each option has a price delta (can be 0) and its own availability. The same modifiers apply to both channels. | M |
| FR-CAT-06 | ADMIN tags products and modifier options with allergens from a fixed list; allergens are shown to customers before adding to cart. | M |
| FR-CAT-07 | Each category belongs to a **preparation station** (default: a single station "Main"). | M |

### 5.2 Stock

Release 1 uses a **hybrid model** to stay simple:

- **Stock-tracked products** (bottled/packaged items) have a numeric quantity.
- **Prepared products** (juices, sandwiches) use the availability toggle only (FR-CAT-03).

| ID | Requirement | P |
|----|-------------|---|
| FR-STK-01 | ADMIN marks which products are stock-tracked and records stock entries (purchases) and adjustments with a reason. | M |
| FR-STK-02 | A stock-tracked product with quantity 0 is automatically shown as unavailable in both channels. | M |
| FR-STK-03 | Online checkout **reserves** stock-tracked units; the reservation is released if payment is not confirmed within 15 minutes. | M |
| FR-STK-04 | Stock is deducted when an online order is paid, a ticket is closed, or a quick sale is recorded. Two channels can never sell the same last unit. Returns follow BR-10. | M |
| FR-STK-05 | ADMIN sees a low-stock list based on a per-product threshold. | M |

### 5.3 Online ordering (CUSTOMER)

| ID | Requirement | P |
|----|-------------|---|
| FR-ONL-01 | Customer registers with email + password, verifies the email, and can reset the password. | M |
| FR-ONL-02 | Customer builds a cart choosing modifiers, adding a note per item (max 140 characters), and an order note (e.g., delivery instructions); the cart survives page reloads on the same device. | M |
| FR-ONL-03 | At checkout the customer chooses **delivery** (address + delivery zone) or **pickup**. Delivery fee is a flat amount per zone, configured by ADMIN. Addresses outside configured zones are rejected. | M |
| FR-ONL-04 | Prices, availability, and stock are re-validated at checkout; the customer is told exactly what changed. | M |
| FR-ONL-05 | Payment is online only (cards and the wallets the payment provider supports in the country) and follows BR-02. The order is confirmed only after the provider approves payment. | M |
| FR-ONL-06 | Customer sees order status updates in real time and receives an email on confirmation, ready for pickup / out for delivery, delivery failed, cancellation, and any refund. | M |
| FR-ONL-07 | Customer sees their order history and can re-order a past order (unavailable items are reported, never silently dropped). | S |
| FR-ONL-08 | Online ordering is closed outside opening hours; the storefront shows the next opening time. Closing, pausing, or busy mode never affects orders already paid. | M |
| FR-ONL-09 | ADMIN can refund a whole paid order or individual lines (partial refund), always with a reason; the customer is notified by email. | M |
| FR-ONL-10 | Once preparation has started, a customer cannot cancel; the order page shows the store's contact (handled via FR-ONL-09). | M |
| FR-ONL-11 | Checkout and the order page show an **estimated ready time** (pickup) or **estimated delivery time**, based on current preparation load, busy mode, and the zone's delivery time. The estimate is updated when the status changes. | M |
| FR-ONL-12 | CASHIER/ADMIN can switch **busy mode** (adds a configured number of minutes to every estimate) or **pause** online ordering (new orders blocked with a clear message). Both are visible on the board. | M |
| FR-ONL-13 | At checkout the customer chooses what to do if an item runs out: **replace with a similar item of the same price**, **remove it and refund it**, or **contact me**. Default: remove and refund. Drives BR-08. | M |
| FR-ONL-14 | Pickup orders are handed over only after staff verifies the short order number and the customer's name shown on the board. | M |
| FR-ONL-15 | A customer can cancel their own order while it is Paid and preparation has not started; the full amount is refunded automatically (BR-05). | S |
| FR-ONL-16 | Online ordering pauses automatically while online orders in preparation reach a limit configured by ADMIN, and resumes when below it. | S |
| FR-ONL-17 | Customer can schedule a pickup or delivery for **later the same day** in 15-minute slots within opening hours; each slot has a capacity configured by ADMIN. | S |
| FR-ONL-18 | ADMIN can set a minimum order amount and a free-delivery threshold per zone. | S |

Online order status (business view):

```
Pending payment ──► Paid ──► Preparing ──► Ready ──┬─► Out for delivery ──┬─► Delivered
      │              │                             │                      └─► Delivery failed
      │              │                             └─► Picked up
      │              └─► Cancelled + refunded (customer, before preparation — FR-ONL-15)
      └─► Expired (no payment in 15 min)
Paid / Preparing / Ready / Delivery failed ──► Cancelled + refunded (ADMIN, reason required)
Delivery failed ──► Out for delivery (retry, set by staff)
Expired ──► Paid (payment approved late and items could be reserved again — see BR-05)
```

- Paid orders are accepted automatically (BR-09). **Preparing** starts when staff taps Start on the board. Scheduled orders stay Paid until their preparation time (FR-PRP-02).
- **Delivery failed** is set by staff when the courier cannot deliver (address not found, customer absent, refused). The customer is notified; staff can schedule a retry, or ADMIN can cancel and refund.

### 5.4 In-store sales (SERVER, CASHIER)

| ID | Requirement | P |
|----|-------------|---|
| FR-INS-01 | SERVER/CASHIER creates a ticket for a **table** (from the configured list) or for the **counter** (customer name). Lines have modifiers, an optional note (max 140 characters), and an allergy flag; the ticket may have a note. | M |
| FR-INS-02 | Sending a ticket puts its unsent lines on the preparation board. Unsent lines can be changed freely; sent lines follow FR-INS-07 (BR-12). More lines can be added and sent at any time until payment. | M |
| FR-INS-03 | CASHIER closes a ticket by recording payment: method (cash, card terminal, mobile wallet, bank transfer), amount, and for cash the amount received and change given. Several payments per ticket are allowed. While the ticket is open, CASHIER/ADMIN can void a recorded payment with a reason. Recording the final payment closes the ticket; a closed ticket cannot be edited — corrections are voids (FR-INS-05). | M |
| FR-INS-04 | CASHIER can register a **quick sale** (counter sale paid upfront) by picking items from a "frequent items" list configured by ADMIN, without searching the catalog. The sale gets a short number and optional customer name and appears on the board like any other order. | M |
| FR-INS-05 | A recorded sale is never deleted. It can only be **voided** with a reason; voids are visible in reports and the audit log. | M |
| FR-INS-06 | In-store payments can only be recorded while the cashier has an **open register shift**. | M |
| FR-INS-07 | A sent line is never edited in place. To change it, staff **voids** it (reason required) and adds a new line. Before the line is Ready: SERVER, CASHIER, or ADMIN can void it. After it is Ready: only CASHIER or ADMIN, and it counts as **waste** (BR-11). A line already covered by a recorded payment cannot be voided until that payment is voided (FR-INS-03). | M |
| FR-INS-08 | CASHIER/ADMIN can **comp** a line (served free of charge) with a reason; it counts as waste. | S |
| FR-INS-09 | ADMIN configures tables (name, area). Staff see a **table grid** with each table's status (free / occupied / waiting for payment) and time since the ticket was opened. | M |
| FR-INS-10 | Staff can **transfer** an open ticket to a free table; lines keep their state (unsent, sent, ready, voided). | M |
| FR-INS-11 | Staff can **merge** two open tickets into one. | S |
| FR-INS-12 | CASHIER can **split the bill** by items or evenly into several recorded payments. | S |
| FR-INS-13 | Staff can **repeat** lines ("same again") with their modifiers and notes; comps are never copied. | S |
| FR-INS-14 | ADMIN maintains short **reason lists** for voids, comps, cash out, and stock adjustments; choosing "Other" requires a free-text reason. | M |

### 5.5 Register shifts (cash reconciliation)

| ID | Requirement | P |
|----|-------------|---|
| FR-REG-01 | CASHIER opens a shift declaring the opening cash float. Only one open shift per cashier. | M |
| FR-REG-02 | **Blind close**: the cashier enters the counted cash first; only then does the system show expected cash (float + cash sales − change − voided cash sales + cash in − cash out), the difference, and totals per payment method. | M |
| FR-REG-03 | If the difference exceeds the threshold configured by ADMIN, the cashier must enter a note before the shift can close (no ADMIN approval needed; the note appears in the shift report). A closed shift is immutable. | M |
| FR-REG-04 | ADMIN can force-close a shift left open (e.g., forgotten at night), with a reason. | M |
| FR-REG-05 | During an open shift, CASHIER records **cash in / cash out** (e.g., paying a fruit supplier from the drawer) with amount, reason, and optional reference. | M |
| FR-REG-06 | When a shift closes, ADMIN receives the shift summary by email. | S |

### 5.6 Preparation board

| ID | Requirement | P |
|----|-------------|---|
| FR-PRP-01 | One live board shows every order to prepare from both channels. Each card shows: short number (`W-042` online, `L-017` in-store), channel (table / counter / pickup / delivery), table or customer name, elapsed time, lines with modifiers, notes, and **allergy flags visually distinct**, plus the scheduled time if any. | M |
| FR-PRP-02 | Cards are ordered oldest first. Scheduled online orders appear when their preparation should start (scheduled time − estimated preparation time). | M |
| FR-PRP-03 | The board updates without manual refresh. If the connection drops, every staff screen shows a clear **disconnected** state and blocks actions until it reconnects and reloads the current state. | M |
| FR-PRP-04 | Staff can **start** an order, mark each line or the whole order **Ready**, **undo** within 5 seconds, and **recall** orders completed in the last 30 minutes. Online delivery orders then move to Out for delivery and Delivered; pickup orders to Picked up (FR-ONL-14). | M |
| FR-PRP-05 | Card header color reflects elapsed time: normal → warning → late, with thresholds configured by ADMIN (default 5 and 10 minutes). | M |
| FR-PRP-06 | Views: **Tickets** (cards), **All-day** (consolidated counts of identical items across open orders, e.g., `6× Orange juice (500 ml)`; lines with notes or allergy flags are listed separately), and **Ready** (orders waiting for handover). | M |
| FR-PRP-07 | New orders, modified orders, and voided lines are highlighted with a label (NEW / MODIFIED / VOID) and a distinct sound; the highlight stays until a staff member acknowledges it. | M |
| FR-PRP-08 | The board can be filtered by station (FR-CAT-07); an order is Ready only when all its lines are Ready. | S |
| FR-PRP-09 | A **customer-facing screen** shows short numbers and first names in two columns: Preparing and Ready. No amounts or other personal data. | S |
| FR-PRP-10 | The system records when each order and line is sent, started, Ready, and handed over, to enable preparation-time analytics later. | M |

### 5.7 Administration, dashboard, reports, and audit

| ID | Requirement | P |
|----|-------------|---|
| FR-ADM-01 | ADMIN creates, deactivates, and changes roles of staff accounts. Deactivation takes effect within 15 minutes, even for active sessions. | M |
| FR-ADM-02 | ADMIN configures store settings: opening hours, delivery zones (fee, delivery time), base preparation time, extra minutes per order already in the queue, busy-mode minutes, board thresholds, register-difference threshold, exception threshold, online capacity limits. | M |
| FR-RPT-01 | Sales report for a date range by channel, product, category, **modifier**, and payment method, including voids, comps, waste, and refunds. | M |
| FR-RPT-02 | Register shift report: float, cash sales, cash in/out, expected vs counted, differences with notes, voids. | M |
| FR-RPT-03 | Every report can be exported to CSV. | S |
| FR-RPT-04 | **Today dashboard**: net sales, number of orders, and average ticket compared with the same weekday last week; split by channel; open orders; open shifts; current unavailable items (86 list); low-stock list. | M |
| FR-RPT-05 | **Hourly heatmap**: sales by hour × weekday for a date range. | M |
| FR-RPT-06 | **Exceptions by employee**: voids, comps, waste, refunds, cash out, and shift differences per employee and reason; employees above the configured threshold are flagged. | M |
| FR-AUD-01 | Every change to prices, modifiers, availability, stock, orders, tickets, lines (void/comp/transfer/merge), payments, cash movements, shifts, refunds, settings, users, and roles is recorded: actor, role, action, entity, before/after values, reason, timestamp, and request origin. | M |
| FR-AUD-02 | The audit log is read-only for everyone, including ADMIN, and is searchable by date, actor, and entity. | M |

## 6. Business rules (consolidated)

| ID | Rule |
|----|------|
| BR-01 | All prices are tax-inclusive, in a single currency configured for the store. |
| BR-02 | An online order is a sale only after the payment provider approves payment. Payments are approved or rejected immediately; payment methods that stay pending (e.g., cash vouchers paid later) are not offered. |
| BR-03 | An order/ticket keeps the prices it was created with, including modifier prices (price snapshot). |
| BR-04 | Money records (sales, payments, cash movements, shifts) are never deleted — only voided/refunded with a reason. |
| BR-05 | Refunds of online payments go through the payment provider and are issued by ADMIN, with three bounded exceptions where the amount is computed by the system: (a) a payment approved after its order expired when the items can no longer be reserved — refunded automatically (if they can be reserved again, the order moves from Expired to Paid); (b) customer self-cancellation before preparation (FR-ONL-15) — full refund; (c) removal of a run-out line (BR-08) confirmed by CASHIER or ADMIN — exactly that line's amount. Every refund is audited. |
| BR-06 | Business day and reports follow the store's local timezone; every recorded time must be unambiguous for audit purposes. |
| BR-07 | Customer personal data is limited to name, email, phone, and delivery addresses; a customer can request account deletion (order history is anonymized, not deleted). |
| BR-08 | If an item already paid online or sent from a ticket runs out before it is Ready, staff flags the line on the board. **Online**, the customer's choice (FR-ONL-13) applies: *replace* → staff substitutes an item of the same price; *remove and refund* → CASHIER/ADMIN confirms the removal and the line is refunded (BR-05c); *contact me* → staff contacts the customer; if there is no answer within 5 minutes, CASHIER/ADMIN may confirm the removal as in *remove and refund* — a line is never refunded without that confirmation. If every line of an order is removed, the order is cancelled and fully refunded, including the delivery fee. **In-store**, the line is substituted or voided before payment. A flagged line cannot stay unresolved on the board. |
| BR-09 | Paid online orders are accepted automatically; the store controls its load with busy mode, pause, and capacity limits (FR-ONL-12/16), never by leaving paid orders waiting for manual acceptance. |
| BR-10 | Voiding a recorded sale or refunding a line returns stock-tracked units to stock, unless the item is recorded as waste. |
| BR-11 | Lines voided after Ready and comped lines are **waste**: they are never charged and appear in reports by employee and reason. |
| BR-12 | No silent edits: once a line is sent to the board, any change is a void plus a new line, visible on the board and in the audit log. |

## 7. Key user journeys

1. **Online delivery order** — browse → choose size and fruits, add "no sugar" → checkout (zone, address, run-out preference) → estimated time shown → pay → Paid, card appears on the board with a sound → Start → Ready → Out for delivery → Delivered; the customer sees each step live and by email.
2. **Table service with a change** — SERVER opens Table 4 → sends 2 mango juices → the customer switches one to strawberry → SERVER voids one mango ("customer changed mind") and sends a strawberry → the board shows VOID and NEW with a sound → lines Ready → CASHIER splits the bill (cash + wallet) → ticket closed, table free.
3. **Table change** — guests at Table 4 move to Table 7 → SERVER transfers the ticket; lines already on the board keep their state.
4. **Counter quick sale** — CASHIER picks 3 frequent items, enters name "Ana" and cash received → change shown → `L-018 Ana` appears on the board and on the customer screen → Ready → handed over.
5. **Rush hour** — the board fills with late (red) cards → CASHIER turns on busy mode (+15 min) → new online customers see longer estimates; staff use the All-day view to blend 6 orange juices at once.
6. **Sold out after payment** — mango runs out → SERVER taps 86 on the board (menu updated in ≤ 5 s) and flags the paid mango line of `W-042` → the customer chose "remove and refund" → CASHIER confirms → the line is refunded automatically and the customer is emailed.
7. **End of day** — CASHIER records a cash out for the fruit supplier → closes the shift entering the counted cash blind → difference shown and justified → ADMIN receives the summary email and reviews the dashboard and the exceptions report.

## 8. Non-functional requirements (product level)

| ID | Quality | Target (Release 1) |
|----|---------|--------------------|
| NFR-01 | Availability | Storefront and ordering ≥ 99.5% monthly, excluding announced maintenance. |
| NFR-02 | Speed — storefront | Menu page Largest Contentful Paint ≤ 2.5 s on mid-range mobile over 4G. |
| NFR-03 | Speed — API | p95 ≤ 300 ms for reads, ≤ 800 ms for writes (excluding payment-provider time). |
| NFR-04 | Real-time | New orders, changes, and availability reach connected screens in ≤ 5 s. |
| NFR-05 | Capacity | Sustains 10× expected peak (expected peak: 100 concurrent shoppers, 30 orders/hour) without degradation. |
| NFR-06 | Scalability | Adding capacity requires configuration only, not code changes. |
| NFR-07 | Data durability | Max data loss 5 minutes (RPO); service restored within 2 hours after a major failure (RTO). |
| NFR-08 | Auditability | 100% of events listed in FR-AUD-01 audited; audit records retained ≥ 5 years (confirm with accountant); every production release traceable to a commit, a pipeline run, and an approver. |
| NFR-09 | Security | Card data never touches our systems; staff sessions revocable; no open CRITICAL/HIGH findings from automated security scans or an OWASP-Top-10 checklist review at each PROD release. |
| NFR-10 | Accessibility | Storefront meets WCAG 2.2 AA. |
| NFR-11 | Devices | Storefront mobile-first; staff screens usable on a 10" tablet; board and customer screen readable from 2 m on a 24"+ landscape display. |
| NFR-12 | Language | UI in Spanish for Release 1; text externalized for future translation. |
| NFR-13 | Cost | Cloud cost for TEST + PROD stays within the monthly budget set in the tech-spec (§8.3). |

## 9. Success metrics

| Area | Metric | Target 90 days after launch |
|------|--------|-----------------------------|
| Business | Share of sales recorded in the system (vs paper) | 100% |
| Business | Online orders as % of total orders | ≥ 20% |
| Business | Unreconciled register differences | < 1% of cash sold |
| Business | Paid lines refunded because an item ran out (BR-08) | < 1% of paid lines |
| Operations | Waste (voids after Ready + comps) | < 2% of in-store sales |
| Operations | Online orders delivered/ready after the estimated time | < 10% |
| Delivery (DORA) | Deployment frequency to PROD | ≥ weekly |
| Delivery (DORA) | Lead time from merge to PROD | < 1 day |
| Delivery (DORA) | Change failure rate | < 15% |
| Delivery (DORA) | Time to restore via rollback | < 30 min |

## 10. Release plan

| Milestone | Content | Exit criteria |
|-----------|---------|---------------|
| M0 — Walking skeleton | Repo, pipelines, TEST + PROD environments, health endpoint deployed end-to-end | A merge to `develop` reaches TEST automatically; a release PR merged to `main` reaches PROD after approval |
| M1 — Catalog & staff | Catalog, modifiers, allergens, stations, availability, tables, reason lists, staff accounts, audit log | FR-CAT, FR-ADM-01/02, FR-INS-09/14, FR-AUD done |
| M2 — In-store | Tickets, voids/comps, transfers, quick sale, board (all views, markers, colors), register shifts with blind close and cash movements, stock | FR-INS, FR-REG, FR-PRP, FR-STK done; used in the store in parallel with paper for 1 week |
| M3 — Online | Customer accounts, checkout with modifiers, estimates, busy mode, run-out preference, payments, delivery/pickup, notifications | FR-ONL done; 20 real test orders paid and refunded in TEST |
| M4 — Dashboard & launch | Today dashboard, heatmap, exceptions, reports, CSV, hardening, load test, backup-restore drill | FR-RPT done; all M items done; NFR-05 and NFR-07 verified |

**Future releases (not committed):** offline operation for staff screens; hold/fire items and coursing; floor-plan editor; scheduling on future days; guest checkout; WhatsApp/SMS notifications; tax report and electronic invoicing; preparation-time, cancellation, and repeat-customer analytics; POS integration; ingredient inventory; promotions/loyalty; cash on delivery; multi-store; extraction of a module into a microservice (course final project).

## 11. Assumptions, constraints, open questions

**Assumptions**

- A1 — One store, one currency, one timezone.
- A2 — The store has reliable internet. If it drops, staff screens show a disconnected state (FR-PRP-03) and staff follow the paper fallback procedure; sales are recorded once the connection returns.
- A3 — Deliveries are made by the store's own courier or an external courier coordinated outside the system.
- A4 — The payment provider account (see tech-spec) is available in the store's country.
- A5 — One preparation station at launch; stations can be added later without changing the product (FR-CAT-07, FR-PRP-08).

**Constraints**

- C1 — Solo developer; scope must fit a part-time schedule. "S" items are the first to move to a follow-up release.
- C2 — Low monthly cloud budget; no always-on infrastructure that the load does not justify.

**Open questions**

| # | Question | Needed by |
|---|----------|-----------|
| Q1 | Country and currency (drives payment methods, the payment provider choice, and tax wording)? | M1 |
| Q2 | Is electronic invoicing legally required for online sales from day one? | M3 |
| Q3 | Audit-record retention period required by the accountant? | M4 |
| Q4 | Domain name for the storefront? **Answered: `jhanantezana.com`** (storefront host still to be chosen: apex or a subdomain) | M0 |
| Q5 | Will juices and food be prepared at separate stations? | M2 |

## 12. Glossary

| Term | Meaning |
|------|---------|
| Ticket | An in-store order (table or counter) that stays open until paid. |
| Quick sale | A counter sale paid upfront at the register; it still appears on the board. |
| Line | One product with its modifiers, note, and allergy flag inside an order or ticket. |
| Modifier group | A set of choices for a product (e.g., Size, Fruits), required or optional, with min/max choices. |
| Ready | A line or order that finished preparation and waits for handover or dispatch. |
| Handed over | Given to the customer, the table, or the courier; the order leaves the board. An online order then becomes Picked up or Out for delivery. |
| Void | Cancellation of a line or recorded sale; the record remains, flagged as voided, with a reason. |
| Comp | A line served free of charge, with a reason. |
| Waste | Voids after Ready and comps: product made but not charged. |
| 86 | Marking an item as unavailable in every channel. |
| All-day view | Board view that consolidates identical items across open orders. |
| Busy mode | Adds minutes to online estimates without stopping orders. |
| Station | A preparation area; each category belongs to one. |
| Register shift | A cashier's working session between opening and closing the register, used for reconciliation. |
| Blind close | Closing a shift by entering the counted cash before seeing the expected amount. |
| Cash in / cash out | Money added to or taken from the drawer for reasons other than sales. |
| Stock-tracked product | A product with a numeric quantity (packaged items). |
| Preparation board | Shared live screen with everything that must be prepared, from both channels. |

## 13. Market references

Requirements in §5 follow patterns documented by established platforms (reviewed 2026-09-17):

| Topic | References |
|-------|------------|
| Voids vs comps, split and transfer of tickets | [Square — Comp and void](https://squareup.com/help/us/en/article/8166-comp-void-and-reassign-checks-with-square-for-restaurants) · [Toast — Transferring items and checks](https://support.toasttab.com/en/article/Transferring-Items-Checks-and-Payments) · [Fudo — Mesas](https://soporte.fu.do/es/articles/11730672-modulo-mesas) |
| Modifiers for drinks | [Square — Item modifiers](https://squareup.com/help/us/en/article/5119-create-and-manage-item-modifiers) · [Lavu — Smoothie & juice POS](https://lavu.com/best-pos-for-smoothie-juice/) |
| Board: all-day counts, bump/recall, change markers, fire time | [Square — All Day counts](https://www.sellercommunity.com/t5/Square-UK-Product-Updates/New-KDS-Basic-Expeditor-Station-and-Launchpad-amp-All-Day-Counts/ba-p/272771) · [Square — Complete and recall](https://squareup.com/help/us/en/article/8171-complete-orders-with-square-kds) · [Toast — Configuring tickets](https://doc.toasttab.com/doc/platformguide/platformKitchenConfiguringTickets.html) · [Toast — Fire by prep time](https://doc.toasttab.com/doc/platformguide/adminFireByPrepTime.html) · [Odoo 18 — Preparation display](https://www.odoo.com/documentation/18.0/applications/sales/point_of_sale/preparation.html) |
| 86 across channels | [Toast — 86 an item](https://support.toasttab.com/en/article/86-an-Item) |
| Online load control, estimates, scheduling | [Toast — Throttling](https://support.toasttab.com/en/article/Managing-Online-Order-Volume-Throttling-Orders-1492627940253) · [Toast — Quote time](https://support.toasttab.com/en/article/Managing-Your-Quote-Time-Strategy) · [Olo — Throttling strategies](https://olosupport.zendesk.com/hc/en-us/articles/115002752386-Order-Throttling-Strategies-Overview) |
| Run-out substitution preference, cancellation windows | [Rappi — Sustituciones](https://merchants.rappi.com/es-co/politica-sustituciones-evitar-cancelaciones-pedidos) · [Uber — Out-of-stock replacements](https://help.uber.com/en/driving-and-delivering/article/out-of-stock-items-and-replacements?nodeId=4127389c-b54c-4422-a5aa-28c2bcd83273) · [ChowNow — Cancellation policy](https://get.chownow.com/restaurant-support/cancellation-policy/) |
| Instant payment approval | [Mercado Pago — Binary mode](https://www.mercadopago.com.ar/developers/en/docs/checkout-pro/checkout-customization/preferences/binary-mode) |
| Dashboard, exceptions by employee, blind close, cash movements | [Toast — Exceptions report](https://support.toasttab.com/en/article/Exceptions-Report-Overview) · [Toast — Blind close](https://doc.toasttab.com/doc/platformguide/adminCashDrawerStates.html) · [Lightspeed — Hourly sales](https://k-series-support.lightspeedhq.com/hc/en-us/articles/35308969074459-Understanding-the-Hourly-Sales-Report) · [Loyverse — Shift management](https://help.loyverse.com/help/shift-management-loyverse-pos) · [Fudo — Arqueos de caja](https://soporte.fu.do/es/articles/11730865-3-arqueos-de-caja) |
