import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { LineRequest } from '../../../core/api/api-types';
import { TicketsApi } from './tickets-api';

const BASE = 'http://api.test/api/v1';

const line: LineRequest = {
  productId: 'p1',
  quantity: 2,
  modifierOptionIds: ['o1'],
  note: null,
  allergy: false,
};

describe('TicketsApi', () => {
  let api: TicketsApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        TicketsApi,
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    api = TestBed.inject(TicketsApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads the table grid together with the server clock', () => {
    let result: unknown;
    api.tables().subscribe((value) => (result = value));

    const request = http.expectOne(`${BASE}/tables`);
    expect(request.request.method).toBe('GET');
    request.flush([{ id: 't1' }], { headers: { Date: 'Tue, 13 Oct 2026 10:00:00 GMT' } });

    expect(result).toEqual({ tables: [{ id: 't1' }], serverDate: 'Tue, 13 Oct 2026 10:00:00 GMT' });
  });

  it('reads the open tickets, one ticket, the menu and the board', () => {
    api.openTickets().subscribe();
    http.expectOne(`${BASE}/tickets`).flush([]);
    api.ticket('k1').subscribe();
    http.expectOne(`${BASE}/tickets/k1`).flush({});
    api.menu().subscribe();
    http.expectOne(`${BASE}/catalog/menu`).flush({ categories: [] });
    api.board().subscribe();
    http.expectOne(`${BASE}/board/tickets`).flush([]);
  });

  it('opens a ticket with the idempotency key of the intent', () => {
    api.open({ tableId: 't1' }, 'key-1').subscribe();

    const request = http.expectOne(`${BASE}/tickets`);
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Idempotency-Key')).toBe('key-1');
    expect(request.request.body).toEqual({ tableId: 't1' });
    request.flush({});
  });

  it('adds a line with its idempotency key', () => {
    api.addLine('k1', line, 'key-2').subscribe();

    const request = http.expectOne(`${BASE}/tickets/k1/lines`);
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Idempotency-Key')).toBe('key-2');
    expect(request.request.body).toEqual(line);
    request.flush({});
  });

  it('changes and removes an unsent line', () => {
    api.changeLine('k1', 'l1', line).subscribe();
    const change = http.expectOne(`${BASE}/tickets/k1/lines/l1`);
    expect(change.request.method).toBe('PUT');
    change.flush({});

    api.removeLine('k1', 'l1').subscribe();
    const removal = http.expectOne(`${BASE}/tickets/k1/lines/l1`);
    expect(removal.request.method).toBe('DELETE');
    removal.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('sends the ticket to the kitchen', () => {
    api.send('k1').subscribe();

    const request = http.expectOne(`${BASE}/tickets/k1/send`);
    expect(request.request.method).toBe('POST');
    request.flush({});
  });

  it('transfers the ticket to another table', () => {
    api.transfer('k1', 't2').subscribe();

    const request = http.expectOne(`${BASE}/tickets/k1/transfer`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ tableId: 't2' });
    request.flush({});
  });

  it('repeats lines with an idempotency key', () => {
    api.repeat('k1', ['l1', 'l2'], 'key-3').subscribe();

    const request = http.expectOne(`${BASE}/tickets/k1/repeat`);
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Idempotency-Key')).toBe('key-3');
    expect(request.request.body).toEqual({ lineIds: ['l1', 'l2'] });
    request.flush({});
  });

  it('voids a sent line with a reason and an idempotency key', () => {
    api.voidLine('k1', 'l1', { reason: 'Error', note: null }, 'key-4').subscribe();

    const request = http.expectOne(`${BASE}/tickets/k1/lines/l1/void`);
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('Idempotency-Key')).toBe('key-4');
    expect(request.request.body).toEqual({ reason: 'Error', note: null });
    request.flush({});
  });

  it('sets the bill request flag', () => {
    api.setBillRequested('k1', true).subscribe();

    const request = http.expectOne(`${BASE}/tickets/k1/bill-requested`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ requested: true });
    request.flush({});
  });
});
