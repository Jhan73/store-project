import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import type { Shift } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { formatInStoreZone } from '../../../core/time/store-time';
import { PendingIds } from '../../../shared/state/pending-ids';
import { RegisterApi } from './register-api';

// A cashier cannot have more than one open shift, so one page holds every open shift of the store.
const PAGE_SIZE = 100;
const SHORT_ID = 8;

@Injectable()
export class OpenShiftsStore {
  private readonly api = inject(RegisterApi);
  private readonly notifier = inject(ErrorNotifier);
  private readonly destroyRef = inject(DestroyRef);

  private readonly list = signal<Shift[] | null>(null);
  private readonly loadFailed = signal(false);
  private readonly names = signal<ReadonlyMap<string, string>>(new Map());
  private readonly zone = signal<string | null>(null);
  private readonly forcedShift = signal<Shift | null>(null);
  private lastListRequest = 0;

  readonly shifts = this.list.asReadonly();
  readonly failed = this.loadFailed.asReadonly();
  readonly forced = this.forcedShift.asReadonly();
  readonly timeZone = this.zone.asReadonly();
  readonly pending = new PendingIds();

  load(): void {
    this.loadShifts();
    this.api
      .staffAccounts()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (accounts) => this.names.set(new Map(accounts.map((account) => [account.id, account.email]))),
        error: () => undefined,
      });
    this.api
      .timeZone()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({ next: (zone) => this.zone.set(zone), error: () => undefined });
  }

  forceClose(shift: Shift, reason: string, closed: () => void): void {
    const pendingId = `force:${shift.id}`;
    this.pending.add(pendingId);
    this.api
      .forceClose(shift.id, { reason })
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (result) => {
          this.pending.delete(pendingId);
          this.forcedShift.set(result);
          this.loadShifts();
          closed();
        },
        error: (error: unknown) => {
          this.pending.delete(pendingId);
          this.notifier.show(error);
          this.loadShifts();
        },
      });
  }

  dismissForced(): void {
    this.forcedShift.set(null);
  }

  // An account that was removed still has a shift: its id, shortened, is all there is to show.
  nameOf(cashierId: string): string {
    return this.names().get(cashierId) ?? cashierId.slice(0, SHORT_ID);
  }

  formatTime(instant: string): string {
    const zone = this.zone();
    return zone === null ? '' : formatInStoreZone(instant, zone);
  }

  private loadShifts(): void {
    const request = ++this.lastListRequest;
    this.api
      .shifts('OPEN', 0, PAGE_SIZE)
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (page) => {
          if (request === this.lastListRequest) {
            this.list.set(page.content);
            this.loadFailed.set(false);
          }
        },
        error: (error: unknown) => {
          if (request === this.lastListRequest) {
            this.loadFailed.set(true);
            this.notifier.show(error);
          }
        },
      });
  }
}
