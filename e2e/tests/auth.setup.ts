import { expect, test } from '@playwright/test';
import { signIn } from '../support/session';

test('the admin signs in through the login screen', async ({ page }) => {
  await signIn(page);
  await expect(page.getByRole('heading', { level: 1 })).toBeVisible();
});
