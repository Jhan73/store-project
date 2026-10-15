import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Observable } from 'rxjs';
import { API_ORIGIN } from '../../../core/api/api-config';
import { BoardApi } from './board-api';

const BASE = 'http://api.test/api/v1';

describe('BoardApi', () => {
  let api: BoardApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        BoardApi,
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    api = TestBed.inject(BoardApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads the cards to prepare together with the server clock', () => {
    let result: unknown;
    api.tickets().subscribe((value) => (result = value));

    const request = http.expectOne(`${BASE}/board/tickets`);
    expect(request.request.method).toBe('GET');
    request.flush([{ id: 'o1' }], { headers: { Date: 'Tue, 13 Oct 2026 10:00:00 GMT' } });

    expect(result).toEqual({ orders: [{ id: 'o1' }], serverDate: 'Tue, 13 Oct 2026 10:00:00 GMT' });
  });

  it('reads the ready cards, the all-day view and the age thresholds', () => {
    api.ready().subscribe();
    http.expectOne(`${BASE}/board/ready`).flush([]);
    api.allDay().subscribe();
    http.expectOne(`${BASE}/board/all-day`).flush({ groups: [], individual: [] });
    let thresholds: unknown;
    api.thresholds().subscribe((value) => (thresholds = value));
    http.expectOne(`${BASE}/store/board-thresholds`).flush({ boardWarningMinutes: 4, boardLateMinutes: 8 });

    expect(thresholds).toEqual({ boardWarningMinutes: 4, boardLateMinutes: 8 });
  });

  it('asks each view for one station only when a station is given', () => {
    api.tickets('s1').subscribe();
    http.expectOne(`${BASE}/board/tickets?station=s1`).flush([]);
    api.ready('s1').subscribe();
    http.expectOne(`${BASE}/board/ready?station=s1`).flush([]);
    api.allDay('s1').subscribe();
    http.expectOne(`${BASE}/board/all-day?station=s1`).flush({ groups: [], individual: [] });
    api.tickets(null).subscribe();
    http.expectOne(`${BASE}/board/tickets`).flush([]);
  });

  it('reads the stations floor staff can filter by', () => {
    let result: unknown;
    api.stations().subscribe((value) => (result = value));

    http.expectOne(`${BASE}/stations`).flush([{ id: 's1', name: 'Bar' }]);

    expect(result).toEqual([{ id: 's1', name: 'Bar' }]);
  });

  it.each([
    ['start', 'orders/o1/start'],
    ['readyOrder', 'orders/o1/ready'],
    ['handover', 'orders/o1/handover'],
    ['recall', 'orders/o1/recall'],
    ['readyLine', 'lines/o1/ready'],
    ['flagLine', 'lines/o1/flag'],
    ['acknowledge', 'markers/o1/ack'],
  ] as const)('posts %s without a body and without an idempotency key', (method, path) => {
    (api[method]('o1') as Observable<unknown>).subscribe();

    const request = http.expectOne(`${BASE}/board/${path}`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toBeNull();
    expect(request.request.headers.has('Idempotency-Key')).toBe(false);
    request.flush({});
  });

  it('lets a ready command outlive the page', () => {
    api.readyOrder('o1', true).subscribe();
    api.readyLine('l1', true).subscribe();

    expect(http.expectOne(`${BASE}/board/orders/o1/ready`).request.keepalive).toBe(true);
    expect(http.expectOne(`${BASE}/board/lines/l1/ready`).request.keepalive).toBe(true);
  });
});
