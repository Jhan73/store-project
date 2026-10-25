import { expect, test } from '@playwright/test';
import { uniqueName, withAdminApi } from '../support/api';
import {
  acknowledgeAll,
  addToTicket,
  AFTER_READY,
  boardCard,
  boardLine,
  SYNC,
  TABLET,
  ticketIdOf,
  ticketLine,
  WALL_SCREEN,
} from '../support/floor';
import { pickOption } from '../support/select';
import { openAdminPage } from '../support/session';

test.afterAll(() => withAdminApi((api) => api.cleanUp()));

test('table service with a change: sent, voided, prepared on the board and paid in two parts', async ({ browser }) => {
  test.setTimeout(180_000);
  const tableName = uniqueName('table-4');
  const mangoName = uniqueName('mango');
  const strawberryName = uniqueName('strawberry');
  const reasonCode = uniqueName('changed-mind');

  const { table, mango, strawberry } = await withAdminApi(async (api) => {
    const category = await api.createCategory(uniqueName('category'));
    const created = {
      mango: await api.createProduct(mangoName, category.id, { price: '12.00' }),
      strawberry: await api.createProduct(strawberryName, category.id, { price: '10.00' }),
      table: await api.createTable(tableName),
    };
    await api.createReason('VOID', reasonCode);
    await api.openShift('100.00');
    return created;
  });

  const server = await openAdminPage(browser, '/staff/tables', TABLET);
  const board = await openAdminPage(browser, '/staff/board', WALL_SCREEN);
  await expect(board.getByRole('heading', { name: 'Tablero' })).toBeVisible();
  const card = boardCard(board, tableName);

  await test.step('the server opens the table and sends two mango juices', async () => {
    await server.getByTestId(`table-${table.id}`).click();
    await expect(server.getByTestId('ticket-title')).toContainText(tableName);
    await addToTicket(server, mango.id);
    await addToTicket(server, mango.id);
    await expect(ticketLine(server, mangoName)).toHaveCount(2);
    await server.getByTestId('send').click();
    await expect(ticketLine(server, mangoName).filter({ hasText: 'Enviada' })).toHaveCount(2);
  });

  await test.step('the order reaches the board labelled as new', async () => {
    await expect(card).toBeVisible(SYNC);
    await expect(card).toContainText('NUEVO');
    await expect(boardLine(card, mangoName)).toHaveCount(1);
    await expect(boardLine(card, mangoName)).toContainText('2×');
  });

  await test.step('the customer switches one mango to strawberry', async () => {
    await ticketLine(server, mangoName).first().getByRole('button', { name: 'Anular' }).click();
    await pickOption(server, 'Motivo', reasonCode);
    await server.getByRole('dialog').getByTestId('fd-save').click();
    await expect(ticketLine(server, mangoName).filter({ hasText: 'Anulada' })).toHaveCount(1);

    await addToTicket(server, strawberry.id);
    await server.getByTestId('send').click();
    await expect(ticketLine(server, strawberryName)).toContainText('Enviada');
    await expect(server.getByTestId('ticket-total')).toContainText('22.00');
  });

  await test.step('the board shows the voided mango and the added strawberry', async () => {
    await expect(card.getByText('ANULADO')).toBeVisible(SYNC);
    await expect(card.getByText('MODIFICADO')).toBeVisible(SYNC);
    await expect(boardLine(card, strawberryName)).toBeVisible();
  });

  await test.step('the cook acknowledges the labels, starts the order and marks it ready', async () => {
    await acknowledgeAll(card, 3);
    await card.getByTestId('start').click();
    await card.getByTestId('ready-order').click();
    await board.getByTestId('view-ready').click();
    await expect(card.getByTestId('handover')).toBeVisible(AFTER_READY);
  });

  await test.step('the server sees the lines as ready', async () => {
    await expect(ticketLine(server, mangoName).filter({ hasText: 'Lista' })).toHaveCount(1, SYNC);
    await expect(ticketLine(server, strawberryName)).toContainText('Lista', SYNC);
  });

  const cashier = await openAdminPage(browser, `/staff/tickets/${ticketIdOf(server)}`);

  await test.step('the cashier splits the bill into cash and wallet', async () => {
    await expect(cashier.getByTestId('ticket-total')).toContainText('22.00');
    await cashier.getByTestId('pay-open').click();
    await pickOption(cashier, 'Partes iguales', '2', { exact: true });
    await cashier.getByTestId('split-load').click();

    await cashier.getByTestId('split-part-0').click();
    await cashier.locator('#pay-received').fill('20');
    await cashier.getByTestId('pay-submit').click();
    await expect(cashier.getByTestId('ticket-paid')).toContainText('11.00');
    await expect(cashier.getByTestId(/^payment-/).first()).toContainText('9.00');

    await pickOption(cashier, 'Método de pago', 'Billetera');
    await cashier.getByTestId('split-part-1').click();
    await cashier.getByTestId('pay-submit').click();
    await expect(cashier.getByTestId('closed-notice')).toBeVisible();
  });

  await test.step('the ticket is closed and the table is free', async () => {
    await server.goto('/staff/tables');
    await expect(server.getByTestId(`table-${table.id}`)).toContainText('Libre');
  });

  await test.step('the cook hands the order over', async () => {
    await card.getByTestId('handover').click();
    await expect(card).toHaveCount(0, SYNC);
  });

  await server.context().close();
  await board.context().close();
  await cashier.context().close();
});
