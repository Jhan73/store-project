import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import type { Shift } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { formatInStoreZone } from '../../../core/time/store-time';
import { IntentKeys } from '../../../shared/state/intent-keys';
import { PendingIds } from '../../../shared/state/pending-ids';
import { RegisterApi } from './register-api';

// Provided by each screen that needs the cashier's shift; there is no realtime topic, so it re-reads after its own commands.
@Injectable()
export class ShiftStore {
  private readonly api = inject(RegisterApi);
  private readonly notifier = inject(ErrorNotifier);
  private readonly destroyRef = inject(DestroyRef);
  private readonly keys = new IntentKeys();

  private readonly current = signal<Shift | null>(null);
  private readonly isLoaded = signal(false);
  private readonly loadFailed = signal(false);
  private readonly storeCurrency = signal<string | null>(null);
  private readonly zone = signal<string | null>(null);
  private lastShiftRequest = 0;

  readonly shift = this.current.asReadonly();
  readonly loaded = this.isLoaded.asReadonly();
  readonly failed = this.loadFailed.asReadonly();
  readonly currency = this.storeCurrency.asReadonly();
  readonly timeZone = this.zone.asReadonly();
  readonly pending = new PendingIds();

  load(): void {
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
          } else if (this.storeCurrency() === null) {
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
    this.api
      .timeZone()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (zone) => this.zone.set(zone), error: () => undefined });
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
