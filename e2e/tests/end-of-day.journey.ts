import { expect, test } from '@playwright/test';
import { minorUnits, uniqueName, withAdminApi } from '../support/api';
import { pickOption } from '../support/select';
import { openAdminPage } from '../support/session';

test.afterAll(() => withAdminApi((api) => api.cleanUp()));

const FLOAT = 100_00;
const CASH_OUT = 40_00;

// A formatted amount without the currency symbol and with the sign the difference must carry: counting less than
// expected is negative, counting more is not.
function signed(minor: number, negative: boolean): RegExp {
  return new RegExp(`^${negative ? '-' : ''}[^\\d-]*${amount(minor).replace('.', '\\.')}$`);
}

function amount(minor: number): string {
  return `${Math.floor(minor / 100)}.${String(minor % 100).padStart(2, '0')}`;
}

test('end of day: cash out for the supplier, blind close and a justified difference', async ({ browser }) => {
  const reasonCode = uniqueName('supplier');
  const reference = uniqueName('fruit-supplier');

  // Counting 5.00 above the allowed difference makes the note mandatory whatever the store configured.
  const threshold = await withAdminApi(async (api) => {
    // The register reads the store currency from the menu prices, so the menu cannot be empty.
    const category = await api.createCategory(uniqueName('category'));
    await api.createProduct(uniqueName('juice'), category.id);
    await api.closeOpenShift();
    await api.createReason('CASH_OUT', reasonCode);
    return minorUnits(await api.registerDifferenceThreshold());
  });
  const expected = FLOAT - CASH_OUT;
  const gap = threshold + 5_00;
  const below = expected - gap >= 0;
  const counted = below ? expected - gap : expected + gap;

  const page = await openAdminPage(browser, '/staff/register');

  await test.step('the cashier opens a shift with a float', async () => {
    await page.locator('#float').fill(amount(FLOAT));
    await page.getByTestId('open-submit').click();
    await expect(page.getByTestId('opening-float')).toContainText(amount(FLOAT));
  });

  await test.step('the cashier records a cash out for the fruit supplier', async () => {
    await page.getByTestId('mv-open').click();
    const movement = page.getByRole('dialog');
    await pickOption(page, 'Motivo', reasonCode);
    await movement.locator('#mv-amount').fill(amount(CASH_OUT));
    await movement.getByTestId('mv-reference').fill(reference);
    await movement.getByTestId('fd-save').click();
    await expect(movement).toHaveCount(0);
    const row = page.getByRole('row', { name: new RegExp(reference) });
    await expect(row).toContainText(amount(CASH_OUT));
    await expect(row).toContainText(reasonCode);
  });

  const dialog = page.getByRole('dialog').filter({ hasText: 'Cerrar turno' });

  await test.step('the close is blind: the count is asked before any expected cash is shown', async () => {
    await page.getByTestId('close-open').click();
    await expect(dialog.locator('#close-count')).toBeVisible();
    await expect(dialog.getByTestId('expected-cash')).toHaveCount(0);
    await expect(dialog).not.toContainText('Efectivo esperado');
    await dialog.locator('#close-count').fill(amount(counted));
    await dialog.getByTestId('close-confirm').click();
  });

  await test.step('the difference is shown, the count is locked and the note is required', async () => {
    await expect(dialog.getByTestId('close-difference').locator('strong')).toHaveText(signed(gap, below));
    await expect(dialog.locator('#close-count')).not.toBeEditable();
    await expect(dialog.getByTestId('close-confirm')).toBeDisabled();
    await dialog.getByTestId('close-note').fill('e2e: missing small change');
    await dialog.getByTestId('close-confirm').click();
  });

  await test.step('the closed shift shows expected cash, count, difference and note', async () => {
    await expect(dialog.getByTestId('expected-cash')).toContainText(amount(expected));
    await expect(dialog.getByTestId('counted-cash')).toContainText(amount(counted));
    await expect(dialog.getByTestId('difference')).toHaveText(signed(gap, below));
    await expect(dialog.getByTestId('difference-note')).toContainText('e2e: missing small change');
    await expect(dialog.getByTestId('cash-in')).toHaveText(/^[^\d-]*0\.00$/);
    await dialog.getByTestId('close-done').click();
    await expect(page.getByTestId('closed-summary')).toBeVisible();
  });

  await page.context().close();
});
