import { signal } from '@angular/core';

export const UNDO_WINDOW_MS = 5_000;

// Commands wait out the undo window before they are sent; `flush` sends them all at once (page close).
export class UndoQueue {
  private readonly entries = new Map<string, { send: () => void; timer: ReturnType<typeof setTimeout> }>();
  private readonly pendingKeys = signal<ReadonlySet<string>>(new Set());

  readonly keys = this.pendingKeys.asReadonly();

  constructor(private readonly delayMs = UNDO_WINDOW_MS) {}

  has(key: string): boolean {
    return this.pendingKeys().has(key);
  }

  schedule(key: string, send: () => void): boolean {
    if (this.entries.has(key)) {
      return false;
    }
    this.entries.set(key, { send, timer: setTimeout(() => this.release(key)?.send(), this.delayMs) });
    this.publish();
    return true;
  }

  cancel(key: string): boolean {
    const entry = this.entries.get(key);
    if (!entry) {
      return false;
    }
    clearTimeout(entry.timer);
    this.entries.delete(key);
    this.publish();
    return true;
  }

  flush(): void {
    for (const key of [...this.entries.keys()]) {
      const entry = this.entries.get(key);
      if (entry) {
        clearTimeout(entry.timer);
        this.entries.delete(key);
        entry.send();
      }
    }
    this.publish();
  }

  private release(key: string) {
    const entry = this.entries.get(key);
    this.entries.delete(key);
    this.publish();
    return entry;
  }

  private publish(): void {
    this.pendingKeys.set(new Set(this.entries.keys()));
  }
}
