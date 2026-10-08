import { isPlatformBrowser } from '@angular/common';
import { computed, inject, Injectable, PLATFORM_ID, signal } from '@angular/core';

const STORAGE_KEY = 'app-sidebar-pinned';

@Injectable({ providedIn: 'root' })
export class SidebarStore {
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly open = signal(false);

  readonly pinned = signal(false);
  readonly expanded = computed(() => this.pinned() || this.open());

  constructor() {
    if (this.isBrowser) {
      this.pinned.set(this.readPinned());
    }
  }

  toggle(): void {
    if (this.expanded()) {
      this.open.set(false);
      this.setPinned(false);
    } else {
      this.open.set(true);
    }
  }

  togglePin(): void {
    if (this.pinned()) {
      this.open.set(true);
      this.setPinned(false);
    } else {
      this.open.set(false);
      this.setPinned(true);
    }
  }

  dismiss(): void {
    this.open.set(false);
  }

  private setPinned(pinned: boolean): void {
    this.pinned.set(pinned);
    if (this.isBrowser) {
      this.store(pinned);
    }
  }

  private readPinned(): boolean {
    try {
      return localStorage.getItem(STORAGE_KEY) === 'true';
    } catch {
      return false;
    }
  }

  private store(pinned: boolean): void {
    try {
      if (pinned) {
        localStorage.setItem(STORAGE_KEY, 'true');
      } else {
        localStorage.removeItem(STORAGE_KEY);
      }
    } catch {
      // Private mode or blocked storage: the choice just lasts for this page.
    }
  }
}
