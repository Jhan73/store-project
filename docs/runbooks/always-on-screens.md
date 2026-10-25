# Runbook — kitchen board and customer display that stay on for weeks

Goal: the board in the kitchen and the display for customers never stop in the middle of service because their sign-in ran out.

These two screens are signed in like any other staff device and are left on all day, every day. The sign-in cannot last forever: a session lives at most **30 days** from the moment someone typed the password, however busy the screen is (the refresh token family has an absolute ceiling), and it also ends after **7 days** without the screen renewing it (a device that was switched off for a week). So every always-on screen needs one person to sign in again about once a month. That is a routine, not an incident.

## How the screens warn you

- **From 3 days before the end**, both screens show a yellow line: "La sesión de esta pantalla vence en N días…" (and "menos de un día" on the last day). On the board it is at the top; on the display it is a small line in the bottom corner, under the numbers. It disappears by itself as soon as someone signs in again.
- The warning comes from the end date the server sends with every sign-in and renewal, so it is exact. The screen learns the date when it signs in or renews its token, which happens about every 15 minutes while it is on and active, so the warning appears at the screen's next renewal; a screen that was switched off for a week loses its session (7 days without renewing) before any warning can reach it.

## What each screen does when the session ends

| Screen | What happens |
|--------|--------------|
| Board (`/staff/board`) | It leaves the board and shows the sign-in page. Nothing is lost on the server; a Ready still inside its 5 s undo window is sent when the screen closes. |
| Customer display (`/display`) | It **keeps the last numbers on screen**, shows "Actualizando…" and "La sesión terminó…" with an **Iniciar sesión** link. The numbers stop updating until someone signs in. |

## The monthly routine

Do it on a quiet moment, ideally when the warning first appears (3 days of margin), not on the last day.

1. Go to the device. On the **board**, press **Cerrar sesión** in the menu and sign in again with the kitchen account. On the **display**, which has no menu, type `/staff` in the address bar, press **Cerrar sesión** there, sign in with the account of that screen and open `/display` again (the sign-in page sends a signed-in browser straight to its home, which is why the sign-out comes first).
2. Check that the yellow line is gone and the screen shows live data (no "Actualizando…" and, on the board, no red or yellow banner).
3. Board only: tap **Activar sonido** again if the browser asks, and check the wake lock keeps the screen on.

Each screen has its **own account**: a `KITCHEN` account for the board, a `SERVER`, `CASHIER` or `ADMIN` account reserved for the display (the display never accepts `KITCHEN`). One account per device, and one signed-in account per browser: the refresh cookie belongs to the browser, so signing another user in on a second tab of the same device ends the display's session (it keeps its last numbers and offers the sign-in). Do not share one account between devices: the refresh cookie belongs to the browser, but two devices on one account make it harder to tell whose session is whose, and deactivating the account (for a lost device) would stop both.

## It already ended (the screen is on the login page or says "La sesión terminó")

1. Board: sign in on the page it shows; it returns to the screen it was on. Display: press **Iniciar sesión** in the bottom line (it opens `/login?returnUrl=%2Fdisplay`) and sign in with the account of that screen; it returns to `/display`.
2. If the password is refused, the account is locked (5 failed attempts, 15 minutes) or was deactivated: an `ADMIN` can check it in **Administración → Personal**. Never reuse a personal account to get a screen back.
3. If the screen says it cannot connect rather than "La sesión terminó", it is a network problem, not the session: see `paper-fallback.md` while it lasts. The screens reconnect and re-read by themselves when the network returns.

## What not to do

- Do not leave the sign-in page open "for later" in the kitchen: the board is the only place orders are read from during service.
- Do not sign a display in with a personal or admin account that someone also uses elsewhere: it makes a deactivation affect the screen.
- Do not clear the browser's site data to "refresh" the session: it deletes the sign-in without ending it on the server and the screen falls to the login page at once.
