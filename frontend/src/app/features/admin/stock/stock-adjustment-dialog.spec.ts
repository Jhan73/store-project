import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { ActiveReason, StockItem } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { primeTranslation } from '../../../core/theme/prime-translation';
import {
  chooseOption,
  numberIsInvalid,
  optionLabels,
  selectIsInvalid,
  typeNumber,
} from '../../../testing/primeng-controls';
import { StockAdjustmentDialog } from './stock-adjustment-dialog';
import { StockApi } from './stock-api';

const API = 'http://api.test/api/v1';
const REASONS = `${API}/reasons?type=STOCK_ADJUSTMENT`;
const ADJUST = `${API}/admin/stock-items/p1/adjustments`;

const item: StockItem = {
  productId: 'p1',
  tracked: true,
  onHand: 10,
  reserved: 0,
  available: 10,
  lowStockThreshold: 5,
  low: false,
};

const reasons = [
  { type: 'STOCK_ADJUSTMENT', code: 'Damaged' },
  { type: 'STOCK_ADJUSTMENT', code: 'Other' },
] as ActiveReason[];

@Component({
  imports: [StockAdjustmentDialog],
  template: `
    <app-stock-adjustment-dialog
      [visible]="visible()"
      [item]="item"
      productName="Papaya"
      (saved)="saved.push($event)"
      (stale)="stale = stale + 1"
      (dismissed)="visible.set(false)"
    />
  `,
})
class Host {
  readonly visible = signal(true);
  readonly item = item;
  readonly saved: StockItem[] = [];
  stale = 0;
}

describe('StockAdjustmentDialog', () => {
  let http: HttpTestingController;
  let toast: ReturnType<typeof vi.spyOn>;

  const dialog = () => document.body.querySelector('[role="dialog"]') as HTMLElement;
  const inDialog = <T extends HTMLElement>(testId: string) =>
    document.body.querySelector<T>(`[role="dialog"] [data-testid="${testId}"]`)!;

  async function open(list: ActiveReason[] = reasons) {
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
    http.expectOne(REASONS).flush(list);
    await fixture.whenStable();
    return fixture;
  }

  const choose = (fixture: ComponentFixture<Host>, label: string) =>
    chooseOption(fixture, dialog(), 'adjust-reason', label);

  async function delta(fixture: ComponentFixture<Host>, value: string) {
    typeNumber(dialog(), 'adjust-delta', value);
    await fixture.whenStable();
  }

  function note(value: string) {
    const area = inDialog<HTMLTextAreaElement>('adjust-note');
    area.value = value;
    area.dispatchEvent(new Event('input'));
  }

  const save = () => inDialog<HTMLButtonElement>('adjust-save').click();

  const problem = (status: number, code: string, extra: Record<string, unknown> = {}) =>
    [
      { type: 'about:blank', status, code, correlationId: 'c', ...extra },
      { status, statusText: 'Error' },
    ] as const;

  afterEach(() => http.verify());

  it('names the product and offers the active stock adjustment reasons', async () => {
    const fixture = await open();

    expect(dialog().textContent).toContain('Papaya');
    expect(await optionLabels(fixture, dialog(), 'adjust-reason')).toEqual(['Damaged', 'Otro']);
  });

  it('subtracts units with a reason and an idempotency key', async () => {
    const fixture = await open();
    await delta(fixture, '-3');
    await choose(fixture, 'Damaged');

    save();
    const request = http.expectOne(ADJUST);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ quantityDelta: -3, reason: 'Damaged', note: null });
    expect(request.request.headers.get('Idempotency-Key')).toMatch(/^[0-9a-f-]{36}$/);
    request.flush({ ...item, onHand: 7, available: 7 });
    await fixture.whenStable();

    expect(fixture.componentInstance.saved).toEqual([{ ...item, onHand: 7, available: 7 }]);
  });

  it('adds units with a positive delta and sends the trimmed note', async () => {
    const fixture = await open();
    await delta(fixture, '4');
    await choose(fixture, 'Damaged');
    note('  Conteo de cierre ');

    save();

    const request = http.expectOne(ADJUST);
    expect(request.request.body).toEqual({ quantityDelta: 4, reason: 'Damaged', note: 'Conteo de cierre' });
    request.flush(item);
  });

  it('refuses a zero or empty change and marks the field', async () => {
    const fixture = await open();
    await choose(fixture, 'Damaged');

    save();
    await fixture.whenStable();
    expect(numberIsInvalid(dialog(), 'adjust-delta')).toBe(true);

    await delta(fixture, '0');
    save();
    await fixture.whenStable();

    http.expectNone(ADJUST);
    expect(numberIsInvalid(dialog(), 'adjust-delta')).toBe(true);
  });

  it('refuses a change without a reason and marks the select', async () => {
    const fixture = await open();
    await delta(fixture, '-1');

    save();
    await fixture.whenStable();

    http.expectNone(ADJUST);
    expect(selectIsInvalid(dialog(), 'adjust-reason')).toBe(true);
  });

  it('needs a note for the reason Other', async () => {
    const fixture = await open();
    await delta(fixture, '-1');
    await choose(fixture, 'Otro');

    save();
    await fixture.whenStable();
    http.expectNone(ADJUST);
    expect(inDialog('adjust-note-required')).not.toBeNull();

    note('Se cayó la caja');
    await fixture.whenStable();
    save();

    const request = http.expectOne(ADJUST);
    expect(request.request.body).toEqual({ quantityDelta: -1, reason: 'Other', note: 'Se cayó la caja' });
    request.flush(item);
  });

  it('limits the note to 300 characters', async () => {
    await open();

    expect(inDialog<HTMLTextAreaElement>('adjust-note').getAttribute('maxlength')).toBe('300');
  });

  it('keeps the same idempotency key on an unchanged retry and a new one after an edit', async () => {
    const fixture = await open();
    await delta(fixture, '-2');
    await choose(fixture, 'Damaged');

    save();
    const first = http.expectOne(ADJUST);
    const key = first.request.headers.get('Idempotency-Key');
    first.flush(...problem(500, 'common.internal-error'));
    await fixture.whenStable();
    save();
    const retry = http.expectOne(ADJUST);
    expect(retry.request.headers.get('Idempotency-Key')).toBe(key);
    retry.flush(...problem(500, 'common.internal-error'));
    await fixture.whenStable();
    await delta(fixture, '-3');
    save();
    const edited = http.expectOne(ADJUST);

    expect(edited.request.headers.get('Idempotency-Key')).not.toBe(key);
    edited.flush(item);
  });

  it('tells the user how many units are available and asks for a refresh when there is not enough stock', async () => {
    const fixture = await open();
    await delta(fixture, '-50');
    await choose(fixture, 'Damaged');

    save();
    http.expectOne(ADJUST).flush(
      ...problem(409, 'inventory.insufficient-stock', { productId: 'p1', available: 4 }),
    );
    await fixture.whenStable();

    expect(toast).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'error', summary: 'No hay suficiente stock disponible.' }),
    );
    expect(inDialog('adjust-available').textContent).toContain('4');
    expect(fixture.componentInstance.stale).toBe(1);
    expect(fixture.componentInstance.saved).toEqual([]);
  });

  it('says there are no reasons to pick from and sends nothing', async () => {
    await open([]);

    expect(inDialog('adjust-no-reasons')).not.toBeNull();
  });

  it('offers a retry when the reasons cannot be loaded', async () => {
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
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    http.expectOne(REASONS).flush(...problem(500, 'common.internal-error'));
    await fixture.whenStable();

    inDialog<HTMLButtonElement>('adjust-retry').click();
    await fixture.whenStable();
    http.expectOne(REASONS).flush(reasons);
    await fixture.whenStable();

    expect(inDialog('adjust-retry')).toBeNull();
    expect(await optionLabels(fixture, dialog(), 'adjust-reason')).toEqual(['Damaged', 'Otro']);
  });

  it('asks to be dismissed on cancel', async () => {
    const fixture = await open();

    inDialog<HTMLButtonElement>('adjust-cancel').click();
    await fixture.whenStable();

    expect(fixture.componentInstance.visible()).toBe(false);
  });
});
