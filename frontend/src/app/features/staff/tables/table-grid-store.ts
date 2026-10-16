import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import type { OpenTicketRequest, TableGridEntry, Ticket, TicketSummary } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { LiveSync } from '../../../core/realtime/live-sync';
import { RealtimeClient } from '../../../core/realtime/realtime-client';
import { clockOffsetMs, elapsedMinutes } from '../../../core/time/store-time';
import { IntentKeys } from '../../../shared/state/intent-keys';
import { PendingIds } from '../../../shared/state/pending-ids';
import { TicketsApi } from '../tickets/tickets-api';

export interface TableTile extends TableGridEntry {
  readonly minutes: number | null;
}

const CLOCK_TICK_MS = 30_000;

// Provided by the screen itself: it owns a realtime subscription and a timer that must end with the screen.
@Injectable()
export class TableGridStore {
  private readonly api = inject(TicketsApi);
  private readonly realtime = inject(RealtimeClient);
  private readonly notifier = inject(ErrorNotifier);
  private readonly destroyRef = inject(DestroyRef);
  private readonly sync = new LiveSync(this.realtime);
  private readonly keys = new IntentKeys();

  private readonly entries = signal<readonly TableGridEntry[]>([]);
  private readonly counterTickets = signal<readonly TicketSummary[]>([]);
  private readonly loadFailed = signal(false);
  private readonly now = signal(Date.now());
  private readonly offset = signal(0);
  private lastTablesRequest = 0;
  private lastCountersRequest = 0;

  readonly failed = this.loadFailed.asReadonly();
  readonly connected = this.realtime.isConnected;
  readonly enabled = this.sync.enabled;
  readonly pending = new PendingIds();
  readonly counters = computed(() => this.counterTickets().filter((ticket) => ticket.type === 'COUNTER'));
  readonly loaded = signal(false);
  readonly tiles = computed<TableTile[]>(() =>
    this.entries().map((entry) => ({
      ...entry,
      minutes:
        entry.ticketOpenedAt === null
          ? null
          : elapsedMinutes(entry.ticketOpenedAt, this.now(), this.offset()),
    })),
  );

  constructor() {
    const timer = setInterval(() => this.now.set(Date.now()), CLOCK_TICK_MS);
    this.destroyRef.onDestroy(() => clearInterval(timer));
    this.realtime
      .refetch('/topic/tables')
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.load());
    this.load();
  }

  load(): void {
    this.loadTables();
    this.loadCounters();
  }

  openTable(tableId: string, opened: (ticket: Ticket) => void): void {
    this.open(`open:table:${tableId}`, tableId, { tableId }, opened);
  }

  openCounter(name: string, opened: (ticket: Ticket) => void): void {
    const counterName = name.trim();
    this.open(`open:counter:${counterName}`, 'counter', { counterName }, opened);
  }

  private open(
    intent: string,
    pendingId: string,
    request: OpenTicketRequest,
    opened: (ticket: Ticket) => void,
  ): void {
    this.pending.add(pendingId);
    this.api
      .open(request, this.keys.keyFor(intent))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (ticket) => {
          this.pending.delete(pendingId);
          this.keys.release(intent);
          opened(ticket);
        },
        error: (error: unknown) => {
          this.pending.delete(pendingId);
          this.notifier.show(error);
          this.load();
        },
      });
  }

  private loadTables(): void {
    const request = ++this.lastTablesRequest;
    const read = this.sync.begin();
    this.api
      .tables()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ tables, serverDate }) => {
          if (request !== this.lastTablesRequest) {
            return;
          }
          this.entries.set(tables);
          this.offset.set(clockOffsetMs(serverDate, Date.now()));
          this.now.set(Date.now());
          this.loadFailed.set(false);
          this.loaded.set(true);
          this.sync.applied(read);
        },
        error: (error: unknown) => this.readFailed(request === this.lastTablesRequest, error),
      });
  }

  private loadCounters(): void {
    const request = ++this.lastCountersRequest;
    this.api
      .openTickets()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (tickets) => {
          if (request === this.lastCountersRequest) {
            this.counterTickets.set(tickets);
          }
        },
        error: (error: unknown) => {
          if (request === this.lastCountersRequest) {
            this.notifier.show(error);
          }
        },
      });
  }

  private readFailed(latest: boolean, error: unknown): void {
    if (latest) {
      this.loadFailed.set(true);
      this.sync.failed();
      this.notifier.show(error);
    }
  }
}
