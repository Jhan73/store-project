import { computed, Injectable, signal } from '@angular/core';
import type { UndoQueue } from './undo-queue';

export interface UndoItem {
  readonly queue: UndoQueue;
  readonly key: string;
  readonly message: string;
}

// Puts a command on its UndoQueue and lists a notice for as long as it waits there.
@Injectable({ providedIn: 'root' })
export class UndoNotice {
  private readonly items = signal<readonly UndoItem[]>([]);

  readonly active = computed(() => this.items().filter((item) => item.queue.has(item.key)));

  schedule(queue: UndoQueue, key: string, message: string, send: () => void): boolean {
    if (!queue.schedule(key, send)) {
      return false;
    }
    this.items.update((items) => [
      ...items.filter((item) => item.queue.has(item.key) && !(item.queue === queue && item.key === key)),
      { queue, key, message },
    ]);
    return true;
  }

  undo(item: UndoItem): void {
    item.queue.cancel(item.key);
  }
}
