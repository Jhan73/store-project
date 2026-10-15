import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { Observable, Subject } from 'rxjs';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { BoardAllDay, BoardOrder } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { RealtimeClient } from '../../../core/realtime/realtime-client';
import { BoardApi } from './board-api';
import { emptyAllDay, minutesAgo, order } from './board-fixtures';
import { BoardStore } from './board-store';

const BASE = 'http://api.test/api/v1';
const TICKETS = `${BASE}/board/tickets`;
const READY = `${BASE}/board/ready`;
const ALL_DAY = `${BASE}/board/all-day`;
const THRESHOLDS = `${BASE}/store/board-thresholds`;

class FakeRealtime {
  readonly connected = signal(false);
  readonly isConnected = this.connected.asReadonly();
  readonly signals = new Subject<void>();
  readonly destinations: string[] = [];

  refetch(destination: string): Observable<void> {
    this.destinations.push(destination);
    return this.signals.asObservable();
  }
}

const serverError = { type: 'about:blank', status: 500, code: 'common.internal-error', correlationId: 'c' };
const failure = { status: 500, statusText: 'Server Error' };

describe('BoardStore', () => {
  let realtime: FakeRealtime;
  let http: HttpTestingController;
  let store: BoardStore;

  beforeEach(() => {
    realtime = new FakeRealtime();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        MessageService,
        BoardApi,
        BoardStore,
        { provide: RealtimeClient, useValue: realtime },
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    http.verify();
  });

  function answer(
    tickets: BoardOrder[] = [],
    ready: BoardOrder[] = [],
    allDay: BoardAllDay = emptyAllDay,
    headers: Record<string, string> = {},
  ) {
    http.expectOne(TICKETS).flush(tickets, { headers });
    http.expectOne(READY).flush(ready);
    http.expectOne(ALL_DAY).flush(allDay);
  }

  function create() {
    store = TestBed.inject(BoardStore);
    TestBed.tick();
    http.expectOne(THRESHOLDS).flush({ boardWarningMinutes: 5, boardLateMinutes: 10 });
  }

  function connect(tickets: BoardOrder[] = [], ready: BoardOrder[] = [], allDay = emptyAllDay) {
    realtime.connected.set(true);
    TestBed.tick();
    realtime.signals.next();
    answer(tickets, ready, allDay);
  }

  it('listens to the board topic and reads the three views', () => {
    create();
    answer([order({ id: 'a' })], [order({ id: 'b', status: 'READY', readyAt: minutesAgo(2) })]);

    expect(realtime.destinations).toEqual(['/topic/board']);
    expect(store.cards().map((card) => card.order.id)).toEqual(['a']);
    expect(store.readyCards().map((card) => card.order.id)).toEqual(['b']);
    expect(store.loaded()).toBe(true);
  });

  it('orders the cards oldest first and counts a scheduled card from its fire time', () => {
    create();
    answer([
      order({ id: 'newer', sentAt: minutesAgo(3) }),
      order({ id: 'scheduled', sentAt: minutesAgo(90), fireAt: minutesAgo(8) }),
      order({ id: 'older', sentAt: minutesAgo(20) }),
    ]);

    expect(store.cards().map((card) => card.order.id)).toEqual(['older', 'scheduled', 'newer']);
    expect(store.cards().map((card) => card.minutes)).toEqual([20, 8, 3]);
  });

  it('gives each card the level of its elapsed minutes', () => {
    create();
    answer([
      order({ id: 'a', sentAt: minutesAgo(12) }),
      order({ id: 'b', sentAt: minutesAgo(6) }),
      order({ id: 'c', sentAt: minutesAgo(2) }),
    ]);

    expect(store.cards().map((card) => card.level)).toEqual(['late', 'warning', 'normal']);
  });

  it('measures a ready card from the moment it became ready and never colors it', () => {
    create();
    answer([], [order({ id: 'r', status: 'READY', sentAt: minutesAgo(60), readyAt: minutesAgo(4) })]);

    expect(store.readyCards()[0].minutes).toBe(4);
    expect(store.readyCards()[0].level).toBe('normal');
  });

  it('reads the thresholds once and uses them for the levels', () => {
    store = TestBed.inject(BoardStore);
    TestBed.tick();
    http.expectOne(THRESHOLDS).flush({ boardWarningMinutes: 1, boardLateMinutes: 2 });
    answer([order({ id: 'a', sentAt: minutesAgo(3) })]);

    expect(store.cards()[0].level).toBe('late');
    realtime.signals.next();
    answer();
    http.expectNone(THRESHOLDS);
  });

  it('falls back to 5 and 10 minutes when the thresholds cannot be read', () => {
    store = TestBed.inject(BoardStore);
    TestBed.tick();
    http.expectOne(THRESHOLDS).flush(serverError, failure);
    answer([order({ id: 'a', sentAt: minutesAgo(6) })]);

    expect(store.cards()[0].level).toBe('warning');
  });

  it('corrects the elapsed time with the offset of the server clock', () => {
    vi.useFakeTimers({ now: new Date('2026-10-13T10:00:00Z') });
    create();
    answer([order({ id: 'a', sentAt: '2026-10-13T09:50:00Z' })], [], emptyAllDay, {
      Date: 'Tue, 13 Oct 2026 10:03:00 GMT',
    });

    expect(store.cards()[0].minutes).toBe(13);
  });

  it('moves the elapsed minutes forward with the clock', () => {
    vi.useFakeTimers({ now: new Date('2026-10-13T10:00:00Z') });
    create();
    answer([order({ id: 'a', sentAt: '2026-10-13T09:58:00Z' })]);
    expect(store.cards()[0].minutes).toBe(2);

    vi.advanceTimersByTime(30_000);

    expect(store.cards()[0].minutes).toBe(2);
    vi.advanceTimersByTime(30_000);
    expect(store.cards()[0].minutes).toBe(3);
    // The periodic refresh fires at the same moment; its answer is not under test here.
    http.match(TICKETS);
    http.match(READY);
    http.match(ALL_DAY);
  });

  it('keeps the all-day view of the latest read', () => {
    create();
    const groups = [{ productId: 'p1', displayName: 'Naranja', modifiersText: null, quantity: 6 }];
    answer([], [], { groups, individual: [] });

    expect(store.allDay().groups).toEqual(groups);
  });

  it('switches between the three views', () => {
    create();
    answer();
    expect(store.view()).toBe('tickets');

    store.setView('allDay');
    expect(store.view()).toBe('allDay');
    store.setView('ready');
    expect(store.view()).toBe('ready');
  });

  it('applies only the latest of overlapping reads', () => {
    create();
    answer();
    realtime.signals.next();
    realtime.signals.next();
    const [firstTickets, secondTickets] = http.match(TICKETS);
    const [firstReady, secondReady] = http.match(READY);
    const [firstAllDay, secondAllDay] = http.match(ALL_DAY);
    secondTickets.flush([order({ id: 'second' })]);
    secondReady.flush([]);
    secondAllDay.flush(emptyAllDay);
    firstTickets.flush([order({ id: 'first' })]);
    firstReady.flush([]);
    firstAllDay.flush(emptyAllDay);

    expect(store.cards().map((card) => card.order.id)).toEqual(['second']);
  });

  it('keeps the actions off until a read that began on the live connection has been applied', () => {
    create();
    answer();
    expect(store.enabled()).toBe(false);

    connect();
    expect(store.enabled()).toBe(true);

    realtime.connected.set(false);
    TestBed.tick();
    expect(store.enabled()).toBe(false);

    realtime.connected.set(true);
    TestBed.tick();
    expect(store.enabled()).toBe(false);

    realtime.signals.next();
    answer();
    expect(store.enabled()).toBe(true);
  });

  it('does not enable the actions on a read that started before the connection came back', () => {
    create();
    answer();
    connect();
    realtime.connected.set(false);
    TestBed.tick();

    realtime.signals.next();
    const early = [http.expectOne(TICKETS), http.expectOne(READY), http.expectOne(ALL_DAY)];
    realtime.connected.set(true);
    TestBed.tick();
    early[0].flush([]);
    early[1].flush([]);
    early[2].flush(emptyAllDay);

    expect(store.enabled()).toBe(false);
  });

  it('disables the actions and flags the failure when a read fails, then recovers', () => {
    create();
    answer();
    connect();

    realtime.signals.next();
    http.expectOne(TICKETS).flush(serverError, failure);
    http.match(READY);
    http.match(ALL_DAY);

    expect(store.failed()).toBe(true);
    expect(store.enabled()).toBe(false);

    store.load();
    answer();
    expect(store.failed()).toBe(false);
    expect(store.enabled()).toBe(true);
  });

  it('re-reads every minute while connected so a scheduled card appears when it fires', () => {
    vi.useFakeTimers();
    create();
    answer();
    connect();

    vi.advanceTimersByTime(60_000);

    answer([order({ id: 'fired' })]);
    expect(store.cards().map((card) => card.order.id)).toEqual(['fired']);
  });

  it('does not poll while disconnected', () => {
    vi.useFakeTimers();
    create();
    answer();

    vi.advanceTimersByTime(60_000);

    http.expectNone(TICKETS);
  });

  it('stops listening and polling when it is destroyed', () => {
    vi.useFakeTimers();
    create();
    answer();
    connect();

    TestBed.resetTestingModule();
    vi.advanceTimersByTime(120_000);
    realtime.signals.next();

    http.expectNone(TICKETS);
  });
});
