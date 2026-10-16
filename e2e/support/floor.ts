import { expect, type Locator, type Page } from '@playwright/test';

const ID = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}';

// The sizes the staff screens are laid out for: a 10" tablet in landscape and a 1080p wall screen.
export const TABLET = { viewport: { width: 1024, height: 768 }, hasTouch: true };
export const WALL_SCREEN = { viewport: { width: 1920, height: 1080 } };

// The board and the screens read each other's changes through the realtime signal, then re-read.
export const SYNC = { timeout: 15_000 };
// A Ready waits five seconds on the client before it is sent, so what depends on it needs more than SYNC.
export const AFTER_READY = { timeout: 25_000 };

// A line of the ticket editor; the test id of its status ("line-status-<id>") never matches.
export function ticketLine(page: Page, productName: string): Locator {
  return page.getByTestId(new RegExp(`^line-${ID}$`)).filter({ hasText: productName });
}

export function boardCard(page: Page, label: string): Locator {
  return page.getByTestId(new RegExp(`^card-${ID}$`)).filter({ hasText: label });
}

export function boardLine(card: Locator, productName: string): Locator {
  return card.getByTestId(new RegExp(`^line-${ID}$`)).filter({ hasText: productName });
}

export function ticketIdOf(page: Page): string {
  const id = new URL(page.url()).pathname.split('/').pop();
  if (!id) throw new Error(`The page is not on a ticket: ${page.url()}`);
  return id;
}

export async function acknowledgeAll(card: Locator, expected: number): Promise<void> {
  const acks = card.getByTestId(/^ack-/);
  await expect(acks).toHaveCount(expected);
  for (let remaining = expected; remaining > 0; remaining--) {
    await acks.first().click();
    await expect(acks).toHaveCount(remaining - 1);
  }
}

export async function addToTicket(page: Page, productId: string): Promise<void> {
  await page.getByTestId(`product-${productId}`).click();
  await page.getByTestId('submit').click();
  await expect(page.getByTestId('submit')).toHaveCount(0);
}
