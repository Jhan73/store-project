# Runbook — keep serving on paper when the system is down

Goal: no sale lost, no sale counted twice, and the drawer reconciles at close. This is the answer to risk R7 (tech-spec §14): there is no offline mode, so when the connection or the system is gone, the store runs on paper and keys the paper in afterwards.

Use this when staff screens stay on the disconnected state, or the system does not answer, and it does not clear within a couple of minutes. It does not cover a bad deploy (use `rollback.md`).

During the M2 pilot paper runs in parallel with the system, so the paper is already complete: after an outage you only key in what the system missed. After launch paper is the only record of the outage, so it has to be complete from the first minute.

## What staff see

Table grid, ticket editor and board show a full-width notice (for example "Sin conexión en tiempo real. Las acciones se habilitan al reconectar.") and every action is disabled until the screen reconnects and reloads. Nothing can be keyed while it lasts, so do not wait at the screen. The customer screen keeps its last numbers and shows "Actualizando…".

## Before you need it

Keep at the register, always:

- A pad of **numbered** paper tickets and one cash sheet per shift.
- A printed price list, with the price of every extra (size, fruit, booster).
- Pens, and this page.

The `ADMIN` keeps a `VOID` reason such as "Keyed twice" in the reason lists (**Configuración**), so a double key-in can be corrected with a reason.

## 1. Decide

The person in charge of the shift decides: the `ADMIN` on duty, or whoever the `ADMIN` puts in charge. Nobody else switches the store to paper.

1. Try once: reload the screen and check the router. A single error toast with a retry is not an outage.
2. If the notice is still there after **2 minutes**, switch to paper. (Starting value: adjust it after the pilot.)
3. Write the time on top of the cash sheet.

## 2. Serve on paper

1. Tell the floor, the kitchen and the cashier out loud: "paper from now".
2. **Order:** one numbered paper ticket per table or counter customer. Write table or name, time, and for each line the quantity, product, extras, note, and a circled **ALLERGY** when it applies. The kitchen gets a slip with the same lines.
3. **Kitchen:** finish what was already on the board when the connection dropped, and tick the slips as they go out, since the buttons are disabled. New orders arrive as slips; call numbers aloud. Ignore the customer screen.
4. **Changes:** never erase. Strike the line through, write the reason (same reasons as the app), and write the new line below. A line struck after it was ready is waste: mark it **W**.
5. **Payments:** write each payment on the ticket: method (cash, card, wallet, transfer) and amount. For cash also the amount received and the change given. A split bill is one line per payment.
6. **Cash in or out of the drawer** (for example the fruit supplier): write time, in or out, amount, reason and reference on the cash sheet.
7. **Sold out:** the availability switch is off too, so tell the floor by voice and write the item on the cash sheet.

## 3. Back online

1. Confirm the notice is gone on the table grid, the board and **Caja**, and reload each one. Write the time on the cash sheet: from now on, orders go straight into the system.
2. Keep the paper tickets in order. Mark each one **keyed** only after step 4, never before.
3. **Check what the system already has**, for each paper ticket, before you key anything: open the table (or **Mostrador** customer) and read the lines, **Pagado** and **Falta cobrar**. A send or a payment that was in flight when the connection dropped may have been saved. Never press it again from memory.
4. **Key in, oldest first**, one ticket at a time, using the cashier whose shift is open (an `ADMIN` needs their own open shift to record payments):
   - Table order: open the table, add the lines as finally served (lines voided on paper are skipped: the system never saw them), write the paper ticket number in the note of the first line, **Enviar a cocina**, then **Cobrar** with the same methods and amounts. For cash type the amount received and check that the change shown equals the paper change.
   - Counter order paid on paper: **Venta rápida** with the paper number and the name in the customer name field, or a **Mostrador** ticket if an item is not on the quick-sale list.
   - Customers still seated and unpaid: open and send, leave it open.
   - Drawer movements: **Caja** → cash in or out, same amount, reason and reference as the sheet.
   - Then write **keyed**, the time and your initials on the paper ticket.
5. **Clear the board.** Keyed orders appear as new cards and on the customer screen, for food already served. The cook runs each one through **Iniciar**, **Todo listo** (it waits 5 seconds to allow undo) and **Entregar** until the board is empty. Do it as you key, not at the end. Preparation times of these cards are meaningless.
6. **Stock:** keying a closed ticket or a quick sale deducts stock, so do not adjust stock for paper sales. If a physical count differs afterwards, adjust with a reason in **Inventario**. Purchases received during the outage are keyed as entries.
7. **Time:** the system stamps the moment of keying and cannot be backdated. Key everything before closing the shift. Sales keyed after midnight (store time) land in the next business day: tell the `ADMIN`.

## 4. Close and reconcile

1. Before closing, check on paper: every ticket with a payment is marked **keyed**, and the count of keyed tickets equals the count of paid tickets. After the close the shift is immutable, and the screen shows no totals before it.
2. **Caja** → **Cerrar turno**. Count the drawer and type the count first, as always. Do not work out the expected cash from the paper beforehand.
3. Compare **Cobros por método** and **Efectivo esperado** with the paper totals per method. If the difference is over the threshold the screen asks for a note: write "paper fallback HH:MM–HH:MM, N tickets keyed" and the reason you suspect.
4. A difference is usually one of: a ticket not keyed, a ticket keyed twice, a wrong method, a missing cash movement, or a wrong amount received. A double key-in noticed right away is voided with **Anular venta** and the "Keyed twice" reason, from the ticket still on screen (the one shown after paying, or the **Ver la cuenta** link of a quick sale). There is no list of closed tickets yet, so one noticed later cannot be found by staff: the `ADMIN` writes it in the shift note. A missing ticket cannot be added to a closed shift either, so it goes in the same note.
5. **If the outage lasts past closing time:** count the drawer on paper and keep cash and sheet together. Do not force-close the shift: a forced close skips the count and shows the expected cash at once, so the reconciliation is lost. When the system returns, key in and close normally, typing the count you made at closing, and say so in the note.

## 5. What not to do

- Do not keep a second record (spreadsheet, chat, phone notes). Paper is the only record.
- Do not key a paper ticket twice, and do not repeat a payment without reading **Pagado** first.
- Do not void keyed sales to fix their time, and do not adjust stock to compensate for them.
- Do not key the paper from several tablets at once; one person keys, one person ticks.
- Do not close the shift before every paper ticket is keyed or accounted for.

## Afterwards

Tell the `ADMIN`: start and end time, number of paper tickets, and anything that did not fit this page. During the pilot it goes in the pilot issue list. If outages exceed one a month, the decision to revisit offline mode (R7) is the owner's.

## Not covered yet

- Online orders (milestone M3): they will need pausing online ordering as the first step, which this runbook cannot do while the system is down.
- Comp and merge are not built, so a comped item on paper is only written on the cash sheet.
- A list of closed tickets does not exist, so a sale keyed in by mistake cannot be found again once its screen is left.
- There is no way to record a paper sale without it appearing on the board: step 3.5 is the cost of that.
