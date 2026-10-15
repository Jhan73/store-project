import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { forkJoin } from 'rxjs';
import type { BoardAllDay, BoardOrder } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { LiveSync } from '../../../core/realtime/live-sync';
import { RealtimeClient } from '../../../core/realtime/realtime-client';
import { clockOffsetMs, elapsedMinutes } from '../../../core/time/store-time';
import { AgeLevel, ageLevel, ageStartOf, AgeThresholds, DEFAULT_AGE_THRESHOLDS, thresholdsOf } from './board-age';
import { BoardApi } from './board-api';

export type BoardView = 'tickets' | 'allDay' | 'ready';

export interface BoardCard {
  readonly order: BoardOrder;
  readonly minutes: number;
  readonly level: AgeLevel;
}

const CLOCK_TICK_MS = 30_000;
// A scheduled card is hidden by the API until it fires and nothing signals that moment, so the board asks again.
const REFRESH_MS = 60_000;

// Provided by the screen itself: it owns a realtime subscription and timers that must end with the screen.
@Injectable()
export class BoardStore {
  private readonly api = inject(BoardApi);
  private readonly realtime = inject(RealtimeClient);
  private readonly notifier = inject(ErrorNotifier);
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
  private lastRequest = 0;

  readonly view = this.currentView.asReadonly();
  readonly allDay = this.currentAllDay.asReadonly();
  readonly failed = this.loadFailed.asReadonly();
  readonly connected = this.realtime.isConnected;
  readonly enabled = this.sync.enabled;
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
        this.load();
      }
    }, REFRESH_MS);
    this.destroyRef.onDestroy(() => {
      clearInterval(clock);
      clearInterval(refresh);
    });
    this.realtime
      .refetch('/topic/board')
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.load());
    this.api
      .thresholds()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (response) => this.ageThresholds.set(thresholdsOf(response)),
        error: () => this.ageThresholds.set(DEFAULT_AGE_THRESHOLDS),
      });
    this.load();
  }

  setView(view: BoardView): void {
    this.currentView.set(view);
  }

  load(): void {
    const request = ++this.lastRequest;
    const read = this.sync.begin();
    forkJoin({ tickets: this.api.tickets(), ready: this.api.ready(), allDay: this.api.allDay() })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ tickets, ready, allDay }) => {
          if (request !== this.lastRequest) {
            return;
          }
          this.tickets.set(tickets.orders);
          this.readyOrders.set(ready);
          this.currentAllDay.set(allDay);
          this.offset.set(clockOffsetMs(tickets.serverDate, Date.now()));
          this.now.set(Date.now());
          this.loadFailed.set(false);
          this.loaded.set(true);
          this.sync.applied(read);
        },
        error: (error: unknown) => {
          if (request === this.lastRequest) {
            this.loadFailed.set(true);
            this.sync.failed();
            this.notifier.show(error);
          }
        },
      });
  }
}
