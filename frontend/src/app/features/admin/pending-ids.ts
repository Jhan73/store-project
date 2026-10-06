import { signal } from '@angular/core';

// The ids with a request in flight, readable from a template so their controls can be disabled.
export class PendingIds {
  private readonly ids = signal<ReadonlySet<string>>(new Set());

  has(id: string): boolean {
    return this.ids().has(id);
  }

  add(id: string): void {
    this.ids.update((current) => new Set(current).add(id));
  }

  delete(id: string): void {
    this.ids.update((current) => {
      const next = new Set(current);
      next.delete(id);
      return next;
    });
  }
}
