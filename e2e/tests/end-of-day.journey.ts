import { expect, test } from '@playwright/test';
import { uniqueName, withAdminApi, type Money } from '../support/api';
import { pickOption } from '../support/select';
import { openAdminPage } from '../support/session';

test.afterAll(() => withAdminApi((api) => api.cleanUp()));

const FLOAT = 100_00;
const CASH_OUT = 40_00;

function minorUnits(money: Money): number {
  const [whole = '0', cents = '0'] = money.amount.split('.');
  return Number(whole) * 100 + Number(cents.padEnd(2, '0'));
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
  const counted = expected - gap >= 0 ? expected - gap : expected + gap;

  const page = await openAdminPage(browser, '/staff/register');

  await test.step('the cashier opens a shift with a float', async () => {
    await page.getByTestId('float').fill(amount(FLOAT));
    await page.getByTestId('open-submit').click();
    await expect(page.getByTestId('opening-float')).toContainText(amount(FLOAT));
  });

  await test.step('the cashier records a cash out for the fruit supplier', async () => {
    await pickOption(page, 'Motivo', reasonCode);
    await page.getByTestId('mv-amount').fill(amount(CASH_OUT));
    await page.getByTestId('mv-reference').fill(reference);
    await page.getByTestId('mv-submit').click();
    const row = page.getByRole('row', { name: new RegExp(reference) });
    await expect(row).toContainText(amount(CASH_OUT));
    await expect(row).toContainText(reasonCode);
  });

  const dialog = page.getByRole('dialog');

  await test.step('the close is blind: the count is asked before any expected cash is shown', async () => {
    await page.getByTestId('close-open').click();
    await expect(dialog.getByTestId('close-count')).toBeVisible();
    await expect(dialog.getByTestId('expected-cash')).toHaveCount(0);
    await expect(dialog).not.toContainText('Efectivo esperado');
    await dialog.getByTestId('close-count').fill(amount(counted));
    await dialog.getByTestId('close-confirm').click();
  });

  await test.step('the difference is shown, the count is locked and the note is required', async () => {
    await expect(dialog.getByTestId('close-difference')).toContainText(amount(gap));
    await expect(dialog.getByTestId('close-count')).not.toBeEditable();
    await expect(dialog.getByTestId('close-confirm')).toBeDisabled();
    await dialog.getByTestId('close-note').fill('e2e: missing small change');
    await dialog.getByTestId('close-confirm').click();
  });

  await test.step('the closed shift shows expected cash, count, difference and note', async () => {
    await expect(dialog.getByTestId('expected-cash')).toContainText(amount(expected));
    await expect(dialog.getByTestId('counted-cash')).toContainText(amount(counted));
    await expect(dialog.getByTestId('difference')).toContainText(amount(gap));
    await expect(dialog.getByTestId('difference-note')).toContainText('e2e: missing small change');
    await expect(dialog.getByTestId('cash-in')).toContainText('0.00');
    await dialog.getByTestId('close-done').click();
    await expect(page.getByTestId('closed-summary')).toBeVisible();
  });

  await page.context().close();
});
