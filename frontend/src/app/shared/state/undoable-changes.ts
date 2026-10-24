import { signal } from '@angular/core';
import type { Observable } from 'rxjs';
import { UndoNotice } from './undo-notice';
import { UndoQueue } from './undo-queue';

// A reversible edit shown at once and sent after the undo window; `pending` is the value to display meanwhile.
export class UndoableChanges<T> {
  private readonly queue = new UndoQueue();
  private readonly values = signal<ReadonlyMap<string, T>>(new Map());
  private readonly running = signal<ReadonlySet<string>>(new Set());

  constructor(private readonly notice: UndoNotice) {}

  pending(key: string): T | undefined {
    return this.queue.has(key) || this.running().has(key) ? this.values().get(key) : undefined;
  }

  has(key: string): boolean {
    return this.queue.has(key) || this.running().has(key);
  }

  schedule(
    key: string,
    value: T,
    message: string,
    run: () => Observable<unknown>,
    onError: (error: unknown) => void,
  ): boolean {
    const scheduled = this.notice.schedule(this.queue, key, message, () => {
      this.running.update((keys) => new Set(keys).add(key));
      run().subscribe({
        complete: () => this.finish(key),
        error: (error: unknown) => {
          this.finish(key);
          onError(error);
        },
      });
    });
    if (scheduled) {
      this.values.update((map) => new Map(map).set(key, value));
    }
    return scheduled;
  }

  flush(): void {
    this.queue.flush();
  }

  private finish(key: string): void {
    this.running.update((keys) => {
      const next = new Set(keys);
      next.delete(key);
      return next;
    });
  }
}
