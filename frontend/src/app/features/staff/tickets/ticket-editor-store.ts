import { computed, DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute } from '@angular/router';
import { forkJoin, Observable } from 'rxjs';
import type {
  ActiveReason,
  LineRequest,
  Menu,
  Money,
  RecordPaymentRequest,
  TableGridEntry,
  Ticket,
  TicketLine,
  VoidLineRequest,
  VoidPaymentRequest,
  VoidSaleRequest,
} from '../../../core/api/api-types';
import { AuthStore } from '../../../core/auth/auth-store';
import { ApiError } from '../../../core/errors/api-error';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { LiveSync } from '../../../core/realtime/live-sync';
import { ReasonsApi } from '../../../core/reasons/reasons-api';
import { RealtimeClient } from '../../../core/realtime/realtime-client';
import { IntentKeys } from '../../../shared/state/intent-keys';
import { PendingIds } from '../../../shared/state/pending-ids';
import { RegisterApi } from '../register/register-api';
import { isShiftStateError } from '../register/shift-store';
import { TicketsApi } from './tickets-api';

export type LineState = 'UNSENT' | 'SENT' | 'READY' | 'VOIDED';

export const MAX_LINE_QUANTITY = 99;

export function lineRequestOf(line: TicketLine, quantity: number): LineRequest {
  return {
    productId: line.productId,
    quantity,
    modifierOptionIds: line.modifiers.map((modifier) => modifier.optionId),
    note: line.note,
    allergy: line.allergy,
  };
}

// Provided by the screen itself: it owns realtime subscriptions that must end with the screen.
@Injectable()
export class TicketEditorStore {
  private readonly api = inject(TicketsApi);
  private readonly register = inject(RegisterApi);
  private readonly realtime = inject(RealtimeClient);
  private readonly notifier = inject(ErrorNotifier);
  private readonly reasonsApi = inject(ReasonsApi);
  private readonly auth = inject(AuthStore);
  private readonly route = inject(ActivatedRoute);
  private readonly destroyRef = inject(DestroyRef);
  private readonly sync = new LiveSync(this.realtime);
  private readonly keys = new IntentKeys();

  private readonly current = signal<Ticket | null>(null);
  private readonly currentMenu = signal<Menu | null>(null);
  private readonly readyLineIds = signal<ReadonlySet<string>>(new Set());
  private readonly tableList = signal<readonly TableGridEntry[]>([]);
  private readonly voidReasons = signal<ActiveReason[] | null>(null);
  private readonly reasonsFailed = signal(false);
  private readonly notFound = signal(false);
  private readonly loadFailed = signal(false);
  private readonly splitList = signal<readonly Money[] | null>(null);
  private readonly shiftErrors = signal(0);
  private ticketId = '';
  private lastTicketRequest = 0;
  private lastBoardRequest = 0;
  private lastMenuRequest = 0;
  private lastTablesRequest = 0;
  private lastReasonsRequest = 0;

  readonly ticket = this.current.asReadonly();
  readonly menu = this.currentMenu.asReadonly();
  readonly missing = this.notFound.asReadonly();
  readonly failed = this.loadFailed.asReadonly();
  readonly connected = this.realtime.isConnected;
  readonly enabled = this.sync.enabled;
  readonly pending = new PendingIds();
  // The even split of what is still owed, exactly as the API computed it.
  readonly splitParts = this.splitList.asReadonly();
  // Counts the commands refused because of the cashier's shift, so the owner of the shift can read it again.
  readonly shiftStateErrors = this.shiftErrors.asReadonly();
  readonly reasons = this.voidReasons.asReadonly();
  readonly reasonsLoadFailed = this.reasonsFailed.asReadonly();
  readonly freeTables = computed(() => this.tableList().filter((table) => table.status === 'FREE'));
  readonly isOpen = computed(() => this.current()?.status === 'OPEN');
  readonly isTable = computed(() => this.current()?.type === 'TABLE');
  readonly unsent = computed(() => this.current()?.lines.filter((line) => line.state === 'UNSENT') ?? []);
  // The lines that went out together in the latest send, minus those voided since.
  readonly lastRound = computed(() => {
    const sent = (this.current()?.lines ?? []).filter(
      (line) => line.state === 'SENT' && line.sentAt !== null,
    );
    const latest = sent.reduce<string | null>(
      (max, line) => (max === null || line.sentAt! > max ? line.sentAt : max),
      null,
    );
    return sent.filter((line) => line.sentAt === latest);
  });

  constructor() {
    this.route.paramMap.pipe(takeUntilDestroyed()).subscribe((params) => {
      this.ticketId = params.get('id') ?? '';
      this.current.set(null);
      this.notFound.set(false);
      this.load();
      this.loadMenu();
    });
    this.realtime
      .refetch('/topic/board')
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.load());
    this.realtime
      .refetch('/topic/tables')
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.loadTicket());
    this.realtime
      .refetch('/topic/catalog')
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.loadMenu());
  }

  load(): void {
    this.loadTicket();
    this.loadBoard();
  }

  stateOf(line: TicketLine): LineState {
    if (line.state === 'SENT' && this.readyLineIds().has(line.id)) {
      return 'READY';
    }
    return line.state;
  }

  // A server may not void a line once it is ready: that is for a cashier or an admin, and it counts as waste.
  mayVoid(line: TicketLine): boolean {
    return line.state === 'SENT' && (this.auth.role() !== 'SERVER' || this.stateOf(line) !== 'READY');
  }

  mustAskCashier(line: TicketLine): boolean {
    return line.state === 'SENT' && !this.mayVoid(line);
  }

  loadVoidReasons(): void {
    const request = ++this.lastReasonsRequest;
    this.voidReasons.set(null);
    this.reasonsFailed.set(false);
    this.reasonsApi
      .active('VOID')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (reasons) => {
          if (request === this.lastReasonsRequest) {
            this.voidReasons.set(reasons);
          }
        },
        error: (error: unknown) => {
          if (request === this.lastReasonsRequest) {
            this.reasonsFailed.set(true);
            this.notifier.show(error);
          }
        },
      });
  }

  voidLine(line: TicketLine, request: VoidLineRequest, voided: () => void): void {
    const intent = `void:${line.id}:${JSON.stringify(request)}`;
    this.command(
      `void:${line.id}`,
      this.api.voidLine(this.ticketId, line.id, request, this.keys.keyFor(intent)),
      () => {
        this.keys.release(intent);
        voided();
      },
    );
  }

  addLine(line: LineRequest, added: () => void): void {
    const intent = `add:${JSON.stringify(line)}`;
    this.command('add', this.api.addLine(this.ticketId, line, this.keys.keyFor(intent)), () => {
      this.keys.release(intent);
      added();
    });
  }

  changeQuantity(line: TicketLine, quantity: number): void {
    this.command(
      `line:${line.id}`,
      this.api.changeLine(this.ticketId, line.id, lineRequestOf(line, quantity)),
    );
  }

  removeLine(lineId: string): void {
    const pendingId = `line:${lineId}`;
    this.pending.add(pendingId);
    this.api
      .removeLine(this.ticketId, lineId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.pending.delete(pendingId);
          this.loadTicket();
        },
        error: (error: unknown) => this.commandFailed(pendingId, error),
      });
  }

  send(): void {
    this.command('send', this.api.send(this.ticketId), () => this.loadBoard());
  }

  repeatLastRound(): void {
    const lineIds = this.lastRound().map((line) => line.id);
    const intent = `repeat:${lineIds.join(',')}`;
    this.command('repeat', this.api.repeat(this.ticketId, lineIds, this.keys.keyFor(intent)), () =>
      this.keys.release(intent),
    );
  }

  setBillRequested(requested: boolean): void {
    this.command('bill', this.api.setBillRequested(this.ticketId, requested));
  }

  transfer(tableId: string, moved: () => void): void {
    this.command('transfer', this.api.transfer(this.ticketId, tableId), moved);
  }

  pay(request: RecordPaymentRequest, paid: () => void): void {
    const intent = `pay:${JSON.stringify(request)}`;
    this.command('pay', this.register.pay(this.ticketId, request, this.keys.keyFor(intent)), () => {
      this.keys.release(intent);
      paid();
    });
  }

  voidPayment(paymentId: string, request: VoidPaymentRequest, voided: () => void): void {
    this.command(
      `void-payment:${paymentId}`,
      this.register.voidPayment(this.ticketId, paymentId, request),
      () => {
        this.splitList.set(null);
        voided();
      },
    );
  }

  voidSale(request: VoidSaleRequest, voided: () => void): void {
    this.command('void-sale', this.register.voidSale(this.ticketId, request), voided);
  }

  loadSplit(parts: number): void {
    this.pending.add('split');
    this.register
      .split(this.ticketId, parts)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (amounts) => {
          this.pending.delete('split');
          this.splitList.set(amounts);
        },
        error: (error: unknown) => {
          this.pending.delete('split');
          this.notifier.show(error);
        },
      });
  }

  clearSplit(): void {
    this.splitList.set(null);
  }

  loadTables(): void {
    const request = ++this.lastTablesRequest;
    this.api
      .tables()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: ({ tables }) => {
          if (request === this.lastTablesRequest) {
            this.tableList.set(tables);
          }
        },
        error: (error: unknown) => {
          if (request === this.lastTablesRequest) {
            this.notifier.show(error);
          }
        },
      });
  }

  private loadTicket(): void {
    const request = ++this.lastTicketRequest;
    const read = this.sync.begin();
    this.api
      .ticket(this.ticketId)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (ticket) => {
          if (request !== this.lastTicketRequest) {
            return;
          }
          this.current.set(ticket);
          this.notFound.set(false);
          this.loadFailed.set(false);
          this.sync.applied(read);
        },
        error: (error: unknown) => {
          if (request !== this.lastTicketRequest) {
            return;
          }
          this.sync.failed();
          if (error instanceof ApiError && error.code === 'instore.ticket-not-found') {
            this.notFound.set(true);
            return;
          }
          this.loadFailed.set(true);
          this.notifier.show(error);
        },
      });
  }

  private loadBoard(): void {
    const request = ++this.lastBoardRequest;
    // A card leaves `tickets` once all of it is ready, so the ready view is read too.
    forkJoin([this.api.board(), this.api.boardReady()])
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (views) => {
          if (request === this.lastBoardRequest) {
            const ready = views
              .flat()
              .flatMap((order) => order.lines)
              .filter((line) => line.state === 'READY');
            this.readyLineIds.set(new Set(ready.map((line) => line.sourceLineId)));
          }
        },
        // The board only decorates the lines; without it they show as sent.
        error: () => undefined,
      });
  }

  private loadMenu(): void {
    const request = ++this.lastMenuRequest;
    this.api
      .menu()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (menu) => {
          if (request === this.lastMenuRequest) {
            this.currentMenu.set(menu);
          }
        },
        error: (error: unknown) => {
          if (request === this.lastMenuRequest) {
            this.notifier.show(error);
          }
        },
      });
  }

  private command(pendingId: string, request: Observable<Ticket>, done?: () => void): void {
    this.pending.add(pendingId);
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (ticket) => {
        this.pending.delete(pendingId);
        // A read that began before the command would bring back the old ticket.
        this.lastTicketRequest++;
        this.current.set(ticket);
        // The read that would have synced the editor after a reconnect was just discarded.
        if (this.connected() && !this.enabled()) {
          this.loadTicket();
        }
        done?.();
      },
      error: (error: unknown) => this.commandFailed(pendingId, error),
    });
  }

  private commandFailed(pendingId: string, error: unknown): void {
    this.pending.delete(pendingId);
    if (isShiftStateError(error)) {
      this.shiftErrors.update((count) => count + 1);
    }
    this.notifier.show(error);
    this.load();
  }
}
