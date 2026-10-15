import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { StockItem } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { primeTranslation } from '../../../core/theme/prime-translation';
import { numberIsInvalid, typeNumber } from '../../../testing/primeng-controls';
import { StockApi } from './stock-api';
import { StockEntryDialog } from './stock-entry-dialog';

const ENTRY = 'http://api.test/api/v1/admin/stock-items/p1/entries';

const item: StockItem = {
  productId: 'p1',
  tracked: true,
  onHand: 10,
  reserved: 0,
  available: 10,
  lowStockThreshold: 5,
  low: false,
};

@Component({
  imports: [StockEntryDialog],
  template: `
    <app-stock-entry-dialog
      [visible]="visible()"
      [item]="item"
      productName="Papaya"
      (saved)="saved.push($event)"
      (dismissed)="visible.set(false)"
    />
  `,
})
class Host {
  readonly visible = signal(true);
  readonly item = item;
  readonly saved: StockItem[] = [];
}

describe('StockEntryDialog', () => {
  let http: HttpTestingController;
  let toast: ReturnType<typeof vi.spyOn>;

  const dialog = () => document.body.querySelector('[role="dialog"]') as HTMLElement;
  const inDialog = <T extends HTMLElement>(testId: string) =>
    document.body.querySelector<T>(`[role="dialog"] [data-testid="${testId}"]`)!;

  async function open() {
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
    await fixture.whenStable();
    return fixture;
  }

  async function quantity(fixture: { whenStable(): Promise<unknown> }, value: string) {
    typeNumber(dialog(), 'entry-quantity', value);
    await fixture.whenStable();
  }

  function note(value: string) {
    const area = inDialog<HTMLTextAreaElement>('entry-note');
    area.value = value;
    area.dispatchEvent(new Event('input'));
  }

  const refused = [
    { type: 'about:blank', status: 500, code: 'common.internal-error', correlationId: 'c' },
    { status: 500, statusText: 'Error' },
  ] as const;

  afterEach(() => http.verify());

  it('names the product in the dialog', async () => {
    await open();

    expect(dialog().textContent).toContain('Papaya');
  });

  it('records the entry with its quantity, a trimmed note and an idempotency key', async () => {
    const fixture = await open();
    await quantity(fixture, '25');
    note('  Proveedor Lima ');

    inDialog<HTMLButtonElement>('entry-save').click();
    const request = http.expectOne(ENTRY);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ quantity: 25, note: 'Proveedor Lima' });
    expect(request.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    request.flush({ ...item, onHand: 35, available: 35 });
    await fixture.whenStable();

    expect(fixture.componentInstance.saved).toEqual([{ ...item, onHand: 35, available: 35 }]);
  });

  it('sends no note when it is blank', async () => {
    const fixture = await open();
    await quantity(fixture, '3');
    note('   ');

    inDialog<HTMLButtonElement>('entry-save').click();

    const request = http.expectOne(ENTRY);
    expect(request.request.body).toEqual({ quantity: 3, note: null });
    request.flush(item);
  });

  it('limits the note to 300 characters', async () => {
    await open();

    expect(inDialog<HTMLTextAreaElement>('entry-note').getAttribute('maxlength')).toBe('300');
  });

  it('refuses an empty quantity and marks the field', async () => {
    const fixture = await open();

    inDialog<HTMLButtonElement>('entry-save').click();
    await fixture.whenStable();

    http.expectNone(ENTRY);
    expect(numberIsInvalid(dialog(), 'entry-quantity')).toBe(true);
  });

  it('never sends less than one unit: the field raises a typed zero to the minimum', async () => {
    const fixture = await open();
    await quantity(fixture, '0');

    inDialog<HTMLButtonElement>('entry-save').click();

    const request = http.expectOne(ENTRY);
    expect(request.request.body.quantity).toBe(1);
    request.flush(item);
  });

  it('keeps the same idempotency key when the same entry is retried', async () => {
    const fixture = await open();
    await quantity(fixture, '5');

    inDialog<HTMLButtonElement>('entry-save').click();
    const first = http.expectOne(ENTRY);
    const key = first.request.headers.get('Idempotency-Key');
    first.flush(...refused);
    await fixture.whenStable();
    inDialog<HTMLButtonElement>('entry-save').click();
    const second = http.expectOne(ENTRY);

    expect(second.request.headers.get('Idempotency-Key')).toBe(key);
    second.flush(item);
    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
  });

  it('uses a new idempotency key once the entry was edited', async () => {
    const fixture = await open();
    await quantity(fixture, '5');

    inDialog<HTMLButtonElement>('entry-save').click();
    const first = http.expectOne(ENTRY);
    const key = first.request.headers.get('Idempotency-Key');
    first.flush(...refused);
    await fixture.whenStable();
    await quantity(fixture, '6');
    inDialog<HTMLButtonElement>('entry-save').click();
    const second = http.expectOne(ENTRY);

    expect(second.request.headers.get('Idempotency-Key')).not.toBe(key);
    second.flush(item);
  });

  it('keeps the dialog open and the typed quantity when the entry is refused', async () => {
    const fixture = await open();
    await quantity(fixture, '5');

    inDialog<HTMLButtonElement>('entry-save').click();
    http.expectOne(ENTRY).flush(
      { type: 'about:blank', status: 409, code: 'inventory.product-not-tracked', correlationId: 'c' },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();

    expect(fixture.componentInstance.saved).toEqual([]);
    expect(document.body.querySelector<HTMLInputElement>('#entry-quantity')!.value).toBe('5');
  });

  it('does not send a second request while the first is in flight', async () => {
    const fixture = await open();
    await quantity(fixture, '5');

    inDialog<HTMLButtonElement>('entry-save').click();
    inDialog<HTMLButtonElement>('entry-save').click();

    http.expectOne(ENTRY).flush(item);
  });

  it('asks to be dismissed on cancel', async () => {
    const fixture = await open();

    inDialog<HTMLButtonElement>('entry-cancel').click();
    await fixture.whenStable();

    expect(fixture.componentInstance.visible()).toBe(false);
  });
});
