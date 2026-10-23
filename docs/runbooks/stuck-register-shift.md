# Runbook — a register shift that cannot be closed

Goal: every shift ends with a count, or with a recorded reason why it has none, and nobody edits the database to get there.

A shift is "stuck" when its cashier cannot or will not close it: they went home without closing, the device is lost, the account was needed elsewhere, or the close keeps being refused. It matters because an open shift blocks changes elsewhere (a currency change, deactivating or re-roling the cashier), keeps taking payments into a drawer nobody watches, and leaves no figures for the day.

Only an `ADMIN` can end a shift that is not theirs. The tool is **forced close**; read what it costs before you use it.

## What forced close does and does not do

- It ends the shift at once and shows the expected cash straight away (float + cash received − change + cash in − cash out − cash paid back for voided payments). A normal close shows nothing until the cashier has typed the count: that blindness is what makes the count honest.
- It takes **no count of its own**. If the cashier had already been shown a difference (see case B), that earlier count is kept.
- It needs a reason, which stays in the shift and the audit log (`Auditoría`).
- It cannot be undone and a closed shift is never written again: no payment, void or cash movement can be added to it afterwards.

So the physical drawer must be counted by a person **before** you force the close, and that count written down: the system will never have it.

## 1. Find the shift

Open **Turnos abiertos** (`/staff/register/shifts`, `ADMIN` only). It lists every open shift with the cashier, the opening time and the float. A shift open since yesterday, or from a cashier who is not on duty, is the one.

Before acting, ask:

1. Is the cashier still around? If yes, ask them to close it themselves (case A or B). Forcing is for when they cannot.
2. Is anyone using the drawer? Payments are taken into the shift that is open for the cashier who records them; do not force a shift while its cashier is serving.
3. Is it a **stuck count** (case B) rather than a forgotten shift?

## 2. Cases

### A. The cashier forgot to close, or left

1. Count the drawer in front of a second person, in cash only, and write it with the date, the time and your name on the cash sheet. Keep the cash and the sheet together.
2. On **Turnos abiertos** press **Cierre forzado** on the row, write in **Motivo** who, why and the physical count (for example "Rosa left at 22:10 without closing; counted 340.50 by Luis and me"), and press **Forzar cierre**.
3. Read the expected cash the screen shows now and compare it with your count. The difference is a fact to record, not something to fix: write both figures and the explanation on the cash sheet, and tell the owner. The system only knows the expected cash, so the count in the reason is the only record of the real one.

### B. The close is "stuck" after the count was shown

The cashier typed a count, the system showed a difference over the threshold and asked for a note, and they never resubmitted. From then on that shift takes no more payments, voids or cash movements (`El turno se está cerrando`), and the count cannot change (`El efectivo contado ya se mostró`).

1. Preferred: the cashier closes again from **Caja** with **the same count** and the note the system asked for. This is a normal close and keeps the blind-close guarantee.
2. If they cannot, force the close as in case A. The count they had already typed is kept, so you only need to state the explanation in the reason.

### C. The cashier's account has to go (leaver, lost device)

You cannot deactivate a user, or change their role, while their shift is open (`instore.user-has-open-shift`). Close the shift first as in case A, then deactivate. Their sessions end when the account is deactivated.

### D. A payment, void or cash movement is refused with "Necesitas un turno de caja abierto"

That is not a stuck shift: that user has no open shift of their own. A `CASHIER` or an `ADMIN` opens one in **Caja** first. Voiding cash from a shift that is already closed is done by an `ADMIN` with their own open shift, because the cash comes out of that drawer (`paper-fallback.md`, section 4).

### E. Two people claim the same drawer

A shift belongs to one user, and a user has at most one open shift. If two people shared a drawer, the one whose shift is open owns the figures; the other's payments were rejected, or sit in their own shift. Count once, force or close the shift that holds the money, and write the split on the cash sheet.

## 3. Afterwards

1. The shift summary email goes to the `ADMIN` as for any closed shift. A forced close is marked as such in the shift and in the audit log.
2. If the figures are wrong because the shift was left open across closing time and later payments landed in it, they cannot be moved: note it in the reason and tell the owner. Never void and re-key sales to move them to another day.
3. If it happened because staff did not know how to close, go over **Caja** → **Cerrar turno** with them: count first, then the system shows the expected cash.

## What not to do

- Do not edit `instore.register_shift` (or any payment table) to close a shift or change its figures. The tables of money are append-only for a reason; a hand edit also hides the case from the audit log.
- Do not force the close before counting the drawer, and do not "fix" the difference with a cash movement afterwards: a closed shift takes none, and a movement in the next shift would be another shift's cash.
- Do not force a shift while its cashier is still serving.
- Do not use forced close as the normal way to end a shift: it throws the blind count away.
