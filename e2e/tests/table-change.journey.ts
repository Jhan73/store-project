import { expect, test } from '@playwright/test';
import { uniqueName, withAdminApi } from '../support/api';
import { addToTicket, AFTER_READY, boardCard, boardLine, SYNC, TABLET, ticketLine, WALL_SCREEN } from '../support/floor';
import { pickOption } from '../support/select';
import { openAdminPage } from '../support/session';

// The ticket is left open on purpose: the sweep settles it, as it does after a failed run.
test.afterAll(() => withAdminApi((api) => api.cleanUp()));

test('guests move to another table and the lines keep their state', async ({ browser }) => {
  test.setTimeout(180_000);
  const fromName = uniqueName('table-4');
  const toName = uniqueName('table-7');
  const mangoName = uniqueName('mango');
  const strawberryName = uniqueName('strawberry');
  const orangeName = uniqueName('orange');

  const { from, to, mango, strawberry, orange } = await withAdminApi(async (api) => {
    const category = await api.createCategory(uniqueName('category'));
    return {
      from: await api.createTable(fromName),
      to: await api.createTable(toName),
      mango: await api.createProduct(mangoName, category.id),
      strawberry: await api.createProduct(strawberryName, category.id),
      orange: await api.createProduct(orangeName, category.id),
    };
  });

  const server = await openAdminPage(browser, '/staff/tables', TABLET);
  const board = await openAdminPage(browser, '/staff/board', WALL_SCREEN);
  await expect(board.getByRole('heading', { name: 'Tablero' })).toBeVisible();

  await test.step('the server opens table 4 with a sent mango, a sent strawberry and an unsent orange', async () => {
    await server.getByTestId(`table-${from.id}`).click();
    await expect(server.getByTestId('ticket-title')).toContainText(fromName);
    await addToTicket(server, mango.id);
    await addToTicket(server, strawberry.id);
    await server.getByTestId('send').click();
    await expect(ticketLine(server, mangoName)).toContainText('Enviada');
    await expect(ticketLine(server, strawberryName)).toContainText('Enviada');
    await addToTicket(server, orange.id);
    await expect(ticketLine(server, orangeName)).toContainText('Sin enviar');
  });

  await test.step('the cook starts the order and marks the mango as ready', async () => {
    const card = boardCard(board, fromName);
    await expect(card).toBeVisible(SYNC);
    await card.getByTestId('start').click();
    await boardLine(card, mangoName).getByTestId(/^ready-line-/).click();
    await expect(ticketLine(server, mangoName)).toContainText('Lista', AFTER_READY);
  });

  await test.step('the server moves the ticket to table 7', async () => {
    await server.getByTestId('transfer-open').click();
    await pickOption(server, 'Mesa de destino', toName);
    await server.getByRole('dialog').getByTestId('fd-save').click();
    await expect(server.getByTestId('ticket-title')).toContainText(toName);
  });

  await test.step('the lines kept their state, also after a reload', async () => {
    const expectStates = async () => {
      await expect(server.getByTestId('ticket-title')).toContainText(toName);
      await expect(ticketLine(server, mangoName)).toContainText('Lista');
      await expect(ticketLine(server, strawberryName)).toContainText('Enviada');
      await expect(ticketLine(server, orangeName)).toContainText('Sin enviar');
    };
    await expectStates();
    await server.reload();
    await expectStates();
  });

  await test.step('the board card follows the ticket and keeps the ready line', async () => {
    const moved = boardCard(board, toName);
    await expect(moved).toBeVisible(SYNC);
    await expect(boardCard(board, fromName)).toHaveCount(0);
    await expect(boardLine(moved, mangoName).getByTestId(/^line-state-/)).toContainText('Lista');
    await expect(boardLine(moved, strawberryName).getByTestId(/^line-state-/)).toHaveCount(0);
  });

  await test.step('table 4 is free and table 7 is occupied', async () => {
    await server.goto('/staff/tables');
    await expect(server.getByTestId(`table-${from.id}`)).toContainText('Libre');
    await expect(server.getByTestId(`table-${to.id}`)).toContainText('Ocupada');
  });

  await server.context().close();
  await board.context().close();
});
