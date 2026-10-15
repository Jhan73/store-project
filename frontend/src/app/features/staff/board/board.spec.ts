import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal, WritableSignal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { Observable, Subject } from 'rxjs';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { BoardAllDay, BoardOrder } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { RealtimeClient } from '../../../core/realtime/realtime-client';
import { ScreenWakeLock } from '../../../core/screen/screen-wake-lock';
import { emptyAllDay, line, marker, minutesAgo, order } from '../../../testing/board-fixtures';
import { BoardApi } from './board-api';
import { BoardSound } from './board-sound';
import { BoardStore } from './board-store';
import { Board } from './board';

const BASE = 'http://api.test/api/v1';
const TICKETS = `${BASE}/board/tickets`;
const READY = `${BASE}/board/ready`;
const ALL_DAY = `${BASE}/board/all-day`;
const THRESHOLDS = `${BASE}/store/board-thresholds`;

class FakeRealtime {
  readonly connected = signal(false);
  readonly isConnected = this.connected.asReadonly();
  readonly signals = new Subject<void>();

  refetch(): Observable<void> {
    return this.signals.asObservable();
  }
}

const serverError = { type: 'about:blank', status: 500, code: 'common.internal-error', correlationId: 'c' };
const failure = { status: 500, statusText: 'Server Error' };

describe('Board', () => {
  let wakeLock: { start: ReturnType<typeof vi.fn> };
  let sound: { enabled: WritableSignal<boolean>; unsupported: WritableSignal<boolean>; enable: ReturnType<typeof vi.fn>; play: ReturnType<typeof vi.fn> };
  let realtime: FakeRealtime;
  let http: HttpTestingController;

  beforeEach(() => {
    realtime = new FakeRealtime();
    const enabled = signal(false);
    sound = {
      enabled,
      unsupported: signal(false),
      enable: vi.fn(() => {
        enabled.set(true);
        return Promise.resolve();
      }),
      play: vi.fn(),
    };
    wakeLock = { start: vi.fn() };
    TestBed.overrideComponent(Board, {
      set: {
        providers: [
          BoardStore,
          { provide: BoardSound, useValue: sound },
          { provide: ScreenWakeLock, useValue: wakeLock },
        ],
      },
    });
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

  afterEach(() => http.verify());

  async function answer(
    fixture: ComponentFixture<Board>,
    tickets: BoardOrder[],
    ready: BoardOrder[] = [],
    allDay: BoardAllDay = emptyAllDay,
  ) {
    http.expectOne(TICKETS).flush(tickets);
    http.expectOne(READY).flush(ready);
    http.expectOne(ALL_DAY).flush(allDay);
    await fixture.whenStable();
  }

  async function open(tickets: BoardOrder[] = [], ready: BoardOrder[] = [], allDay = emptyAllDay) {
    const fixture = TestBed.createComponent(Board);
    await fixture.whenStable();
    http.expectOne(THRESHOLDS).flush({ boardWarningMinutes: 5, boardLateMinutes: 10 });
    await answer(fixture, tickets, ready, allDay);
    return fixture;
  }

  async function live(tickets: BoardOrder[] = [], ready: BoardOrder[] = [], allDay = emptyAllDay) {
    const fixture = await open(tickets, ready, allDay);
    realtime.connected.set(true);
    realtime.signals.next();
    await answer(fixture, tickets, ready, allDay);
    return fixture;
  }

  const host = (fixture: ComponentFixture<Board>) => fixture.nativeElement as HTMLElement;
  const query = (fixture: ComponentFixture<Board>, testId: string) =>
    host(fixture).querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`);
  const banner = (fixture: ComponentFixture<Board>) => query(fixture, 'offline-banner');
  const numbers = (fixture: ComponentFixture<Board>) =>
    Array.from(host(fixture).querySelectorAll('app-board-card .number')).map((node) => node.textContent?.trim());

  it('shows the cards to prepare, oldest first, with their numbers', async () => {
    const fixture = await live([
      order({ id: 'a', shortNumber: 'L-002', sentAt: minutesAgo(2) }),
      order({ id: 'b', shortNumber: 'W-001', sentAt: minutesAgo(9) }),
    ]);

    expect(numbers(fixture)).toEqual(['W-001', 'L-002']);
  });

  it('tells there is nothing to prepare once the first read is in', async () => {
    const fixture = await live();

    expect(host(fixture).textContent).toContain('No hay pedidos por preparar');
  });

  it('switches between the three views and keeps the choice visible', async () => {
    const fixture = await live(
      [order({ id: 'a', shortNumber: 'L-002' })],
      [order({ id: 'r', shortNumber: 'W-009', status: 'READY', readyAt: minutesAgo(1) })],
      { groups: [{ productId: 'p1', displayName: 'Naranja', modifiersText: '500 ml', quantity: 6 }], individual: [] },
    );
    expect(query(fixture, 'view-tickets')!.getAttribute('aria-pressed')).toBe('true');

    query(fixture, 'view-allDay')!.click();
    await fixture.whenStable();
    expect(host(fixture).querySelector('app-board-all-day')?.textContent?.replace(/\s+/g, ' ')).toContain(
      '6× Naranja (500 ml)',
    );
    expect(query(fixture, 'view-allDay')!.getAttribute('aria-pressed')).toBe('true');
    expect(query(fixture, 'view-tickets')!.getAttribute('aria-pressed')).toBe('false');

    query(fixture, 'view-ready')!.click();
    await fixture.whenStable();
    expect(numbers(fixture)).toEqual(['W-009']);
  });

  it('counts the cards on the tabs', async () => {
    const fixture = await live(
      [order({ id: 'a' }), order({ id: 'b' })],
      [order({ id: 'r', status: 'READY', readyAt: minutesAgo(1) })],
    );

    expect(query(fixture, 'view-tickets')!.textContent).toContain('2');
    expect(query(fixture, 'view-ready')!.textContent).toContain('1');
  });

  it('keeps the screen on while the board is open', async () => {
    await live();

    expect(wakeLock.start).toHaveBeenCalledTimes(1);
  });

  describe('sound', () => {
    it('warns while the sound is off and offers to enable it', async () => {
      const fixture = await live();

      expect(query(fixture, 'sound-warning')?.textContent).toContain('Sonido desactivado');
      expect(query(fixture, 'enable-sound')).not.toBeNull();
      expect(query(fixture, 'sound-on')).toBeNull();
    });

    it('enables the sound on the tap and drops the warning', async () => {
      const fixture = await live();

      query(fixture, 'enable-sound')!.click();
      await fixture.whenStable();

      expect(sound.enable).toHaveBeenCalledTimes(1);
      expect(query(fixture, 'sound-warning')).toBeNull();
      expect(query(fixture, 'sound-on')?.textContent).toContain('Sonido activado');
    });

    it('says so when the browser has no audio, without a button that cannot work', async () => {
      sound.unsupported.set(true);
      const fixture = await live();

      expect(query(fixture, 'sound-warning')?.textContent).toContain('no permite sonido');
      expect(query(fixture, 'enable-sound')).toBeNull();
    });
  });

  describe('disconnection', () => {
    it('shows a full-width banner and keeps every action off until the connection has re-read the board', async () => {
      const fixture = await open([order({ id: 'a' })]);

      expect(banner(fixture)?.textContent).toContain('Sin conexión');
      expect(query(fixture, 'start')!.disabled).toBe(true);

      realtime.connected.set(true);
      await fixture.whenStable();
      expect(banner(fixture)).not.toBeNull();
      expect(query(fixture, 'start')!.disabled).toBe(true);

      realtime.signals.next();
      await answer(fixture, [order({ id: 'a' })]);

      expect(banner(fixture)).toBeNull();
      expect(query(fixture, 'start')!.disabled).toBe(false);
    });

    it('goes back to blocked when the connection drops, and reopens only after the re-read', async () => {
      const fixture = await live([order({ id: 'a' })]);

      realtime.connected.set(false);
      await fixture.whenStable();
      expect(banner(fixture)?.textContent).toContain('Sin conexión');
      expect(query(fixture, 'start')!.disabled).toBe(true);

      realtime.connected.set(true);
      realtime.signals.next();
      await answer(fixture, [order({ id: 'a' })]);
      expect(banner(fixture)).toBeNull();
      expect(query(fixture, 'start')!.disabled).toBe(false);
    });

    it('offers a retry when a read fails and recovers on it', async () => {
      const fixture = await live([order({ id: 'a' })]);

      realtime.signals.next();
      http.expectOne(TICKETS).flush(serverError, failure);
      http.match(READY);
      http.match(ALL_DAY);
      await fixture.whenStable();

      expect(query(fixture, 'retry')).not.toBeNull();
      expect(query(fixture, 'start')!.disabled).toBe(true);

      query(fixture, 'retry')!.click();
      await answer(fixture, [order({ id: 'a' })]);
      expect(query(fixture, 'retry')).toBeNull();
      expect(query(fixture, 'start')!.disabled).toBe(false);
    });
  });

  describe('commands', () => {
    it('starts an order and shows its new state after the re-read', async () => {
      const fixture = await live([order({ id: 'a', status: 'WAITING' })]);

      query(fixture, 'start')!.click();
      http.expectOne(`${BASE}/board/orders/a/start`).flush(order({ id: 'a', status: 'PREPARING' }));
      await answer(fixture, [order({ id: 'a', status: 'PREPARING', lines: [line({ id: 'l1' })] })]);

      expect(query(fixture, 'start')).toBeNull();
      expect(query(fixture, 'ready-order')).not.toBeNull();
    });

    it('lets the cook undo Ready within the window and then sends nothing', async () => {
      const fixture = await live([order({ id: 'a', status: 'PREPARING', lines: [line({ id: 'l1' })] })]);

      query(fixture, 'ready-order')!.click();
      await fixture.whenStable();
      expect(query(fixture, 'ready-order')).toBeNull();

      query(fixture, 'undo-order')!.click();
      await fixture.whenStable();

      expect(query(fixture, 'ready-order')).not.toBeNull();
      http.expectNone(`${BASE}/board/orders/a/ready`);
    });

    it('sends the pending Ready when the screen is closed', async () => {
      const fixture = await live([order({ id: 'a', status: 'PREPARING', lines: [line({ id: 'l1' })] })]);
      query(fixture, 'ready-line-l1')!.click();
      await fixture.whenStable();

      fixture.destroy();

      http.expectOne(`${BASE}/board/lines/l1/ready`).flush(order({ id: 'a' }));
    });

    it('flags a line as sold out', async () => {
      const fixture = await live([order({ id: 'a', status: 'PREPARING', lines: [line({ id: 'l1' })] })]);

      query(fixture, 'flag-l1')!.click();

      http.expectOne(`${BASE}/board/lines/l1/flag`).flush(order({ id: 'a' }));
      await answer(fixture, [order({ id: 'a', status: 'PREPARING', lines: [line({ id: 'l1' })] })]);
    });

    it('hands over and recalls a ready order from the Ready view', async () => {
      const ready = order({ id: 'r', status: 'READY', readyAt: minutesAgo(1), lines: [line({ id: 'l1', state: 'READY' })] });
      const fixture = await live([], [ready]);
      query(fixture, 'view-ready')!.click();
      await fixture.whenStable();

      query(fixture, 'handover')!.click();
      http.expectOne(`${BASE}/board/orders/r/handover`).flush({});
      await answer(fixture, [], [ready]);

      query(fixture, 'recall')!.click();
      http.expectOne(`${BASE}/board/orders/r/recall`).flush({});
      await answer(fixture, [], [ready]);
    });

    it('says why a command was refused, with the message of its code', async () => {
      const fixture = await live([order({ id: 'a', status: 'PREPARING', lines: [line({ id: 'l1' })] })]);
      const toasts = vi.spyOn(TestBed.inject(MessageService), 'add');

      query(fixture, 'undo-order');
      query(fixture, 'ready-line-l1')!.click();
      await fixture.whenStable();
      fixture.destroy();
      http
        .expectOne(`${BASE}/board/lines/l1/ready`)
        .flush(
          { ...serverError, status: 409, code: 'preparation.line-flagged-unavailable' },
          { status: 409, statusText: 'Conflict' },
        );

      expect(toasts).toHaveBeenCalledWith(
        expect.objectContaining({ summary: expect.stringContaining('agotada sin resolver') }),
      );
    });

    it('acknowledges a marker', async () => {
      const fixture = await live([order({ id: 'a', markers: [marker({ id: 'm1', boardOrderId: 'a' })] })]);

      query(fixture, 'ack-m1')!.click();

      http.expectOne(`${BASE}/board/markers/m1/ack`).flush({});
      await answer(fixture, [order({ id: 'a' })]);
      expect(query(fixture, 'marker-m1')).toBeNull();
    });
  });
});
