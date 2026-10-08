import { expect, test, type Page } from '@playwright/test';
import { AdminApi, uniqueName } from '../support/api';
import { openAdminPage } from '../support/session';

const SYNC_WINDOW = { timeout: 5_000 };

test.afterAll(async () => {
  const api = await AdminApi.create();
  try {
    await api.cleanUp();
  } finally {
    await api.dispose();
  }
});

function productRow(page: Page, name: string) {
  return page.getByRole('listitem').filter({ hasText: name });
}

test('staff availability changes show on another open screen within 5 seconds', async ({ browser }) => {
  const productName = uniqueName('juice');
  const api = await AdminApi.create();
  try {
    const category = await api.createCategory(uniqueName('category'));
    await api.createProduct(productName, category.id);
  } finally {
    await api.dispose();
  }

  const staff = await openAdminPage(browser, '/staff/availability');
  const board = await openAdminPage(browser, '/staff/availability');
  const staffRow = productRow(staff, productName);
  const boardRow = productRow(board, productName);

  await expect(boardRow).toContainText('Disponible');
  await expect(staffRow.getByRole('switch', { name: productName })).toBeEnabled();
  await expect(boardRow.getByRole('switch', { name: productName })).toBeEnabled();

  await staffRow.getByRole('switch', { name: productName }).click();
  await expect(staffRow).toContainText('Agotado');
  await expect(boardRow).toContainText('Agotado', SYNC_WINDOW);

  await staffRow.getByRole('switch', { name: productName }).click();
  await expect(staffRow).toContainText('Disponible');
  await expect(boardRow).toContainText('Disponible', SYNC_WINDOW);

  await staff.context().close();
  await board.context().close();
});
