import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { Product, StockItem } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { primeTranslation } from '../../../core/theme/prime-translation';
import { chooseOption, typeNumber } from '../../../testing/primeng-controls';
import { StockApi } from './stock-api';
import { StockList } from './stock-list';

const API = 'http://api.test/api/v1';
const STOCK = `${API}/admin/stock-items`;
const PRODUCTS = `${API}/admin/products?page=0&size=100`;

const PAPAYA = '0199f3a2-0000-7000-8000-00000000000a';
const MANGO = '0199f3a2-0000-7000-8000-00000000000b';
const GHOST = '0199f3a2-0000-7000-8000-00000000000c';

function stock(productId: string, onHand: number, overrides: Partial<StockItem> = {}): StockItem {
  return {
    productId,
    tracked: true,
    onHand,
    reserved: 0,
    available: onHand,
    lowStockThreshold: 5,
    low: onHand <= 5,
    ...overrides,
  };
}

function product(id: string, name: string): Product {
  return { id, name } as Product;
}

function productPage(content: Product[]) {
  return { content, page: 0, size: 100, totalElements: content.length, totalPages: 1 };
}

const papaya = product(PAPAYA, 'Papaya');
const mango = product(MANGO, 'Mango');

async function render(items: StockItem[] = [], products: Product[] = [papaya, mango]) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      MessageService,
      providePrimeNG({ translation: primeTranslation }),
      StockApi,
      { provide: API_ORIGIN, useValue: 'http://api.test' },
    ],
  });
  const fixture = TestBed.createComponent(StockList);
  const http = TestBed.inject(HttpTestingController);
  const toast = vi.spyOn(TestBed.inject(MessageService), 'add');
  await fixture.whenStable();
  http.expectOne(STOCK).flush(items);
  http.expectOne(PRODUCTS).flush(productPage(products));
  await fixture.whenStable();
  return { fixture, host: fixture.nativeElement as HTMLElement, http, toast };
}

function rows(host: HTMLElement) {
  return Array.from(host.querySelectorAll('tbody tr')).map((row) => row.textContent ?? '');
}

const click = (host: HTMLElement, testId: string) =>
  host.querySelector<HTMLElement>(`[data-testid="${testId}"]`)!.click();

const lowSwitch = (host: HTMLElement) =>
  host.querySelector<HTMLInputElement>('p-toggleswitch[data-testid="low-only"] input')!;

describe('StockList', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('names each tracked item from the catalog and sorts them by name', async () => {
    const { host } = await render([stock(PAPAYA, 40), stock(MANGO, 3)]);

    const [first, second] = rows(host);
    expect(first).toContain('Mango');
    expect(second).toContain('Papaya');
    expect(second).toContain('40');
  });

  it('shows on hand, reserved, available and the threshold of an item', async () => {
    const { host } = await render([
      stock(PAPAYA, 120, { reserved: 20, available: 100, lowStockThreshold: 50, low: false }),
    ]);

    const row = host.querySelector('tbody tr')!;
    const cells = Array.from(row.querySelectorAll('td')).map((cell) => cell.textContent?.trim());
    expect(cells.slice(0, 5)).toEqual(['Papaya', '100', '120', '20', '50']);
  });

  it('flags the items at or below their threshold with a text badge', async () => {
    const { host } = await render([stock(PAPAYA, 40), stock(MANGO, 3)]);

    const [mangoRow, papayaRow] = rows(host);
    expect(mangoRow).toContain('Stock bajo');
    expect(papayaRow).not.toContain('Stock bajo');
  });

  it('shows a shortened id for an item whose product the catalog no longer lists', async () => {
    const { host } = await render([stock(GHOST, 7)]);

    expect(rows(host)[0]).toContain('0199f3a2…');
  });

  it('reads only the low-stock items, emptiest first, while the filter is on', async () => {
    const { fixture, host, http } = await render([stock(PAPAYA, 40), stock(MANGO, 3)]);

    lowSwitch(host).click();
    await fixture.whenStable();
    http.expectOne(`${STOCK}/low`).flush([stock(PAPAYA, 0), stock(MANGO, 3)]);
    await fixture.whenStable();

    const [first, second] = rows(host);
    expect(first).toContain('Papaya');
    expect(second).toContain('Mango');
  });

  it('goes back to the whole list when the filter is turned off', async () => {
    const { fixture, host, http } = await render([stock(PAPAYA, 40), stock(MANGO, 3)]);

    lowSwitch(host).click();
    await fixture.whenStable();
    http.expectOne(`${STOCK}/low`).flush([stock(MANGO, 3)]);
    await fixture.whenStable();
    lowSwitch(host).click();
    await fixture.whenStable();
    http.expectOne(STOCK).flush([stock(PAPAYA, 40), stock(MANGO, 3)]);
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(2);
  });

  it('keeps only the answer to the latest filter when answers arrive out of order', async () => {
    const { fixture, host, http } = await render([stock(PAPAYA, 40), stock(MANGO, 3)]);

    lowSwitch(host).click();
    await fixture.whenStable();
    const low = http.expectOne(`${STOCK}/low`);
    lowSwitch(host).click();
    await fixture.whenStable();
    const all = http.expectOne(STOCK);
    all.flush([stock(PAPAYA, 40), stock(MANGO, 3)]);
    low.flush([stock(MANGO, 3)]);
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(2);
  });

  it('explains that nothing is tracked yet', async () => {
    const { host } = await render([]);

    expect(host.querySelector('[data-testid="empty"]')!.textContent).toContain(
      'Todavía no hay productos con control de stock',
    );
    expect(host.querySelector('table')).toBeNull();
  });

  it('says so when no item is low', async () => {
    const { fixture, host, http } = await render([stock(PAPAYA, 40)]);

    lowSwitch(host).click();
    await fixture.whenStable();
    http.expectOne(`${STOCK}/low`).flush([]);
    await fixture.whenStable();

    expect(host.querySelector('[data-testid="empty"]')!.textContent).toContain(
      'Ningún producto tiene el stock bajo',
    );
  });

  describe('tracking', () => {
    const trackedSwitch = (host: HTMLElement, productId: string) =>
      host.querySelector<HTMLInputElement>(`p-toggleswitch[data-testid="tracked-${productId}"] input`)!;
    const dialog = () => document.body.querySelector('[role="dialog"]') as HTMLElement | null;

    it('opens the picker from the toolbar and reloads the list after a product is tracked', async () => {
      const { fixture, host, http } = await render([stock(PAPAYA, 40)]);

      click(host, 'track-new');
      await fixture.whenStable();
      http.expectOne(STOCK).flush([stock(PAPAYA, 40)]);
      await fixture.whenStable();
      expect(dialog()!.textContent).toContain('Controlar un producto');
      await chooseOption(fixture, dialog()!, 'stock-track-product', 'Mango');
      typeNumber(dialog()!, 'stock-threshold', '4');
      await fixture.whenStable();
      dialog()!.querySelector<HTMLButtonElement>('[data-testid="track-save"]')!.click();
      http.expectOne(`${STOCK}/${MANGO}`).flush(stock(MANGO, 0, { lowStockThreshold: 4 }));
      await fixture.whenStable();
      http.expectOne(STOCK).flush([stock(PAPAYA, 40), stock(MANGO, 0, { lowStockThreshold: 4 })]);
      await fixture.whenStable();

      expect(dialog()).toBeNull();
      expect(rows(host)).toHaveLength(2);
    });

    it('edits the threshold of an item from its row', async () => {
      const { fixture, host, http } = await render([stock(PAPAYA, 40, { lowStockThreshold: 5 })]);

      click(host, `threshold-${PAPAYA}`);
      await fixture.whenStable();
      expect(dialog()!.textContent).toContain('Papaya');
      typeNumber(dialog()!, 'stock-threshold', '30');
      await fixture.whenStable();
      dialog()!.querySelector<HTMLButtonElement>('[data-testid="track-save"]')!.click();
      const request = http.expectOne(`${STOCK}/${PAPAYA}`);
      expect(request.request.body).toEqual({ tracked: true, lowStockThreshold: 30 });
      request.flush(stock(PAPAYA, 40, { lowStockThreshold: 30, low: false }));
      await fixture.whenStable();
      http.expectOne(STOCK).flush([stock(PAPAYA, 40, { lowStockThreshold: 30, low: false })]);
      await fixture.whenStable();

      expect(rows(host)[0]).toContain('30');
    });

    it('stops tracking an item, keeping its threshold, and drops its row', async () => {
      const { fixture, host, http } = await render([
        stock(PAPAYA, 40, { lowStockThreshold: 7 }),
        stock(MANGO, 3),
      ]);

      trackedSwitch(host, PAPAYA).click();
      await fixture.whenStable();
      expect(trackedSwitch(host, PAPAYA).disabled).toBe(true);
      const request = http.expectOne(`${STOCK}/${PAPAYA}`);
      expect(request.request.method).toBe('PUT');
      expect(request.request.body).toEqual({ tracked: false, lowStockThreshold: 7 });
      request.flush(stock(PAPAYA, 40, { tracked: false, lowStockThreshold: 7 }));
      await fixture.whenStable();

      expect(rows(host)).toHaveLength(1);
      expect(rows(host)[0]).toContain('Mango');
    });

    it('puts the switch back and tells the user when units are still reserved', async () => {
      const { fixture, host, http, toast } = await render([stock(PAPAYA, 40, { reserved: 2 })]);

      trackedSwitch(host, PAPAYA).click();
      await fixture.whenStable();
      http.expectOne(`${STOCK}/${PAPAYA}`).flush(
        { type: 'about:blank', status: 409, code: 'inventory.stock-reserved', correlationId: 'c' },
        { status: 409, statusText: 'Conflict' },
      );
      await fixture.whenStable();

      expect(toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
      expect(trackedSwitch(host, PAPAYA).checked).toBe(true);
      expect(trackedSwitch(host, PAPAYA).disabled).toBe(false);
      expect(rows(host)).toHaveLength(1);
    });
  });

  it('shows an error with a retry when the stock cannot be read', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        MessageService,
        providePrimeNG({ translation: primeTranslation }),
        StockApi,
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    const fixture = TestBed.createComponent(StockList);
    const http = TestBed.inject(HttpTestingController);
    const toast = vi.spyOn(TestBed.inject(MessageService), 'add');
    const host = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
    http.expectOne(STOCK).flush(
      { type: 'about:blank', status: 500, code: 'common.internal-error', correlationId: 'c' },
      { status: 500, statusText: 'Error' },
    );
    http.expectOne(PRODUCTS).flush(productPage([papaya]));
    await fixture.whenStable();

    expect(host.querySelector('[role="alert"]')).not.toBeNull();
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));

    click(host, 'retry');
    await fixture.whenStable();
    http.expectOne(STOCK).flush([stock(PAPAYA, 40)]);
    http.expectOne(PRODUCTS).flush(productPage([papaya]));
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(1);
  });
});
