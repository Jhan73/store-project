import { expect, test } from '@playwright/test';
import { uniqueName, withAdminApi } from '../support/api';
import { AFTER_READY, boardCard, SYNC, WALL_SCREEN } from '../support/floor';
import { openAdminPage } from '../support/session';

test.afterAll(() => withAdminApi((api) => api.cleanUp()));

test('counter quick sale paid in cash shows the change and moves through the board and the customer screen', async ({
  browser,
}) => {
  test.setTimeout(180_000);
  // The customer screen shows only the first 20 characters of a name, so the name has to fit.
  const customer = uniqueName('an');

  const items = await withAdminApi(async (api) => {
    const category = await api.createCategory(uniqueName('category'));
    const created = [];
    for (const [label, price] of [['orange', '8.00'], ['strawberry', '10.00'], ['pineapple', '9.00']] as const) {
      created.push(await api.createProduct(uniqueName(label), category.id, { price, quickSalePinned: true }));
    }
    await api.openShift('50.00');
    return created;
  });

  const cashier = await openAdminPage(browser, '/staff/quick-sale');
  const board = await openAdminPage(browser, '/staff/board', WALL_SCREEN);
  const display = await openAdminPage(browser, '/display', WALL_SCREEN);

  await test.step('the cashier picks three frequent items, names the customer and enters the cash received', async () => {
    for (const item of items) {
      await cashier.getByTestId(`item-${item.id}`).click();
    }
    await expect(cashier.getByTestId('cart-total')).toContainText('27.00');
    await cashier.getByTestId('customer-name').fill(customer);
    await cashier.getByTestId('qs-received').fill('50');
    await cashier.getByTestId('qs-submit').click();
  });

  await test.step('the sale shows its number, total and change', async () => {
    await expect(cashier.getByTestId('sale-result')).toBeVisible();
    await expect(cashier.getByTestId('sale-total')).toContainText('27.00');
    await expect(cashier.getByTestId('sale-change')).toContainText('23.00');
  });

  const card = boardCard(board, customer);
  let number = '';

  await test.step('the order appears on the board and on the customer screen', async () => {
    await expect(card).toBeVisible(SYNC);
    await expect(card.getByTestId('short-number')).toHaveText(/^Q\d+$/);
    number = (await card.getByTestId('short-number').textContent()) ?? '';
    const preparing = display.getByTestId('column-preparing');
    await expect(preparing).toContainText(customer, SYNC);
    await expect(preparing).toContainText(number);
  });

  await test.step('the cook prepares it and the customer screen moves it to ready', async () => {
    await card.getByTestId('start').click();
    await card.getByTestId('ready-order').click();
    await expect(display.getByTestId('column-ready')).toContainText(customer, AFTER_READY);
    await expect(display.getByTestId('column-preparing')).not.toContainText(customer);
  });

  await test.step('the order is handed over and leaves both screens', async () => {
    await board.getByTestId('view-ready').click();
    await card.getByTestId('handover').click();
    await expect(card).toHaveCount(0, SYNC);
    await expect(display.getByTestId('column-ready')).not.toContainText(customer, SYNC);
  });

  await cashier.context().close();
  await board.context().close();
  await display.context().close();
});
