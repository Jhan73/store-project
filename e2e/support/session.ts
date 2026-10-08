import type { Browser, Page } from '@playwright/test';
import { adminCredentials } from './env';

export async function signIn(page: Page): Promise<void> {
  const { email, password } = adminCredentials();
  await page.goto('/login');
  await page.getByLabel('Correo electrónico').fill(email);
  await page.getByLabel('Contraseña').fill(password);
  await page.getByRole('button', { name: 'Entrar' }).click();
  await page.waitForURL((url) => !url.pathname.startsWith('/login'), { timeout: 30_000 });
}

// Each session is its own sign-in: the refresh cookie rotates on every use and reuse revokes the whole session
// family, so a saved storage state cannot be shared between contexts or runs.
export async function openAdminPage(browser: Browser, path: string): Promise<Page> {
  const context = await browser.newContext();
  const page = await context.newPage();
  await signIn(page);
  await page.goto(path);
  return page;
}
