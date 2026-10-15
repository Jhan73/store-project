import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import type {
  ActiveReason,
  CashMovement,
  CashMovementRequest,
  Money,
  Shift,
} from '../../../core/api/api-types';
import { ApiError } from '../../../core/errors/api-error';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { ReasonsApi } from '../../../core/reasons/reasons-api';
import { formatInStoreZone } from '../../../core/time/store-time';
import { IntentKeys } from '../../../shared/state/intent-keys';
import { PendingIds } from '../../../shared/state/pending-ids';
import { RegisterApi } from './register-api';

const SHIFT_STATE_CODES = ['instore.shift-closing', 'instore.shift-closed', 'instore.no-open-shift'];

export function isShiftStateError(error: unknown): boolean {
  return error instanceof ApiError && error.code !== null && SHIFT_STATE_CODES.includes(error.code);
}

// Provided by each screen that needs the cashier's shift; there is no realtime topic, so it re-reads after its own commands.
@Injectable()
export class ShiftStore {
  private readonly api = inject(RegisterApi);
  private readonly reasonsApi = inject(ReasonsApi);
  private readonly notifier = inject(ErrorNotifier);
  private readonly destroyRef = inject(DestroyRef);
  private readonly keys = new IntentKeys();

  private readonly current = signal<Shift | null>(null);
  private readonly isLoaded = signal(false);
  private readonly loadFailed = signal(false);
  private readonly storeCurrency = signal<string | null>(null);
  private readonly zone = signal<string | null>(null);
  private readonly movementList = signal<readonly CashMovement[]>([]);
  private readonly reasonList = signal<ActiveReason[] | null>(null);
  private readonly reasonsFailed = signal(false);
  private readonly closed = signal<Shift | null>(null);
  private readonly noteRequired = signal<{ count: string; difference: Money } | null>(null);
  private readonly locked = signal(false);
  private lastShiftRequest = 0;
  private lastMovementsRequest = 0;

  // The shift as the close returned it: the only place expected cash and the totals are ever shown.
  readonly closedShift = this.closed.asReadonly();
  readonly noteNeeded = this.noteRequired.asReadonly();
  readonly countLocked = this.locked.asReadonly();
  readonly movements = this.movementList.asReadonly();
  readonly reasons = this.reasonList.asReadonly();
  readonly reasonsLoadFailed = this.reasonsFailed.asReadonly();
  readonly shift = this.current.asReadonly();
  readonly loaded = this.isLoaded.asReadonly();
  readonly failed = this.loadFailed.asReadonly();
  readonly currency = this.storeCurrency.asReadonly();
  readonly timeZone = this.zone.asReadonly();
  readonly pending = new PendingIds();

  // `shift` reads only the current shift, for screens that need to know whether one is open and nothing else.
  load(scope: 'full' | 'shift' = 'full'): void {
    const request = ++this.lastShiftRequest;
    this.api
      .currentShift()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (shift) => {
          if (request !== this.lastShiftRequest) {
            return;
          }
          this.current.set(shift);
          this.isLoaded.set(true);
          this.loadFailed.set(false);
          if (shift) {
            this.storeCurrency.set(shift.openingFloat.currency);
          } else if (scope === 'full' && this.storeCurrency() === null) {
            this.loadCurrency();
          }
        },
        error: (error: unknown) => {
          if (request === this.lastShiftRequest) {
            this.loadFailed.set(true);
            this.notifier.show(error);
          }
        },
      });
    if (scope === 'full') {
      this.api
        .timeZone()
        .pipe(takeUntilDestroyed(this.destroyRef))
        .subscribe({ next: (zone) => this.zone.set(zone), error: () => undefined });
    }
  }

  open(amount: string, opened?: () => void): void {
    const currency = this.storeCurrency();
    if (currency === null) {
      return;
    }
    const intent = `open:${amount}`;
    this.pending.add('open');
    this.api
      .openShift({ openingFloat: { amount, currency } }, this.keys.keyFor(intent))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (shift) => {
          this.pending.delete('open');
          this.keys.release(intent);
          this.lastShiftRequest++;
          this.current.set(shift);
          this.isLoaded.set(true);
          opened?.();
        },
        error: (error: unknown) => {
          this.pending.delete('open');
          this.notifier.show(error);
          this.load();
        },
      });
  }

  loadMovements(): void {
    const shift = this.current();
    if (shift === null) {
      return;
    }
    const request = ++this.lastMovementsRequest;
    this.api
      .movements(shift.id)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (movements) => {
          if (request === this.lastMovementsRequest) {
            this.movementList.set(movements);
          }
        },
        error: (error: unknown) => {
          if (request === this.lastMovementsRequest) {
            this.notifier.show(error);
          }
        },
      });
  }

  loadReasons(): void {
    this.reasonsApi
      .active('CASH_OUT')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (reasons) => {
          this.reasonList.set(reasons);
          this.reasonsFailed.set(false);
        },
        error: (error: unknown) => {
          this.reasonsFailed.set(true);
          this.notifier.show(error);
        },
      });
  }

  recordMovement(movement: CashMovementRequest, recorded: () => void): void {
    const shift = this.current();
    if (shift === null) {
      return;
    }
    const intent = `movement:${JSON.stringify(movement)}`;
    this.pending.add('movement');
    this.api
      .recordMovement(shift.id, movement, this.keys.keyFor(intent))
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: () => {
          this.pending.delete('movement');
          this.keys.release(intent);
          this.loadMovements();
          recorded();
        },
        error: (error: unknown) => {
          this.pending.delete('movement');
          this.notifier.show(error);
          this.loadMovements();
          if (isShiftStateError(error)) {
            this.load();
          }
        },
      });
  }

  // The count is locked once the difference was shown, so a retry sends the same count with the note.
  close(count: string, note: string | null): void {
    const shift = this.current();
    const currency = this.storeCurrency();
    if (shift === null || currency === null) {
      return;
    }
    this.pending.add('close');
    this.api
      .closeShift(shift.id, { countedCash: { amount: count, currency }, note })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (outcome) => {
          this.pending.delete('close');
          if (outcome.kind === 'closed') {
            this.lastShiftRequest++;
            this.current.set(null);
            this.closed.set(outcome.shift);
            this.noteRequired.set(null);
            this.locked.set(false);
            this.movementList.set([]);
          } else if (outcome.kind === 'note-required') {
            this.locked.set(false);
            this.noteRequired.set({ count, difference: outcome.difference });
          } else {
            this.locked.set(true);
          }
        },
        error: (error: unknown) => {
          this.pending.delete('close');
          this.notifier.show(error);
          this.load();
        },
      });
  }

  dismissClosed(): void {
    this.closed.set(null);
  }

  private loadCurrency(): void {
    this.api
      .currency()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (currency) => this.storeCurrency.set(currency), error: () => undefined });
  }

  formatTime(instant: string): string {
    const zone = this.zone();
    return zone === null ? '' : formatInStoreZone(instant, zone);
  }
}
