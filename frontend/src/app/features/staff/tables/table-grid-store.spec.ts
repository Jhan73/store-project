import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { createEnvironmentInjector, EnvironmentInjector, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { Observable, Subject } from 'rxjs';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { TableGridEntry, Ticket } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { RealtimeClient } from '../../../core/realtime/realtime-client';
import { problem } from '../../../testing/register-fixtures';
import { TicketsApi } from '../tickets/tickets-api';
import { TableGridStore } from './table-grid-store';

const BASE = 'http://api.test/api/v1';
const TABLES = `${BASE}/tables`;
const TICKETS = `${BASE}/tickets`;
const TICKET_PAGE = `${BASE}/tickets?page=0&size=100`;
const emptyPage = { content: [], page: 0, size: 100, totalElements: 0, totalPages: 1 };

class FakeRealtime {
  readonly connected = signal(false);
  readonly isConnected = this.connected.asReadonly();
  readonly signals = new Subject<void>();

  refetch(): Observable<void> {
    return this.signals.asObservable();
  }
}

function table(overrides: Partial<TableGridEntry> & { id: string; name: string }): TableGridEntry {
  return {
    area: null,
    displayOrder: 0,
    status: 'FREE',
    ticketId: null,
    ticketOpenedAt: null,
    ...overrides,
  };
}

const counter = (id: string, type: 'COUNTER' | 'TABLE') => ({
  id,
  number: 7,
  type,
  counterName: type === 'COUNTER' ? 'Ana' : null,
  tableId: type === 'TABLE' ? 't2' : null,
  tableName: type === 'TABLE' ? 'Mesa 2' : null,
  billRequested: false,
  openedAt: '2026-10-13T10:00:00Z',
  total: { amount: '10.00', currency: 'PEN' },
});

describe('TableGridStore', () => {
  let realtime: FakeRealtime;
  let http: HttpTestingController;
  let messages: MessageService;
  let screen: EnvironmentInjector;
  let store: TableGridStore;
  let closed = false;

  beforeEach(() => {
    realtime = new FakeRealtime();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        MessageService,
        TicketsApi,
        { provide: RealtimeClient, useValue: realtime },
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    messages = TestBed.inject(MessageService);
    closed = false;
    screen = createEnvironmentInjector([TableGridStore], TestBed.inject(EnvironmentInjector));
    store = screen.get(TableGridStore);
  });

  afterEach(() => {
    if (!closed) {
      screen.destroy();
    }
    http.verify();
    vi.useRealTimers();
  });

  function answer(tables: TableGridEntry[] = [], date?: string) {
    http
      .expectOne(TABLES)
      .flush(tables, date ? { headers: { Date: date } } : undefined);
    http.expectOne(TICKET_PAGE).flush(emptyPage);
  }

  function connect(tables: TableGridEntry[] = []) {
    realtime.connected.set(true);
    TestBed.tick();
    realtime.signals.next();
    answer(tables);
  }

  it('reads the tables and the open tickets when the screen opens', () => {
    answer([table({ id: 't1', name: 'Mesa 1' })]);

    expect(store.loaded()).toBe(true);
    expect(store.tiles().map((tile) => tile.name)).toEqual(['Mesa 1']);
  });

  it('keeps the actions off until a read made on the live connection has been applied', () => {
    answer();
    expect(store.enabled()).toBe(false);

    connect();

    expect(store.enabled()).toBe(true);
  });

  it('turns the actions off again when the connection drops', () => {
    answer();
    connect();

    realtime.connected.set(false);
    TestBed.tick();

    expect(store.enabled()).toBe(false);
  });

  it('shows only counter tickets in the counters list', () => {
    http.expectOne(TABLES).flush([]);
    http.expectOne(TICKET_PAGE).flush({
      ...emptyPage,
      content: [counter('k1', 'COUNTER'), counter('k2', 'TABLE')],
    });

    expect(store.counters().map((ticket) => ticket.id)).toEqual(['k1']);
  });

  it('reads again on every tables signal', () => {
    answer();

    realtime.signals.next();

    answer([table({ id: 't9', name: 'Mesa 9' })]);
    expect(store.tiles().map((tile) => tile.id)).toEqual(['t9']);
  });

  it('applies only the latest tables read when an older one answers late', () => {
    const first = http.expectOne(TABLES);
    http.expectOne(TICKET_PAGE).flush(emptyPage);
    realtime.signals.next();
    const second = http.expectOne(TABLES);
    http.expectOne(TICKET_PAGE).flush(emptyPage);

    second.flush([table({ id: 'new', name: 'Nueva' })]);
    first.flush([table({ id: 'old', name: 'Vieja' })]);

    expect(store.tiles().map((tile) => tile.id)).toEqual(['new']);
  });

  it('counts the minutes a table has been open against the server clock', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-13T12:00:00Z'));
    answer(
      [table({ id: 't1', name: 'Mesa 1', status: 'OCCUPIED', ticketId: 'k', ticketOpenedAt: '2026-10-13T11:30:00Z' })],
      new Date('2026-10-13T12:00:00Z').toUTCString(),
    );

    expect(store.tiles()[0].minutes).toBe(30);
  });

  it('gives a free table no minutes', () => {
    answer([table({ id: 't1', name: 'Mesa 1' })]);

    expect(store.tiles()[0].minutes).toBeNull();
  });

  it('reports a failed read, disables the actions and recovers on the next good read', () => {
    const added = vi.spyOn(messages, 'add');
    http.expectOne(TABLES).flush(problem(500, 'common.internal-error'), { status: 500, statusText: 'Error' });
    http.expectOne(TICKET_PAGE).flush(emptyPage);

    expect(store.failed()).toBe(true);
    expect(store.enabled()).toBe(false);
    expect(added).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));

    store.load();
    answer([table({ id: 't1', name: 'Mesa 1' })]);
    expect(store.failed()).toBe(false);
  });

  describe('opening a ticket', () => {
    const opened = { id: 'k1' } as Ticket;

    beforeEach(() => answer());

    it('opens a table with an idempotency key and hands the ticket over', () => {
      const done = vi.fn();

      store.openTable('t1', done);
      expect(store.pending.has('t1')).toBe(true);
      const request = http.expectOne(TICKETS);
      expect(request.request.body).toEqual({ tableId: 't1' });
      expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
      request.flush(opened, { status: 201, statusText: 'Created' });

      expect(done).toHaveBeenCalledWith(opened);
      expect(store.pending.has('t1')).toBe(false);
    });

    it('opens a counter ticket under the trimmed name', () => {
      store.openCounter('  Ana ', vi.fn());

      const request = http.expectOne(TICKETS);
      expect(request.request.body).toEqual({ counterName: 'Ana' });
      request.flush(opened, { status: 201, statusText: 'Created' });
    });

    it('retries a failed open with the same key, reads the grid again and uses a new key after success', () => {
      store.openTable('t1', vi.fn());
      const first = http.expectOne(TICKETS);
      const key = first.request.headers.get('Idempotency-Key');
      first.flush(problem(409, 'instore.table-occupied'), { status: 409, statusText: 'Conflict' });
      answer();

      store.openTable('t1', vi.fn());
      const second = http.expectOne(TICKETS);
      expect(second.request.headers.get('Idempotency-Key')).toBe(key);
      second.flush(opened, { status: 201, statusText: 'Created' });

      store.openTable('t1', vi.fn());
      const third = http.expectOne(TICKETS);
      expect(third.request.headers.get('Idempotency-Key')).not.toBe(key);
      third.flush(opened, { status: 201, statusText: 'Created' });
    });
  });

  it('stops listening to the realtime signal and the clock when the screen is destroyed', () => {
    answer();

    closed = true;
    screen.destroy();
    realtime.signals.next();

    http.expectNone(TABLES);
  });
});
