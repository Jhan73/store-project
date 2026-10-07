import { expect, type Page } from '@playwright/test';

// Every p-select has a filter and renders its options in an overlay appended to the body.
export async function pickOption(page: Page, selectName: string, optionText: string): Promise<void> {
  await page.getByRole('combobox', { name: selectName }).click();
  const overlay = page.locator('.p-select-overlay');
  await overlay.getByRole('searchbox').fill(optionText);
  await overlay.getByRole('option', { name: optionText }).click();
  await expect(overlay).toHaveCount(0);
}
