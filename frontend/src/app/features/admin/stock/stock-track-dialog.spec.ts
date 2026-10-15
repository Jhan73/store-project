import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { Product, StockItem } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { primeTranslation } from '../../../core/theme/prime-translation';
import {
  chooseOption,
  numberIsInvalid,
  optionLabels,
  selectIsInvalid,
  typeNumber,
} from '../../../testing/primeng-controls';
import { StockApi } from './stock-api';
import { StockTrackDialog } from './stock-track-dialog';

const STOCK = 'http://api.test/api/v1/admin/stock-items';
const PAPAYA = 'p-papaya';
const MANGO = 'p-mango';
const KIWI = 'p-kiwi';

const products = [
  { id: PAPAYA, name: 'Papaya' },
  { id: MANGO, name: 'Mango' },
  { id: KIWI, name: 'Kiwi' },
] as Product[];

function stock(productId: string, overrides: Partial<StockItem> = {}): StockItem {
  return {
    productId,
    tracked: true,
    onHand: 0,
    reserved: 0,
    available: 0,
    lowStockThreshold: 5,
    low: true,
    ...overrides,
  };
}

@Component({
  imports: [StockTrackDialog],
  template: `
    <app-stock-track-dialog
      [visible]="visible()"
      [target]="target()"
      [products]="products()"
      [catalogStatus]="catalogStatus()"
      (catalogRetried)="catalogRetries = catalogRetries + 1"
      (saved)="saved.push($event)"
      (dismissed)="visible.set(false)"
    />
  `,
})
class Host {
  readonly visible = signal(true);
  readonly target = signal<StockItem | null>(null);
  readonly products = signal<Product[]>(products);
  readonly catalogStatus = signal<'loading' | 'ready' | 'failed'>('ready');
  catalogRetries = 0;
  readonly saved: StockItem[] = [];
}

describe('StockTrackDialog', () => {
  let http: HttpTestingController;
  let toast: ReturnType<typeof vi.spyOn>;

  const dialog = () => document.body.querySelector('[role="dialog"]') as HTMLElement;
  const inDialog = <T extends HTMLElement>(testId: string) =>
    document.body.querySelector<T>(`[role="dialog"] [data-testid="${testId}"]`);

  async function open(
    target: StockItem | null = null,
    tracked: StockItem[] = [],
    setup: (host: Host) => void = () => undefined,
  ) {
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
    http = TestBed.inject(HttpTestingController);
    toast = vi.spyOn(TestBed.inject(MessageService), 'add');
    const fixture = TestBed.createComponent(Host);
    fixture.componentInstance.target.set(target);
    setup(fixture.componentInstance);
    await fixture.whenStable();
    if (!target) {
      http.expectOne(STOCK).flush(tracked);
      await fixture.whenStable();
    }
    return fixture;
  }

  const choose = (fixture: ComponentFixture<Host>, label: string) =>
    chooseOption(fixture, dialog(), 'stock-track-product', label);

  afterEach(() => http.verify());

  it('offers only the products that are not tracked yet, by name', async () => {
    const fixture = await open(null, [stock(MANGO)]);

    expect(await optionLabels(fixture, dialog(), 'stock-track-product')).toEqual(['Kiwi', 'Papaya']);
  });

  it('says so when every product is already tracked', async () => {
    await open(null, [stock(PAPAYA), stock(MANGO), stock(KIWI)]);

    expect(inDialog('track-none')).not.toBeNull();
  });

  it('does not claim everything is tracked while the catalog is loading', async () => {
    await open(null, [], (host) => {
      host.products.set([]);
      host.catalogStatus.set('loading');
    });

    expect(inDialog('track-none')).toBeNull();
    expect(inDialog('track-catalog-loading')).not.toBeNull();
  });

  it('offers a retry instead of claiming everything is tracked when the catalog failed', async () => {
    const fixture = await open(null, [], (host) => {
      host.products.set([]);
      host.catalogStatus.set('failed');
    });

    expect(inDialog('track-none')).toBeNull();
    inDialog<HTMLButtonElement>('track-catalog-retry')!.click();

    expect(fixture.componentInstance.catalogRetries).toBe(1);
  });

  it('says the catalog is empty, not that everything is tracked, when it has no products', async () => {
    await open(null, [], (host) => host.products.set([]));

    expect(inDialog('track-none')).toBeNull();
    expect(inDialog('track-empty-catalog')).not.toBeNull();
  });

  it('starts tracking the chosen product with its threshold', async () => {
    const fixture = await open();
    await choose(fixture, 'Papaya');
    typeNumber(dialog(), 'stock-threshold', '8');
    await fixture.whenStable();

    inDialog<HTMLButtonElement>('track-save')!.click();
    const request = http.expectOne(`${STOCK}/${PAPAYA}`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ tracked: true, lowStockThreshold: 8 });
    request.flush(stock(PAPAYA, { lowStockThreshold: 8 }));
    await fixture.whenStable();

    expect(fixture.componentInstance.saved).toEqual([stock(PAPAYA, { lowStockThreshold: 8 })]);
  });

  it('accepts a threshold of zero', async () => {
    const fixture = await open();
    await choose(fixture, 'Kiwi');
    typeNumber(dialog(), 'stock-threshold', '0');
    await fixture.whenStable();

    inDialog<HTMLButtonElement>('track-save')!.click();

    const request = http.expectOne(`${STOCK}/${KIWI}`);
    expect(request.request.body).toEqual({ tracked: true, lowStockThreshold: 0 });
    request.flush(stock(KIWI, { lowStockThreshold: 0 }));
  });

  it('does not send anything without a product and a threshold, and marks both', async () => {
    const fixture = await open();

    inDialog<HTMLButtonElement>('track-save')!.click();
    await fixture.whenStable();

    http.expectNone(`${STOCK}/${PAPAYA}`);
    expect(selectIsInvalid(dialog(), 'stock-track-product')).toBe(true);
    expect(numberIsInvalid(dialog(), 'stock-threshold')).toBe(true);
  });

  it.each(['-5', '1000001', '2.5'])(
    'refuses the threshold %s instead of correcting it',
    async (value) => {
      const fixture = await open();
      await choose(fixture, 'Papaya');
      typeNumber(dialog(), 'stock-threshold', value);
      await fixture.whenStable();

      inDialog<HTMLButtonElement>('track-save')!.click();
      await fixture.whenStable();

      http.expectNone(`${STOCK}/${PAPAYA}`);
      expect(numberIsInvalid(dialog(), 'stock-threshold')).toBe(true);
    },
  );

  it('keeps the dialog open and tells the user when the request is refused', async () => {
    const fixture = await open();
    await choose(fixture, 'Papaya');
    typeNumber(dialog(), 'stock-threshold', '8');
    await fixture.whenStable();

    inDialog<HTMLButtonElement>('track-save')!.click();
    http.expectOne(`${STOCK}/${PAPAYA}`).flush(
      { type: 'about:blank', status: 404, code: 'inventory.product-not-found', correlationId: 'c' },
      { status: 404, statusText: 'Not Found' },
    );
    await fixture.whenStable();

    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
    expect(dialog()).not.toBeNull();
    expect(fixture.componentInstance.saved).toEqual([]);
  });

  it('edits only the threshold of a tracked product', async () => {
    const fixture = await open(stock(PAPAYA, { lowStockThreshold: 5 }));

    expect(inDialog('stock-track-product')).toBeNull();
    expect(dialog().textContent).toContain('Papaya');
    typeNumber(dialog(), 'stock-threshold', '12');
    await fixture.whenStable();
    inDialog<HTMLButtonElement>('track-save')!.click();

    const request = http.expectOne(`${STOCK}/${PAPAYA}`);
    expect(request.request.body).toEqual({ tracked: true, lowStockThreshold: 12 });
    request.flush(stock(PAPAYA, { lowStockThreshold: 12 }));
  });

  it('asks to be dismissed on cancel', async () => {
    const fixture = await open();

    inDialog<HTMLButtonElement>('track-cancel')!.click();
    await fixture.whenStable();

    expect(fixture.componentInstance.visible()).toBe(false);
  });
});
