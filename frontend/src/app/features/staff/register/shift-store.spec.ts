import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { pen, problem, shift } from '../../../testing/register-fixtures';
import { RegisterApi } from './register-api';
import { ShiftStore } from './shift-store';

const BASE = 'http://api.test/api/v1';
const notFound = problem(404, 'instore.shift-not-found');

describe('ShiftStore', () => {
  let store: ShiftStore;
  let http: HttpTestingController;
  let messages: MessageService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        MessageService,
        RegisterApi,
        ShiftStore,
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    store = TestBed.inject(ShiftStore);
    http = TestBed.inject(HttpTestingController);
    messages = TestBed.inject(MessageService);
  });

  afterEach(() => http.verify());

  const zone = () =>
    http
      .expectOne(`${BASE}/store/board-thresholds`)
      .flush({ boardWarningMinutes: 5, boardLateMinutes: 10, timeZone: 'America/Lima' });

  it('has no shift and reads the store currency when none is open', () => {
    store.load();
    http.expectOne(`${BASE}/shifts/current`).flush(notFound, { status: 404, statusText: 'Not Found' });
    zone();
    http.expectOne(`${BASE}/catalog/menu`).flush({
      categories: [{ id: 'c', products: [{ id: 'p', price: pen('9.00') }] }],
    });

    expect(store.loaded()).toBe(true);
    expect(store.shift()).toBeNull();
    expect(store.currency()).toBe('PEN');
    expect(store.timeZone()).toBe('America/Lima');
  });

  it('holds the open shift and does not look for a currency', () => {
    store.load();
    http.expectOne(`${BASE}/shifts/current`).flush(shift());
    zone();

    expect(store.shift()?.id).toBe('s1');
    expect(store.shift()?.expectedCash).toBeNull();
    expect(store.currency()).toBe('PEN');
  });

  it('reports a failed read and lets it be retried', () => {
    const added = vi.spyOn(messages, 'add');
    store.load();
    http
      .expectOne(`${BASE}/shifts/current`)
      .flush({ type: 'about:blank', status: 500, code: 'common.internal-error', correlationId: 'c' }, {
        status: 500,
        statusText: 'Server Error',
      });
    zone();

    expect(store.failed()).toBe(true);
    expect(store.loaded()).toBe(false);
    expect(added).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
  });

  it('formats a moment in the store time zone', () => {
    store.load();
    http.expectOne(`${BASE}/shifts/current`).flush(shift());
    zone();

    expect(store.formatTime('2026-10-13T14:00:00Z')).toContain('9:00');
  });

  describe('cash movements', () => {
    const out = { type: 'OUT' as const, amount: pen('5.00'), reasonCode: 'Change', note: null, reference: null };

    function withShift() {
      store.load();
      http.expectOne(`${BASE}/shifts/current`).flush(shift());
      zone();
    }

    it('reads the movements and the cash-out reasons of the open shift', () => {
      withShift();

      store.loadMovements();
      http.expectOne(`${BASE}/shifts/s1/cash-movements`).flush([{ id: 'm1', type: 'IN' }]);
      store.loadReasons();
      http.expectOne(`${BASE}/reasons?type=CASH_OUT`).flush([{ code: 'Change' }]);

      expect(store.movements()).toEqual([{ id: 'm1', type: 'IN' }]);
      expect(store.reasons()).toEqual([{ code: 'Change' }]);
    });

    it('records a movement with its key, reads the movements again and reports it done', () => {
      withShift();
      const done = vi.fn();

      store.recordMovement(out, done);
      const request = http.expectOne(`${BASE}/shifts/s1/cash-movements`);
      expect(request.request.method).toBe('POST');
      expect(request.request.body).toEqual(out);
      expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
      request.flush({ id: 'm1' }, { status: 201, statusText: 'Created' });
      http.expectOne(`${BASE}/shifts/s1/cash-movements`).flush([{ id: 'm1' }]);

      expect(done).toHaveBeenCalled();
      expect(store.movements()).toEqual([{ id: 'm1' }]);
    });

    it('retries a failed movement with the same key and a new one for the next', () => {
      withShift();
      const added = vi.spyOn(messages, 'add');

      store.recordMovement(out, vi.fn());
      const first = http.expectOne(`${BASE}/shifts/s1/cash-movements`);
      const key = first.request.headers.get('Idempotency-Key');
      first.flush(problem(503, 'common.service-unavailable'), { status: 503, statusText: 'Unavailable' });
      http.expectOne(`${BASE}/shifts/s1/cash-movements`).flush([]);
      expect(added).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));

      store.recordMovement(out, vi.fn());
      const second = http.expectOne(`${BASE}/shifts/s1/cash-movements`);
      expect(second.request.headers.get('Idempotency-Key')).toBe(key);
      second.flush({ id: 'm1' }, { status: 201, statusText: 'Created' });
      http.expectOne(`${BASE}/shifts/s1/cash-movements`).flush([]);

      store.recordMovement(out, vi.fn());
      const third = http.expectOne(`${BASE}/shifts/s1/cash-movements`);
      expect(third.request.headers.get('Idempotency-Key')).not.toBe(key);
      third.flush({ id: 'm2' }, { status: 201, statusText: 'Created' });
      http.expectOne(`${BASE}/shifts/s1/cash-movements`).flush([]);
    });

    it('reads the shift again when a movement is refused because the shift is closing', () => {
      withShift();

      store.recordMovement(out, vi.fn());
      http
        .expectOne(`${BASE}/shifts/s1/cash-movements`)
        .flush(problem(409, 'instore.shift-closing'), { status: 409, statusText: 'Conflict' });
      http.expectOne(`${BASE}/shifts/s1/cash-movements`).flush([]);
      http.expectOne(`${BASE}/shifts/current`).flush(shift());
      zone();
    });
  });

  describe('opening a shift', () => {
    function loadedWithoutShift() {
      store.load();
      http.expectOne(`${BASE}/shifts/current`).flush(notFound, { status: 404, statusText: 'Not Found' });
      zone();
      http.expectOne(`${BASE}/catalog/menu`).flush({
        categories: [{ id: 'c', products: [{ id: 'p', price: pen('9.00') }] }],
      });
    }

    it('opens with the float, the store currency and an idempotency key', () => {
      loadedWithoutShift();

      store.open('50.00');
      const request = http.expectOne(`${BASE}/shifts`);
      expect(request.request.body).toEqual({ openingFloat: pen('50.00') });
      expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
      request.flush(shift(), { status: 201, statusText: 'Created' });

      expect(store.shift()?.id).toBe('s1');
    });

    it('retries with the same key and uses a new one after success', () => {
      loadedWithoutShift();
      store.open('50.00');
      const first = http.expectOne(`${BASE}/shifts`);
      const key = first.request.headers.get('Idempotency-Key');
      first.flush(
        { type: 'about:blank', status: 503, code: 'common.service-unavailable', correlationId: 'c' },
        { status: 503, statusText: 'Unavailable' },
      );
      http.expectOne(`${BASE}/shifts/current`).flush(notFound, { status: 404, statusText: 'Not Found' });
      zone();

      store.open('50.00');
      const second = http.expectOne(`${BASE}/shifts`);
      expect(second.request.headers.get('Idempotency-Key')).toBe(key);
      second.flush(shift(), { status: 201, statusText: 'Created' });
    });

    it('re-reads the current shift when the open is refused', () => {
      loadedWithoutShift();
      const added = vi.spyOn(messages, 'add');

      store.open('50.00');
      http.expectOne(`${BASE}/shifts`).flush(
        { type: 'about:blank', status: 409, code: 'instore.shift-already-open', correlationId: 'c' },
        { status: 409, statusText: 'Conflict' },
      );
      http.expectOne(`${BASE}/shifts/current`).flush(shift());
      zone();

      expect(added).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
      expect(store.shift()?.id).toBe('s1');
    });

    it('does nothing without a store currency', () => {
      store.load();
      http.expectOne(`${BASE}/shifts/current`).flush(notFound, { status: 404, statusText: 'Not Found' });
      zone();
      http.expectOne(`${BASE}/catalog/menu`).flush({ categories: [] });

      store.open('50.00');

      expect(store.currency()).toBeNull();
    });
  });
});
