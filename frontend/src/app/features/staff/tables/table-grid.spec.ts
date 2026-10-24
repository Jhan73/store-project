import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ChangeDetectionStrategy, Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { MessageService } from 'primeng/api';
import { Observable, Subject } from 'rxjs';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { TableGridEntry, Ticket, TicketSummary } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { RealtimeClient } from '../../../core/realtime/realtime-client';
import { TicketsApi } from '../tickets/tickets-api';
import { TableGrid } from './table-grid';

const BASE = 'http://api.test/api/v1';
const TABLES = `${BASE}/tables`;
const TICKETS = `${BASE}/tickets`;
const TICKET_PAGE = `${BASE}/tickets?page=0&size=100`;

const pageOf = (content: TicketSummary[]) => ({
  content,
  page: 0,
  size: 100,
  totalElements: content.length,
  totalPages: 1,
});

@Component({ template: '', changeDetection: ChangeDetectionStrategy.OnPush })
class Blank {}

const minutesAgo = (minutes: number) => new Date(Date.now() - minutes * 60_000).toISOString();

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

const grid = () => [
  table({ id: 't1', name: 'Mesa 1', area: 'Terraza' }),
  table({ id: 't2', name: 'Mesa 2', status: 'OCCUPIED', ticketId: 'k2', ticketOpenedAt: minutesAgo(12) }),
  table({
    id: 't3',
    name: 'Mesa 3',
    status: 'WAITING_FOR_PAYMENT',
    ticketId: 'k3',
    ticketOpenedAt: minutesAgo(40),
  }),
];

const counterTicket = (id: string, name: string): TicketSummary => ({
  id,
  number: 7,
  type: 'COUNTER',
  counterName: name,
  tableId: null,
  tableName: null,
  billRequested: false,
  openedAt: minutesAgo(3),
  total: { amount: '10.00', currency: 'PEN' },
});

class FakeRealtime {
  readonly connected = signal(false);
  readonly isConnected = this.connected.asReadonly();
  readonly signals = new Subject<void>();

  refetch(): Observable<void> {
    return this.signals.asObservable();
  }
}

const failure = {
  type: 'about:blank',
  status: 409,
  code: 'instore.table-occupied',
  correlationId: 'c',
};

describe('TableGrid', () => {
  let realtime: FakeRealtime;
  let http: HttpTestingController;
  let messages: MessageService;

  beforeEach(() => {
    realtime = new FakeRealtime();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([{ path: 'staff/tickets/:id', component: Blank }]),
        MessageService,
        TicketsApi,
        { provide: RealtimeClient, useValue: realtime },
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    messages = TestBed.inject(MessageService);
  });

  afterEach(() => http.verify());

  function answerReads(tables: TableGridEntry[] = grid(), counters: TicketSummary[] = []) {
    http.expectOne(TABLES).flush(tables);
    http.expectOne(TICKET_PAGE).flush(pageOf(counters));
  }

  async function open(tables: TableGridEntry[] = grid(), counters: TicketSummary[] = []) {
    const fixture = TestBed.createComponent(TableGrid);
    await fixture.whenStable();
    answerReads(tables, counters);
    await fixture.whenStable();
    return fixture;
  }

  // Connecting emits the re-fetch signal; answering that read is what enables the actions.
  async function ready(tables: TableGridEntry[] = grid(), counters: TicketSummary[] = []) {
    const fixture = await open(tables, counters);
    realtime.connected.set(true);
    realtime.signals.next();
    answerReads(tables, counters);
    await fixture.whenStable();
    return fixture;
  }

  const text = (fixture: ComponentFixture<TableGrid>, testId: string) =>
    (fixture.nativeElement as HTMLElement).querySelector(`[data-testid="${testId}"]`)?.textContent ?? '';
  const element = <T extends HTMLElement>(fixture: ComponentFixture<TableGrid>, testId: string) =>
    (fixture.nativeElement as HTMLElement).querySelector<T>(`[data-testid="${testId}"]`)!;

  it('shows each table with its area, status and the time since its ticket opened', async () => {
    const fixture = await open();

    expect(text(fixture, 'table-t1')).toContain('Mesa 1');
    expect(text(fixture, 'table-t1')).toContain('Terraza');
    expect(text(fixture, 'table-t1')).toContain('Libre');
    expect(text(fixture, 'table-t2')).toContain('Ocupada');
    expect(text(fixture, 'table-t2')).toContain('12 min');
    expect(text(fixture, 'table-t3')).toContain('Esperando el pago');
    expect(text(fixture, 'table-t3')).toContain('40 min');
  });

  it('links an occupied table to its ticket', async () => {
    const fixture = await open();

    expect(element(fixture, 'table-t2').getAttribute('href')).toBe('/staff/tickets/k2');
    expect(element(fixture, 'table-t3').getAttribute('href')).toBe('/staff/tickets/k3');
  });

  it('keeps the actions off until the live connection has been read', async () => {
    const fixture = await open();

    expect(element<HTMLButtonElement>(fixture, 'table-t1').disabled).toBe(true);
    expect(text(fixture, 'offline-banner')).toContain('Sin conexión en tiempo real');

    realtime.connected.set(true);
    realtime.signals.next();
    answerReads();
    await fixture.whenStable();

    expect(element<HTMLButtonElement>(fixture, 'table-t1').disabled).toBe(false);
    expect(element(fixture, 'offline-banner')).toBeNull();
  });

  it('opens a ticket on a free table and goes to its editor', async () => {
    const fixture = await ready();
    const router = TestBed.inject(Router);

    element<HTMLButtonElement>(fixture, 'table-t1').click();
    await fixture.whenStable();
    const request = http.expectOne(TICKETS);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ tableId: 't1' });
    expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
    request.flush({ id: 'k9' } as Ticket, { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(router.url).toBe('/staff/tickets/k9');
  });

  it('retries an open with the same idempotency key and shows the error', async () => {
    const fixture = await ready();
    const added = vi.spyOn(messages, 'add');

    element<HTMLButtonElement>(fixture, 'table-t1').click();
    await fixture.whenStable();
    const first = http.expectOne(TICKETS);
    const key = first.request.headers.get('Idempotency-Key');
    first.flush(failure, { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();
    http.expectOne(TABLES).flush(grid());
    http.expectOne(TICKET_PAGE).flush(pageOf([]));
    await fixture.whenStable();

    expect(added).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));

    element<HTMLButtonElement>(fixture, 'table-t1').click();
    await fixture.whenStable();
    const second = http.expectOne(TICKETS);
    expect(second.request.headers.get('Idempotency-Key')).toBe(key);
    second.flush({ id: 'k9' } as Ticket, { status: 201, statusText: 'Created' });
  });

  it('lists the open counter tickets and links them to their editor', async () => {
    const fixture = await open(grid(), [counterTicket('k5', 'Ana')]);

    expect(text(fixture, 'counter-k5')).toContain('Ana');
    expect(element(fixture, 'counter-k5').getAttribute('href')).toBe('/staff/tickets/k5');
  });

  it('labels the counter name with the form field, which keeps the room of its message', async () => {
    const fixture = await open();
    const root = fixture.nativeElement as HTMLElement;

    expect(root.querySelector('app-form-field label[for="counter-name"]')).not.toBeNull();
    expect(root.querySelector('app-form-field .message')).not.toBeNull();
    expect(element(fixture, 'counter-name').closest('app-form-field')).not.toBeNull();
  });

  it('opens a counter ticket only with a customer name', async () => {
    const fixture = await ready();
    const router = TestBed.inject(Router);
    const submit = element<HTMLButtonElement>(fixture, 'open-counter');
    expect(submit.disabled).toBe(true);

    const name = element<HTMLInputElement>(fixture, 'counter-name');
    name.value = '  Ana ';
    name.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    expect(submit.disabled).toBe(false);
    submit.click();
    await fixture.whenStable();

    const request = http.expectOne(TICKETS);
    expect(request.request.body).toEqual({ counterName: 'Ana' });
    request.flush({ id: 'k8' } as Ticket, { status: 201, statusText: 'Created' });
    await fixture.whenStable();
    expect(router.url).toBe('/staff/tickets/k8');
  });

  it('reads again on every realtime signal', async () => {
    const fixture = await ready();

    realtime.signals.next();
    http.expectOne(TABLES).flush([table({ id: 't7', name: 'Mesa 7' })]);
    http.expectOne(TICKET_PAGE).flush(pageOf([]));
    await fixture.whenStable();

    expect(text(fixture, 'table-t7')).toContain('Mesa 7');
  });

  it('offers a retry when the tables cannot be read', async () => {
    const fixture = TestBed.createComponent(TableGrid);
    await fixture.whenStable();
    http.expectOne(TABLES).flush(failure, { status: 500, statusText: 'Server Error' });
    http.expectOne(TICKET_PAGE).flush(pageOf([]));
    await fixture.whenStable();

    expect(text(fixture, 'offline-banner')).toContain('No se pudieron cargar las mesas');

    element<HTMLButtonElement>(fixture, 'retry').click();
    await fixture.whenStable();
    answerReads();
    await fixture.whenStable();

    expect(text(fixture, 'table-t1')).toContain('Mesa 1');
  });
});
