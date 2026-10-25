import { DOCUMENT } from '@angular/common';
import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin, Observable } from 'rxjs';
import type { BoardAllDay, BoardOrder, StationOption } from '../../../core/api/api-types';
import { AuthStore } from '../../../core/auth/auth-store';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { LiveSync } from '../../../core/realtime/live-sync';
import { RealtimeClient } from '../../../core/realtime/realtime-client';
import { clockOffsetMs, elapsedMinutes } from '../../../core/time/store-time';
import { AgeLevel, ageLevel, ageStartOf, AgeThresholds, DEFAULT_AGE_THRESHOLDS, thresholdsOf } from './board-age';
import { PendingIds } from '../../../shared/state/pending-ids';
import { indicatorsOf } from '../../../shared/ui/board-card/board-lines';
import { UNDO_WINDOW_MS, UndoQueue } from '../../../shared/state/undo-queue';
import { BoardApi } from './board-api';
import { newMarkerKinds, unacknowledgedMarkerIds } from './board-markers';
import { BoardSound } from './board-sound';

export type BoardView = 'tickets' | 'allDay' | 'ready';

export interface BoardCard {
  readonly order: BoardOrder;
  readonly minutes: number;
  readonly level: AgeLevel;
}

// Elapsed counts from the fire time of a scheduled card; arrival always counts from when it was sent.
export type BoardSort = 'elapsed' | 'arrivalAsc' | 'arrivalDesc';

interface Aged {
  readonly order: BoardOrder;
  readonly start: string;
}

const SORTS: Record<BoardSort, (a: Aged, b: Aged) => number> = {
  elapsed: (a, b) => Date.parse(a.start) - Date.parse(b.start),
  arrivalAsc: (a, b) => Date.parse(a.order.sentAt) - Date.parse(b.order.sentAt),
  arrivalDesc: (a, b) => Date.parse(b.order.sentAt) - Date.parse(a.order.sentAt),
};

const STATION_KEY = 'board.station';
const CLOCK_TICK_MS = 30_000;
// A scheduled card is hidden by the API until it fires and nothing signals that moment, so the board asks again.
const REFRESH_MS = 60_000;
// Background reads that fail in a row before the board admits its data may be stale (about this many minutes).
export const BACKGROUND_FAILURES_BEFORE_FAILED = 3;
// A Ready can be flushed as the page closes, when no refresh can finish: the token must outlive the undo window.
const TOKEN_MARGIN_MS = 30_000;

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
  private readonly auth = inject(AuthStore);
  private readonly destroyRef = inject(DestroyRef);
  private readonly sync = new LiveSync(this.realtime);

  private readonly tickets = signal<readonly BoardOrder[]>([]);
  private readonly readyOrders = signal<readonly BoardOrder[]>([]);
  private readonly currentAllDay = signal<BoardAllDay>({ groups: [], individual: [] });
  private readonly currentView = signal<BoardView>('tickets');
  private readonly currentSort = signal<BoardSort>('elapsed');
  private readonly currentAttention = signal(false);
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
  private readonly inFlight = new Set<Promise<void>>();
  // Markers nobody has acknowledged as of the previous read; null until the first read has been applied.
  private knownMarkers: ReadonlySet<string> | null = null;
  private lastRequest = 0;
  private backgroundFailures = 0;

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
  // Tab counts stay those of the whole board, so a filter never looks like an empty kitchen.
  readonly ticketCount = computed(() => this.tickets().length);
  readonly readyCount = computed(() => this.readyOrders().length);
  readonly sortOrder = this.currentSort.asReadonly();
  readonly attentionOnly = this.currentAttention.asReadonly();
  readonly cards = computed<BoardCard[]>(() =>
    this.tickets()
      .filter((order) => this.passesFilter(order))
      .map((order) => ({ order, start: ageStartOf(order) }))
      .sort(SORTS[this.currentSort()])
      .map(({ order, start }) => {
        const minutes = elapsedMinutes(start, this.now(), this.offset());
        return { order, minutes, level: ageLevel(minutes, this.ageThresholds()) };
      }),
  );
  readonly readyCards = computed<BoardCard[]>(() =>
    this.readyOrders()
      .filter((order) => this.passesFilter(order))
      .map((order) => ({
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
    const leaveSignOut = this.auth.onSignOut(() => this.drain());
    this.destroyRef.onDestroy(() => {
      leaveSignOut();
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

  // Sends what is waiting out its undo window and resolves once every command in flight has been answered.
  drain(): Promise<void> {
    this.queue.flush();
    return Promise.allSettled([...this.inFlight]).then(() => undefined);
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

  setSortOrder(sort: BoardSort): void {
    this.currentSort.set(sort);
  }

  setAttentionOnly(only: boolean): void {
    this.currentAttention.set(only);
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

  // A background read that fails keeps the last good data and tries again on the next tick; only after several in a
  // row does the board admit it may be stale.
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
          this.backgroundFailures = 0;
          this.loaded.set(true);
          this.sync.applied(read);
        },
        error: (error: unknown) => {
          if (request !== this.lastRequest) {
            return;
          }
          if (!background) {
            this.loadFailed.set(true);
            this.sync.failed();
            this.notifier.show(error);
          } else if (++this.backgroundFailures >= BACKGROUND_FAILURES_BEFORE_FAILED) {
            // No toast: the banner is on screen and the next read tries again by itself.
            this.loadFailed.set(true);
            this.sync.failed();
          }
        },
      });
  }

  private passesFilter(order: BoardOrder): boolean {
    if (!this.currentAttention()) {
      return true;
    }
    const { allergy, note } = indicatorsOf(order);
    return allergy || note;
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
    if (this.queue.schedule(id, () => this.send(id, request()))) {
      void this.auth.ensureFresh(UNDO_WINDOW_MS + TOKEN_MARGIN_MS);
    }
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
    let settle!: () => void;
    const answered = new Promise<void>((resolve) => (settle = resolve));
    this.inFlight.add(answered);
    void answered.then(() => this.inFlight.delete(answered));
    request.subscribe({
      next: () => {
        this.pending.delete(pendingId);
        this.reload();
        settle();
      },
      error: (error: unknown) => {
        this.pending.delete(pendingId);
        this.notifier.show(error);
        this.reload();
        settle();
      },
    });
  }

  private reload(): void {
    if (!this.destroyed) {
      this.load();
    }
  }
}
