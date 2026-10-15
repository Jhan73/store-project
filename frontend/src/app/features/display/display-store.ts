import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import type { CustomerDisplayEntry } from '../../core/api/api-types';
import { RealtimeClient } from '../../core/realtime/realtime-client';
import { DisplayApi } from './display-api';

// A scheduled order is hidden by the API until it fires and nothing signals that moment, so the screen asks again.
const REFRESH_MS = 60_000;

// Provided by the screen itself: it owns a realtime subscription and a timer that must end with the screen.
@Injectable()
export class DisplayStore {
  private readonly api = inject(DisplayApi);
  private readonly realtime = inject(RealtimeClient);
  private readonly destroyRef = inject(DestroyRef);
  private readonly preparingEntries = signal<readonly CustomerDisplayEntry[]>([]);
  private readonly readyEntries = signal<readonly CustomerDisplayEntry[]>([]);
  private readonly readFailed = signal(false);
  private lastRequest = 0;

  readonly preparing = this.preparingEntries.asReadonly();
  readonly ready = this.readyEntries.asReadonly();
  readonly failed = this.readFailed.asReadonly();
  readonly connected = this.realtime.isConnected;
  readonly loaded = signal(false);

  constructor() {
    const refresh = setInterval(() => {
      if (this.realtime.isConnected()) {
        this.load();
      }
    }, REFRESH_MS);
    this.destroyRef.onDestroy(() => clearInterval(refresh));
    this.realtime
      .refetch('/topic/display')
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(() => this.load());
    this.load();
  }

  // A failed read keeps what is on screen: the page tells it may be out of date.
  load(): void {
    const request = ++this.lastRequest;
    this.api
      .read()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: (display) => {
          if (request === this.lastRequest) {
            this.preparingEntries.set(display.preparing);
            this.readyEntries.set(display.ready);
            this.readFailed.set(false);
            this.loaded.set(true);
          }
        },
        error: () => {
          if (request === this.lastRequest) {
            this.readFailed.set(true);
          }
        },
      });
  }
}
