import { DOCUMENT } from '@angular/common';
import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, Observable } from 'rxjs';
import type { BoardAllDay, BoardOrder, StationOption } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { LiveSync } from '../../../core/realtime/live-sync';
import { RealtimeClient } from '../../../core/realtime/realtime-client';
import { clockOffsetMs, elapsedMinutes } from '../../../core/time/store-time';
import { AgeLevel, ageLevel, ageStartOf, AgeThresholds, DEFAULT_AGE_THRESHOLDS, thresholdsOf } from './board-age';
import { PendingIds } from '../../../shared/state/pending-ids';
import { UndoQueue } from '../../../shared/state/undo-queue';
import { BoardApi } from './board-api';
import { newMarkerKinds, unacknowledgedMarkerIds } from './board-markers';
import { BoardSound } from './board-sound';

export type BoardView = 'tickets' | 'allDay' | 'ready';

export interface BoardCard {
  readonly order: BoardOrder;
  readonly minutes: number;
  readonly level: AgeLevel;
}

const STATION_KEY = 'board.station';
const CLOCK_TICK_MS = 30_000;
// A scheduled card is hidden by the API until it fires and nothing signals that moment, so the board asks again.
const REFRESH_MS = 60_000;

function readStation(): string | null {
  try {
    return localStorage.getItem(STATION_KEY);
  } catch {
    return null;
  }
}

function storeStation(station: string | null): void {
  try {
    if (station === null) {
      localStorage.removeItem(STATION_KEY);
    } else {
      localStorage.setItem(STATION_KEY, station);
    }
  } catch {
    // Private mode or blocked storage: the choice just lasts for this page.
  }
}

// Provided by the screen itself: it owns a realtime subscription and timers that must end with the screen.
@Injectable()
export class BoardStore {
  private readonly api = inject(BoardApi);
  private readonly realtime = inject(RealtimeClient);
  private readonly notifier = inject(ErrorNotifier);
  private readonly sound = inject(BoardSound);
  private readonly destroyRef = inject(DestroyRef);
  private readonly sync = new LiveSync(this.realtime);

  private readonly tickets = signal<readonly BoardOrder[]>([]);
  private readonly readyOrders = signal<readonly BoardOrder[]>([]);
  private readonly currentAllDay = signal<BoardAllDay>({ groups: [], individual: [] });
  private readonly currentView = signal<BoardView>('tickets');
  private readonly loadFailed = signal(false);
  private readonly now = signal(Date.now());
  private readonly offset = signal(0);
  private readonly ageThresholds = signal<AgeThresholds>(DEFAULT_AGE_THRESHOLDS);
  // The store zone arrives with the thresholds; the device zone covers the gap and a failed read.
  private readonly zone = signal(Intl.DateTimeFormat().resolvedOptions().timeZone);
  private readonly stationList = signal<readonly StationOption[]>([]);
  private readonly chosenStation = signal<string | null>(readStation());
  private readonly queue = new UndoQueue();
  private destroyed = false;
  // Markers nobody has acknowledged as of the previous read; null until the first read has been applied.
  private knownMarkers: ReadonlySet<string> | null = null;
  private lastRequest = 0;

  readonly view = this.currentView.asReadonly();
  readonly allDay = this.currentAllDay.asReadonly();
  readonly timeZone = this.zone.asReadonly();
  readonly stations = this.stationList.asReadonly();
  // The station this device filters by, or null for all of them.
  readonly station = this.chosenStation.asReadonly();
  readonly failed = this.loadFailed.asReadonly();
  readonly connected = this.realtime.isConnected;
  readonly enabled = this.sync.enabled;
  readonly pending = new PendingIds();
  // Ids of the orders and lines whose Ready is still inside the undo window.
  readonly queuedIds = this.queue.keys;
  readonly loaded = signal(false);
  readonly cards = computed<BoardCard[]>(() =>
    this.tickets()
      .map((order) => ({ order, start: ageStartOf(order) }))
      .sort((a, b) => Date.parse(a.start) - Date.parse(b.start))
      .map(({ order, start }) => {
        const minutes = elapsedMinutes(start, this.now(), this.offset());
        return { order, minutes, level: ageLevel(minutes, this.ageThresholds()) };
      }),
  );
  readonly readyCards = computed<BoardCard[]>(() =>
    this.readyOrders().map((order) => ({
      order,
      minutes: elapsedMinutes(order.readyAt ?? order.sentAt, this.now(), this.offset()),
      level: 'normal',
    })),
  );

  constructor() {
    const clock = setInterval(() => this.now.set(Date.now()), CLOCK_TICK_MS);
    const refresh = setInterval(() => {
      if (this.realtime.isConnected()) {
        this.load(true);
      }
    }, REFRESH_MS);
    const page = inject(DOCUMENT).defaultView;
    const flush = () => this.queue.flush();
    page?.addEventListener('pagehide', flush);
    this.destroyRef.onDestroy(() => {
      clearInterval(clock);
      clearInterval(refresh);
      page?.removeEventListener('pagehide', flush);
      this.destroyed = true;
      this.queue.flush();
    });
    this.realtime
      .refetch('/topic/board')
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.load());
    this.api
      .thresholds()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => {
          this.ageThresholds.set(thresholdsOf(response));
          this.zone.set(response.timeZone);
        },
        error: () => this.ageThresholds.set(DEFAULT_AGE_THRESHOLDS),
      });
    this.api
      .stations()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (stations) => {
          this.stationList.set(stations);
          this.dropUnknownStation(true);
        },
        error: () => this.dropUnknownStation(false),
      });
    this.load();
  }

  isQueued(id: string): boolean {
    return this.queue.has(id);
  }

  start(orderId: string): void {
    this.run(orderId, () => this.api.start(orderId));
  }

  handover(orderId: string): void {
    this.run(orderId, () => this.api.handover(orderId));
  }

  recall(orderId: string): void {
    this.run(orderId, () => this.api.recall(orderId));
  }

  flag(lineId: string): void {
    this.run(lineId, () => this.api.flagLine(lineId));
  }

  acknowledge(markerId: string): void {
    this.run(markerId, () => this.api.acknowledge(markerId));
  }

  // Ready is the only command with an undo window: it waits before it is sent.
  readyOrder(orderId: string): void {
    this.schedule(orderId, () => this.api.readyOrder(orderId, true));
  }

  readyLine(lineId: string): void {
    this.schedule(lineId, () => this.api.readyLine(lineId, true));
  }

  undo(id: string): void {
    this.queue.cancel(id);
  }

  setView(view: BoardView): void {
    this.currentView.set(view);
  }

  setStation(station: string | null, remember = true): void {
    this.chosenStation.set(station);
    if (remember) {
      storeStation(station);
    }
    // Markers that merely came into scope with the new filter are not news.
    this.knownMarkers = null;
    this.load();
  }

  // A background read that fails keeps the last good data and tries again on the next tick.
  load(background = false): void {
    const request = ++this.lastRequest;
    const read = this.sync.begin();
    const station = this.chosenStation();
    forkJoin({ tickets: this.api.tickets(station), ready: this.api.ready(station), allDay: this.api.allDay(station) })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ tickets, ready, allDay }) => {
          if (request !== this.lastRequest) {
            return;
          }
          this.tickets.set(tickets.orders);
          this.readyOrders.set(ready);
          this.currentAllDay.set(allDay);
          this.announce([...tickets.orders, ...ready]);
          this.offset.set(clockOffsetMs(tickets.serverDate, Date.now()));
          this.now.set(Date.now());
          this.loadFailed.set(false);
          this.loaded.set(true);
          this.sync.applied(read);
        },
        error: (error: unknown) => {
          if (request === this.lastRequest && !background) {
            this.loadFailed.set(true);
            this.sync.failed();
            this.notifier.show(error);
          }
        },
      });
  }

  // A remembered station may no longer exist; an unknown id would show an empty board, so it means all stations.
  // Only a successful read proves the station is gone: after a failed one the saved choice is kept for the next visit.
  private dropUnknownStation(forget: boolean): void {
    const station = this.chosenStation();
    if (station !== null && !this.stationList().some((option) => option.id === station)) {
      this.setStation(null, forget);
    }
  }

  // The first read is the baseline: only markers that show up in a later read are news.
  private announce(orders: readonly BoardOrder[]): void {
    if (this.knownMarkers !== null) {
      const kinds = newMarkerKinds(this.knownMarkers, orders);
      if (kinds.length > 0) {
        this.sound.play(kinds);
      }
    }
    this.knownMarkers = unacknowledgedMarkerIds(orders);
  }

  private schedule(id: string, request: () => Observable<unknown>): void {
    if (!this.enabled() || this.pending.has(id)) {
      return;
    }
    this.queue.schedule(id, () => this.send(id, request()));
  }

  private run(pendingId: string, request: () => Observable<unknown>): void {
    if (!this.enabled() || this.pending.has(pendingId)) {
      return;
    }
    this.send(pendingId, request());
  }

  // Not tied to the screen's lifetime: a command flushed while the screen closes must still go out.
  private send(pendingId: string, request: Observable<unknown>): void {
    this.pending.add(pendingId);
    request.subscribe({
      next: () => {
        this.pending.delete(pendingId);
        this.reload();
      },
      error: (error: unknown) => {
        this.pending.delete(pendingId);
        this.notifier.show(error);
        this.reload();
      },
    });
  }

  private reload(): void {
    if (!this.destroyed) {
      this.load();
    }
  }
}
