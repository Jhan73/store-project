import { computed, effect, signal, untracked, type Signal } from '@angular/core';

export interface SyncRead {
  readonly epoch: number;
  readonly connected: boolean;
}

// Actions stay off until a read that began on the live connection has been applied. Create it in an injection context.
export class LiveSync {
  private readonly synced = signal(false);
  private epoch = 0;

  readonly enabled = computed(() => this.synced() && this.realtime.isConnected());

  constructor(private readonly realtime: { readonly isConnected: Signal<boolean> }) {
    effect(() => {
      if (!this.realtime.isConnected()) {
        untracked(() => {
          this.epoch++;
          this.synced.set(false);
        });
      }
    });
  }

  begin(): SyncRead {
    return { epoch: this.epoch, connected: this.realtime.isConnected() };
  }

  applied(read: SyncRead): void {
    if (read.connected && read.epoch === this.epoch && this.realtime.isConnected()) {
      this.synced.set(true);
    }
  }

  failed(): void {
    this.synced.set(false);
  }
}
