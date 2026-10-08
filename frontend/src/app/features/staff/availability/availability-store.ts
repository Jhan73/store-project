import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { computed, DestroyRef, effect, inject, Injectable, signal, untracked } from '@angular/core';
import type { Menu, MenuModifierGroup } from '../../../core/api/api-types';
import { ErrorNotifier } from '../../../core/errors/error-notifier';
import { RealtimeClient } from '../../../core/realtime/realtime-client';
import { PendingIds } from '../../../shared/state/pending-ids';
import { AvailabilityApi } from './availability-api';

export type ItemKind = 'product' | 'option';

// Provided by the screen itself, not by the route: it owns a realtime subscription that must end with the screen.
@Injectable()
export class AvailabilityStore {
  private readonly api = inject(AvailabilityApi);
  private readonly realtime = inject(RealtimeClient);
  private readonly notifier = inject(ErrorNotifier);
  private readonly destroyRef = inject(DestroyRef);

  private readonly current = signal<Menu | null>(null);
  private readonly loadFailed = signal(false);
  private readonly synced = signal(false);
  private lastRequest = 0;
  private connectionEpoch = 0;

  readonly menu = this.current.asReadonly();
  readonly failed = this.loadFailed.asReadonly();
  readonly connected = this.realtime.isConnected;
  readonly pending = new PendingIds();
  // Actions stay off until a read that began on the live connection has been applied.
  readonly enabled = computed(() => this.synced() && this.realtime.isConnected());
  readonly groups = computed(() => {
    const seen = new Map<string, MenuModifierGroup>();
    for (const category of this.current()?.categories ?? []) {
      for (const product of category.products) {
        for (const group of product.modifierGroups) {
          if (!seen.has(group.id)) {
            seen.set(group.id, group);
          }
        }
      }
    }
    return [...seen.values()];
  });

  constructor() {
    effect(() => {
      if (!this.realtime.isConnected()) {
        untracked(() => {
          this.connectionEpoch++;
          this.synced.set(false);
        });
      }
    });
    this.realtime
      .refetch('/topic/catalog')
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.load());
    this.load();
  }

  load(): void {
    const request = ++this.lastRequest;
    const epoch = this.connectionEpoch;
    const startedConnected = this.realtime.isConnected();
    this.api
      .menu()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (menu) => {
          if (request !== this.lastRequest) {
            return;
          }
          this.current.set(menu);
          this.loadFailed.set(false);
          if (startedConnected && epoch === this.connectionEpoch && this.realtime.isConnected()) {
            this.synced.set(true);
          }
        },
        error: (error: unknown) => {
          if (request === this.lastRequest) {
            this.loadFailed.set(true);
            this.synced.set(false);
            this.notifier.show(error);
          }
        },
      });
  }

  availableOf(id: string): boolean | undefined {
    for (const category of this.current()?.categories ?? []) {
      for (const product of category.products) {
        if (product.id === id) {
          return product.available;
        }
        for (const group of product.modifierGroups) {
          const found = group.options.find((option) => option.id === id);
          if (found) {
            return found.available;
          }
        }
      }
    }
    return undefined;
  }

  // `settled` runs once the request is over, so a control can be put back to what the menu holds.
  set(kind: ItemKind, id: string, available: boolean, settled: () => void): void {
    this.pending.add(id);
    const request =
      kind === 'product' ? this.api.setProduct(id, available) : this.api.setOption(id, available);
    request.pipe(takeUntilDestroyed(this.destroyRef)).subscribe({
      next: (result) => {
        this.pending.delete(id);
        this.patch(result.id, result.available);
        settled();
      },
      error: (error: unknown) => {
        this.pending.delete(id);
        this.notifier.show(error);
        this.load();
        settled();
      },
    });
  }

  private patch(id: string, available: boolean): void {
    this.current.update((menu) =>
      menu && {
        categories: menu.categories.map((category) => ({
          ...category,
          products: category.products.map((product) => ({
            ...product,
            available: product.id === id ? available : product.available,
            modifierGroups: product.modifierGroups.map((group) => ({
              ...group,
              options: group.options.map((option) =>
                option.id === id ? { ...option, available } : option,
              ),
            })),
          })),
        })),
      },
    );
  }
}
