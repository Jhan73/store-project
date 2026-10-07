import { expect, type Page } from '@playwright/test';

// Every p-select has a filter and renders its options in an overlay appended to the body.
export async function pickOption(page: Page, selectName: string, optionText: string): Promise<void> {
  await page.getByRole('combobox', { name: selectName }).click();
  await page.getByRole('searchbox').fill(optionText);
  await page.getByRole('option', { name: optionText }).click();
  await expect(page.getByRole('searchbox')).toHaveCount(0);
}
