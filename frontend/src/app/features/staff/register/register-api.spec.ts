import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { Shift } from '../../../core/api/api-types';
import { ApiError } from '../../../core/errors/api-error';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { RegisterApi, type CloseOutcome } from './register-api';

const BASE = 'http://api.test/api/v1';
const pen = (amount: string) => ({ amount, currency: 'PEN' });

const problem = (status: number, code: string, extra: Record<string, unknown> = {}) => ({
  type: 'about:blank',
  status,
  code,
  correlationId: 'c1',
  ...extra,
});

describe('RegisterApi', () => {
  let api: RegisterApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        RegisterApi,
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    api = TestBed.inject(RegisterApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('opens a shift with the idempotency key of the intent', () => {
    api.openShift({ openingFloat: pen('50.00') }, 'key-1').subscribe();

    const request = http.expectOne(`${BASE}/shifts`);
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Idempotency-Key')).toBe('key-1');
    expect(request.request.body).toEqual({ openingFloat: pen('50.00') });
    request.flush({}, { status: 201, statusText: 'Created' });
  });

  it('reads the current shift', () => {
    let result: Shift | null | undefined;
    api.currentShift().subscribe((shift) => (result = shift));

    http.expectOne(`${BASE}/shifts/current`).flush({ id: 's1', status: 'OPEN' });

    expect(result).toEqual({ id: 's1', status: 'OPEN' });
  });

  it('reads "no shift" as null instead of an error', () => {
    let result: Shift | null | undefined;
    let failed = false;
    api.currentShift().subscribe({ next: (shift) => (result = shift), error: () => (failed = true) });

    http
      .expectOne(`${BASE}/shifts/current`)
      .flush(problem(404, 'instore.shift-not-found'), { status: 404, statusText: 'Not Found' });

    expect(result).toBeNull();
    expect(failed).toBe(false);
  });

  it('keeps every other failure of the current shift read an error', () => {
    let error: unknown;
    api.currentShift().subscribe({ error: (value) => (error = value) });

    http
      .expectOne(`${BASE}/shifts/current`)
      .flush(problem(403, 'auth.forbidden'), { status: 403, statusText: 'Forbidden' });

    expect(error).toBeInstanceOf(ApiError);
  });

  it('reads one shift and the paged shifts by status', () => {
    api.shift('s1').subscribe();
    http.expectOne(`${BASE}/shifts/s1`).flush({});

    api.shifts('OPEN', 2, 50).subscribe();
    const request = http.expectOne(`${BASE}/shifts?status=OPEN&page=2&size=50`);
    expect(request.request.method).toBe('GET');
    request.flush({ content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 });
  });

  it('closes a shift with the counted cash and no idempotency key', () => {
    let outcome: CloseOutcome | undefined;
    api.closeShift('s1', { countedCash: pen('100.00'), note: null }).subscribe((value) => (outcome = value));

    const request = http.expectOne(`${BASE}/shifts/s1/close`);
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.has('Idempotency-Key')).toBe(false);
    expect(request.request.body).toEqual({ countedCash: pen('100.00'), note: null });
    request.flush({ id: 's1', status: 'CLOSED' });

    expect(outcome).toEqual({ kind: 'closed', shift: { id: 's1', status: 'CLOSED' } });
  });

  it('types a close that needs a note and carries the difference', () => {
    let outcome: CloseOutcome | undefined;
    api.closeShift('s1', { countedCash: pen('90.00') }).subscribe((value) => (outcome = value));

    http
      .expectOne(`${BASE}/shifts/s1/close`)
      .flush(problem(409, 'instore.difference-note-required', { difference: pen('-10.00') }), {
        status: 409,
        statusText: 'Conflict',
      });

    expect(outcome).toEqual({ kind: 'note-required', difference: pen('-10.00') });
  });

  it('types a close whose count is locked to the one already shown', () => {
    let outcome: CloseOutcome | undefined;
    api.closeShift('s1', { countedCash: pen('91.00') }).subscribe((value) => (outcome = value));

    http
      .expectOne(`${BASE}/shifts/s1/close`)
      .flush(problem(422, 'instore.counted-cash-locked'), { status: 422, statusText: 'Unprocessable' });

    expect(outcome).toEqual({ kind: 'count-locked' });
  });

  it('keeps the other close failures errors', () => {
    let error: unknown;
    api.closeShift('s1', { countedCash: pen('90.00') }).subscribe({ error: (value) => (error = value) });

    http
      .expectOne(`${BASE}/shifts/s1/close`)
      .flush(problem(409, 'instore.shift-closed'), { status: 409, statusText: 'Conflict' });

    expect(error).toBeInstanceOf(ApiError);
  });

  it('force-closes a shift with a reason and no idempotency key', () => {
    api.forceClose('s1', { reason: 'Left early' }).subscribe();

    const request = http.expectOne(`${BASE}/shifts/s1/force-close`);
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.has('Idempotency-Key')).toBe(false);
    expect(request.request.body).toEqual({ reason: 'Left early' });
    request.flush({});
  });

  it('lists and records cash movements, the latter with an idempotency key', () => {
    api.movements('s1').subscribe();
    http.expectOne(`${BASE}/shifts/s1/cash-movements`).flush([]);

    api
      .recordMovement('s1', { type: 'OUT', amount: pen('5.00'), reasonCode: 'Change', note: null, reference: null }, 'key-2')
      .subscribe();
    const request = http.expectOne(`${BASE}/shifts/s1/cash-movements`);
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Idempotency-Key')).toBe('key-2');
    request.flush({}, { status: 201, statusText: 'Created' });
  });

  it('records a payment with its idempotency key', () => {
    api.pay('k1', { method: 'CASH', amount: pen('10.00'), receivedAmount: pen('20.00') }, 'key-3').subscribe();

    const request = http.expectOne(`${BASE}/tickets/k1/payments`);
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Idempotency-Key')).toBe('key-3');
    expect(request.request.body).toEqual({
      method: 'CASH',
      amount: pen('10.00'),
      receivedAmount: pen('20.00'),
    });
    request.flush({}, { status: 201, statusText: 'Created' });
  });

  it('voids a payment and a sale without an idempotency key', () => {
    api.voidPayment('k1', 'p1', { reason: 'Error', note: null }).subscribe();
    const payment = http.expectOne(`${BASE}/tickets/k1/payments/p1/void`);
    expect(payment.request.method).toBe('POST');
    expect(payment.request.headers.has('Idempotency-Key')).toBe(false);
    expect(payment.request.body).toEqual({ reason: 'Error', note: null });
    payment.flush({});

    api.voidSale('k1', { reason: 'Error', note: 'x' }).subscribe();
    const sale = http.expectOne(`${BASE}/tickets/k1/void`);
    expect(sale.request.method).toBe('POST');
    expect(sale.request.headers.has('Idempotency-Key')).toBe(false);
    expect(sale.request.body).toEqual({ reason: 'Error', note: 'x' });
    sale.flush({});
  });

  it('reads the parts of an even split', () => {
    let parts: unknown;
    api.split('k1', 3).subscribe((value) => (parts = value));

    const request = http.expectOne(`${BASE}/tickets/k1/split?parts=3`);
    expect(request.request.headers.has('Idempotency-Key')).toBe(false);
    request.flush({ parts: [pen('3.34'), pen('3.33'), pen('3.33')] });

    expect(parts).toEqual([pen('3.34'), pen('3.33'), pen('3.33')]);
  });

  it('reads the quick-sale items and records a quick sale with its idempotency key', () => {
    api.quickSaleItems().subscribe();
    http.expectOne(`${BASE}/quick-sales/items`).flush([]);

    const sale = {
      customerName: null,
      lines: [{ productId: 'p1', quantity: 1, modifierOptionIds: [], note: null, allergy: false }],
      payment: { method: 'CARD' as const, receivedAmount: null },
    };
    api.quickSale(sale, 'key-4').subscribe();
    const request = http.expectOne(`${BASE}/quick-sales`);
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Idempotency-Key')).toBe('key-4');
    expect(request.request.body).toEqual(sale);
    request.flush({}, { status: 201, statusText: 'Created' });
  });

  it('reads the store time zone from the board thresholds', () => {
    let zone: string | undefined;
    api.timeZone().subscribe((value) => (zone = value));

    http
      .expectOne(`${BASE}/store/board-thresholds`)
      .flush({ boardWarningMinutes: 5, boardLateMinutes: 10, timeZone: 'America/Lima' });

    expect(zone).toBe('America/Lima');
  });

  it('takes the store currency from the menu prices', () => {
    let currency: string | null | undefined;
    api.currency().subscribe((value) => (currency = value));

    http.expectOne(`${BASE}/catalog/menu`).flush({
      categories: [{ id: 'c', products: [{ id: 'p', price: pen('9.00') }] }],
    });

    expect(currency).toBe('PEN');
  });

  it('has no currency to offer while the menu has no products', () => {
    let currency: string | null | undefined;
    api.currency().subscribe((value) => (currency = value));

    http.expectOne(`${BASE}/catalog/menu`).flush({ categories: [] });

    expect(currency).toBeNull();
  });

  it('reads every page of the staff accounts', () => {
    let names: unknown;
    api.staffAccounts().subscribe((value) => (names = value));

    http
      .expectOne(`${BASE}/staff?page=0&size=100`)
      .flush({ content: [{ id: 'a' }], page: 0, size: 100, totalElements: 2, totalPages: 2 });
    http
      .expectOne(`${BASE}/staff?page=1&size=100`)
      .flush({ content: [{ id: 'b' }], page: 1, size: 100, totalElements: 2, totalPages: 2 });

    expect(names).toEqual([{ id: 'a' }, { id: 'b' }]);
  });
});
