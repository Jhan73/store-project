import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { createEnvironmentInjector, EnvironmentInjector, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { Observable, Subject } from 'rxjs';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { BoardAllDay, BoardOrder, StationOption } from '../../../core/api/api-types';
import { AuthStore } from '../../../core/auth/auth-store';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { RealtimeClient } from '../../../core/realtime/realtime-client';
import { BoardApi } from './board-api';
import { emptyAllDay, marker, minutesAgo, order } from '../../../testing/board-fixtures';
import { BoardSound } from './board-sound';
import { BoardStore } from './board-store';

const BASE = 'http://api.test/api/v1';
const TICKETS = `${BASE}/board/tickets`;
const READY = `${BASE}/board/ready`;
const ALL_DAY = `${BASE}/board/all-day`;
const THRESHOLDS = `${BASE}/store/board-thresholds`;
const STATIONS = `${BASE}/stations`;
const STATION_KEY = 'board.station';
const bar = { id: 's1', name: 'Bar' };
const kitchen = { id: 's2', name: 'Cocina' };

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
  let screen: EnvironmentInjector;
  let screenClosed: boolean;
  let sound: { play: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    realtime = new FakeRealtime();
    screenClosed = false;
    sound = { play: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        MessageService,
        BoardApi,
        { provide: RealtimeClient, useValue: realtime },
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    vi.useRealTimers();
    closeScreen();
    http.verify();
    vi.restoreAllMocks();
    localStorage.removeItem(STATION_KEY);
  });

  // The screen provides the store, so it is destroyed on its own while the root services live on.
  function build(): BoardStore {
    screen = createEnvironmentInjector(
      [BoardStore, { provide: BoardSound, useValue: sound }],
      TestBed.inject(EnvironmentInjector),
    );
    return screen.get(BoardStore);
  }

  function closeScreen() {
    if (!screenClosed) {
      screenClosed = true;
      screen.destroy();
    }
  }

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

  function create(stations: StationOption[] = [bar, kitchen]) {
    store = build();
    TestBed.tick();
    http.expectOne(THRESHOLDS).flush({ boardWarningMinutes: 5, boardLateMinutes: 10, timeZone: 'America/Lima' });
    http.expectOne(STATIONS).flush(stations);
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
    store = build();
    TestBed.tick();
    http.expectOne(THRESHOLDS).flush({ boardWarningMinutes: 1, boardLateMinutes: 2, timeZone: 'America/Lima' });
    http.expectOne(STATIONS).flush([]);
    answer([order({ id: 'a', sentAt: minutesAgo(3) })]);

    expect(store.cards()[0].level).toBe('late');
    realtime.signals.next();
    answer();
    http.expectNone(THRESHOLDS);
  });

  it('falls back to 5 and 10 minutes when the thresholds cannot be read', () => {
    store = build();
    TestBed.tick();
    http.expectOne(THRESHOLDS).flush(serverError, failure);
    http.expectOne(STATIONS).flush([]);
    answer([order({ id: 'a', sentAt: minutesAgo(6) })]);

    expect(store.cards()[0].level).toBe('warning');
  });

  it('takes the store time zone from the thresholds read', () => {
    create();
    answer();

    expect(store.timeZone()).toBe('America/Lima');
  });

  it('falls back to the device time zone until the thresholds arrive and when they cannot be read', () => {
    const device = Intl.DateTimeFormat().resolvedOptions().timeZone;
    store = build();
    TestBed.tick();
    expect(store.timeZone()).toBe(device);

    http.expectOne(THRESHOLDS).flush(serverError, failure);
    http.expectOne(STATIONS).flush([]);
    answer();

    expect(store.timeZone()).toBe(device);
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

  it('keeps the last data, the actions and the silence when a background re-read fails, then retries', () => {
    vi.useFakeTimers();
    create();
    answer();
    connect([order({ id: 'kept' })]);
    const toasts = vi.spyOn(TestBed.inject(MessageService), 'add');

    vi.advanceTimersByTime(60_000);
    http.expectOne(TICKETS).flush(serverError, failure);
    http.match(READY);
    http.match(ALL_DAY);

    expect(store.failed()).toBe(false);
    expect(store.enabled()).toBe(true);
    expect(toasts).not.toHaveBeenCalled();
    expect(store.cards().map((card) => card.order.id)).toEqual(['kept']);

    vi.advanceTimersByTime(60_000);
    answer([order({ id: 'fresh' })]);
    expect(store.cards().map((card) => card.order.id)).toEqual(['fresh']);
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

    closeScreen();
    vi.advanceTimersByTime(120_000);
    realtime.signals.next();

    http.expectNone(TICKETS);
  });

  describe('station filter', () => {
    const answerFor = (station: string | null, tickets: BoardOrder[] = []) => {
      const query = station === null ? '' : `?station=${station}`;
      http.expectOne(`${TICKETS}${query}`).flush(tickets);
      http.expectOne(`${READY}${query}`).flush([]);
      http.expectOne(`${ALL_DAY}${query}`).flush(emptyAllDay);
    };

    it('offers the stations the API lists and starts on all of them', () => {
      create();
      answer();

      expect(store.stations()).toEqual([bar, kitchen]);
      expect(store.station()).toBeNull();
    });

    it('reads every view for the chosen station and remembers the choice on this device', () => {
      create();
      answer();

      store.setStation('s1');

      answerFor('s1', [order({ id: 'bar-order' })]);
      expect(store.station()).toBe('s1');
      expect(store.cards().map((card) => card.order.id)).toEqual(['bar-order']);
      expect(localStorage.getItem(STATION_KEY)).toBe('s1');
    });

    it('goes back to all stations and forgets the choice', () => {
      create();
      answer();
      store.setStation('s1');
      answerFor('s1');

      store.setStation(null);

      answerFor(null);
      expect(store.station()).toBeNull();
      expect(localStorage.getItem(STATION_KEY)).toBeNull();
    });

    it('keeps the filter on every later read', () => {
      create();
      answer();
      store.setStation('s2');
      answerFor('s2');

      realtime.signals.next();

      answerFor('s2');
    });

    it('applies only the read of the station chosen last', () => {
      create();
      answer();
      store.setStation('s1');
      store.setStation('s2');

      answerFor('s2', [order({ id: 'kitchen-order' })]);
      answerFor('s1', [order({ id: 'bar-order' })]);

      expect(store.cards().map((card) => card.order.id)).toEqual(['kitchen-order']);
    });

    it('starts on the remembered station', () => {
      localStorage.setItem(STATION_KEY, 's2');
      create();

      answerFor('s2', [order({ id: 'kitchen-order' })]);

      expect(store.station()).toBe('s2');
    });

    it('falls back to all stations when the remembered one is not in the list any more', () => {
      localStorage.setItem(STATION_KEY, 'gone');
      create();

      answerFor('gone', [order({ id: 'stale' })]);
      answerFor(null, [order({ id: 'all' })]);

      expect(store.station()).toBeNull();
      expect(store.cards().map((card) => card.order.id)).toEqual(['all']);
      expect(localStorage.getItem(STATION_KEY)).toBeNull();
    });

    it('falls back to all stations when the stations cannot be read', () => {
      localStorage.setItem(STATION_KEY, 's1');
      store = build();
      TestBed.tick();
      http.expectOne(THRESHOLDS).flush({ boardWarningMinutes: 5, boardLateMinutes: 10, timeZone: 'America/Lima' });
      http.expectOne(STATIONS).flush(serverError, failure);

      answerFor('s1');
      answerFor(null, [order({ id: 'all' })]);

      expect(store.station()).toBeNull();
      expect(store.stations()).toEqual([]);
      expect(store.cards().map((card) => card.order.id)).toEqual(['all']);
    });

    it('keeps the remembered station saved when the stations read failed, since the failure may be passing', () => {
      localStorage.setItem(STATION_KEY, 's1');
      store = build();
      TestBed.tick();
      http.expectOne(THRESHOLDS).flush({ boardWarningMinutes: 5, boardLateMinutes: 10, timeZone: 'America/Lima' });
      http.expectOne(STATIONS).flush(serverError, failure);
      answerFor('s1');
      answerFor(null);

      expect(store.station()).toBeNull();
      expect(localStorage.getItem(STATION_KEY)).toBe('s1');
    });

    describe('marker sounds', () => {
      const withMarker = (id: string) =>
        order({ id: `o-${id}`, markers: [marker({ id, boardOrderId: `o-${id}`, kind: 'NEW', acknowledgedAt: null })] });

      it('stays quiet for markers that only came into scope when the station changed, then sounds a new one', () => {
        create();
        answer([withMarker('m1')]);

        store.setStation('s1');
        answerFor('s1', [withMarker('m1'), withMarker('m2')]);
        expect(sound.play).not.toHaveBeenCalled();

        realtime.signals.next();
        answerFor('s1', [withMarker('m1'), withMarker('m2'), withMarker('m3')]);
        expect(sound.play).toHaveBeenCalledTimes(1);
        expect(sound.play).toHaveBeenCalledWith(['NEW']);
      });

      it('stays quiet when going back to all stations', () => {
        create();
        answer();
        store.setStation('s1');
        answerFor('s1', [withMarker('m1')]);

        store.setStation(null);
        answerFor(null, [withMarker('m1'), withMarker('m2')]);

        expect(sound.play).not.toHaveBeenCalled();
      });

      it('does not take a read made with a station that is gone as the baseline', () => {
        localStorage.setItem(STATION_KEY, 'gone');
        store = build();
        TestBed.tick();
        http.expectOne(THRESHOLDS).flush({ boardWarningMinutes: 5, boardLateMinutes: 10, timeZone: 'America/Lima' });
        answerFor('gone', [withMarker('m1')]);

        http.expectOne(STATIONS).flush([bar, kitchen]);
        answerFor(null, [withMarker('m1'), withMarker('m2')]);

        expect(sound.play).not.toHaveBeenCalled();
      });
    });

    it('works without storage', () => {
      vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
        throw new Error('blocked');
      });
      create();
      answer();

      store.setStation('s1');

      answerFor('s1');
      expect(store.station()).toBe('s1');
    });
  });

  describe('markers and sound', () => {
    const withMarker = (id: string, kind: 'NEW' | 'MODIFIED' | 'VOID', acknowledgedAt: string | null = null) =>
      order({ id: `o-${id}`, markers: [marker({ id, boardOrderId: `o-${id}`, kind, acknowledgedAt })] });

    it('stays quiet on the first read: what was already there is not news', () => {
      create();
      answer([withMarker('m1', 'NEW')]);

      expect(sound.play).not.toHaveBeenCalled();
    });

    it('sounds the kind of a marker that arrives after the first read, and only once', () => {
      create();
      answer([withMarker('m1', 'NEW')]);

      realtime.signals.next();
      answer([withMarker('m1', 'NEW'), withMarker('m2', 'VOID')]);
      expect(sound.play).toHaveBeenCalledTimes(1);
      expect(sound.play).toHaveBeenCalledWith(['VOID']);

      realtime.signals.next();
      answer([withMarker('m1', 'NEW'), withMarker('m2', 'VOID')]);
      expect(sound.play).toHaveBeenCalledTimes(1);
    });

    it('sounds each kind that arrived together, most urgent first', () => {
      create();
      answer();

      realtime.signals.next();
      answer([withMarker('m1', 'NEW'), withMarker('m2', 'MODIFIED'), withMarker('m3', 'NEW')]);

      expect(sound.play).toHaveBeenCalledWith(['MODIFIED', 'NEW']);
    });

    it('does not sound for a marker that was acknowledged', () => {
      create();
      answer();

      realtime.signals.next();
      answer([withMarker('m1', 'NEW', '2026-10-13T10:00:00Z')]);

      expect(sound.play).not.toHaveBeenCalled();
    });

    it('hears the markers of a ready card too, such as a line added after it was ready', () => {
      create();
      answer();

      realtime.signals.next();
      answer([], [{ ...withMarker('m1', 'MODIFIED'), status: 'READY', readyAt: minutesAgo(1) }]);

      expect(sound.play).toHaveBeenCalledWith(['MODIFIED']);
    });

    it('does not sound a marker again after a reconnection re-read', () => {
      create();
      answer([withMarker('m1', 'NEW')]);
      connect([withMarker('m1', 'NEW')]);
      realtime.connected.set(false);
      TestBed.tick();
      realtime.connected.set(true);
      TestBed.tick();

      realtime.signals.next();
      answer([withMarker('m1', 'NEW')]);

      expect(sound.play).not.toHaveBeenCalled();
    });

    it('does not take a failed read for a read without markers', () => {
      create();
      answer([withMarker('m1', 'NEW')]);
      realtime.signals.next();
      http.expectOne(TICKETS).flush(serverError, failure);
      http.match(READY);
      http.match(ALL_DAY);

      realtime.signals.next();
      answer([withMarker('m1', 'NEW')]);

      expect(sound.play).not.toHaveBeenCalled();
    });
  });

  describe('commands', () => {
    const ready = () => {
      create();
      answer();
      connect([order({ id: 'o1', status: 'PREPARING' })]);
    };
    const reload = () => answer();

    it('sends Ready for a whole order only after the 5 second undo window', () => {
      vi.useFakeTimers();
      ready();

      store.readyOrder('o1');
      expect(store.isQueued('o1')).toBe(true);
      expect(store.queuedIds()).toEqual(new Set(['o1']));
      vi.advanceTimersByTime(4_999);
      http.expectNone(`${BASE}/board/orders/o1/ready`);

      vi.advanceTimersByTime(1);
      const request = http.expectOne(`${BASE}/board/orders/o1/ready`);
      expect(request.request.method).toBe('POST');
      expect(store.isQueued('o1')).toBe(false);
      request.flush(order({ id: 'o1', status: 'READY' }));
      reload();
    });

    it('sends Ready for a line after the undo window', () => {
      vi.useFakeTimers();
      ready();

      store.readyLine('l1');
      vi.advanceTimersByTime(5_000);

      http.expectOne(`${BASE}/board/lines/l1/ready`).flush(order({ id: 'o1' }));
      reload();
    });

    it('never sends a Ready that was undone', () => {
      vi.useFakeTimers();
      ready();

      store.readyOrder('o1');
      store.undo('o1');
      vi.advanceTimersByTime(10_000);

      http.expectNone(`${BASE}/board/orders/o1/ready`);
    });

    it('sends the pending Ready at once when the page is hidden, so it outlives the page', () => {
      vi.useFakeTimers();
      ready();
      store.readyOrder('o1');
      store.readyLine('l1');

      window.dispatchEvent(new Event('pagehide'));

      const orderRequest = http.expectOne(`${BASE}/board/orders/o1/ready`);
      const lineRequest = http.expectOne(`${BASE}/board/lines/l1/ready`);
      expect(orderRequest.request.keepalive).toBe(true);
      expect(lineRequest.request.keepalive).toBe(true);
      orderRequest.flush(order({ id: 'o1' }));
      lineRequest.flush(order({ id: 'o1' }));
      http.match(TICKETS);
      http.match(READY);
      http.match(ALL_DAY);
    });

    it('sends the pending Ready before the session is signed out and waits for its answer', async () => {
      vi.useFakeTimers();
      ready();
      const auth = TestBed.inject(AuthStore);
      const login = auth.login({ email: 'a@b.pe', password: 'x' });
      http
        .expectOne(`${BASE}/auth/login`)
        .flush({ accessToken: 'jwt', tokenType: 'Bearer', userId: 'u', role: 'CASHIER' });
      await login;
      store.readyOrder('o1');

      const out = auth.logout();
      const request = http.expectOne(`${BASE}/board/orders/o1/ready`);
      http.expectNone(`${BASE}/auth/logout`);
      request.flush(order({ id: 'o1', status: 'READY' }));
      reload();
      await vi.waitFor(() =>
        http.expectOne(`${BASE}/auth/logout`).flush(null, { status: 204, statusText: 'No Content' }),
      );

      expect(await out).toBe(true);
    });

    it('sends the pending Ready when the screen is destroyed and does not cancel it', () => {
      vi.useFakeTimers();
      ready();
      store.readyOrder('o1');

      closeScreen();

      const request = http.expectOne(`${BASE}/board/orders/o1/ready`);
      expect(request.cancelled).toBe(false);
      request.flush(order({ id: 'o1' }));
      http.expectNone(TICKETS);
    });

    it('stops listening to the page when it is destroyed', () => {
      vi.useFakeTimers();
      ready();
      store.readyOrder('o1');
      closeScreen();
      http.expectOne(`${BASE}/board/orders/o1/ready`);

      window.dispatchEvent(new Event('pagehide'));

      http.expectNone(`${BASE}/board/orders/o1/ready`);
    });

    it('reads the board again after a command and blocks the card while it is in flight', () => {
      ready();

      store.start('o1');
      expect(store.pending.has('o1')).toBe(true);
      http.expectOne(`${BASE}/board/orders/o1/start`).flush(order({ id: 'o1', status: 'PREPARING' }));
      expect(store.pending.has('o1')).toBe(false);

      answer([order({ id: 'o1', status: 'PREPARING' })]);
      expect(store.cards()[0].order.status).toBe('PREPARING');
    });

    it.each([
      ['start', 'o1', 'orders/o1/start'],
      ['handover', 'o1', 'orders/o1/handover'],
      ['recall', 'o1', 'orders/o1/recall'],
      ['flag', 'l1', 'lines/l1/flag'],
      ['acknowledge', 'm1', 'markers/m1/ack'],
    ] as const)('sends %s at once, with no undo window', (action, id, path) => {
      ready();

      store[action](id);

      http.expectOne(`${BASE}/board/${path}`).flush({});
      reload();
    });

    it('does not send a second command for an item that is in flight', () => {
      ready();

      store.start('o1');
      store.start('o1');

      expect(http.match(`${BASE}/board/orders/o1/start`).length).toBe(1);
    });

    it.each(['start', 'handover', 'recall', 'flag', 'acknowledge', 'readyOrder', 'readyLine'] as const)(
      'ignores %s while the actions are off',
      (action) => {
        create();
        answer();

        store[action]('x');

        http.expectNone((request) => request.method === 'POST');
        expect(store.isQueued('x')).toBe(false);
      },
    );

    it('shows the error and reads the board again when a command is refused', () => {
      ready();
      const toasts = vi.spyOn(TestBed.inject(MessageService), 'add');

      store.handover('o1');
      http
        .expectOne(`${BASE}/board/orders/o1/handover`)
        .flush(
          { ...serverError, status: 409, code: 'preparation.order-not-ready' },
          { status: 409, statusText: 'Conflict' },
        );

      expect(toasts).toHaveBeenCalledTimes(1);
      expect(store.pending.has('o1')).toBe(false);
      reload();
    });
  });
});
