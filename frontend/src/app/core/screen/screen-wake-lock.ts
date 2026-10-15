import { DOCUMENT, isPlatformBrowser } from '@angular/common';
import { DestroyRef, inject, Injectable, PLATFORM_ID, signal } from '@angular/core';

// Keeps the screen on while a board is open. Provided by the screen that needs it, so it ends with that screen.
@Injectable()
export class ScreenWakeLock {
  private readonly document = inject(DOCUMENT);
  private readonly browser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly holding = signal(false);
  private sentinel: WakeLockSentinel | null = null;
  private wanted = false;
  private requesting = false;

  readonly active = this.holding.asReadonly();

  private readonly onVisibility = () => {
    if (this.document.visibilityState === 'visible') {
      void this.acquire();
    }
  };

  constructor() {
    inject(DestroyRef).onDestroy(() => this.stop());
  }

  start(): void {
    if (!this.browser || this.wanted) {
      return;
    }
    this.wanted = true;
    // The browser drops the lock whenever the page is hidden, so it has to be taken again on return.
    this.document.addEventListener('visibilitychange', this.onVisibility);
    void this.acquire();
  }

  stop(): void {
    if (!this.wanted) {
      return;
    }
    this.wanted = false;
    this.document.removeEventListener('visibilitychange', this.onVisibility);
    const sentinel = this.sentinel;
    this.sentinel = null;
    this.holding.set(false);
    void sentinel?.release().catch(() => undefined);
  }

  private async acquire(): Promise<void> {
    const wakeLock = this.document.defaultView?.navigator.wakeLock;
    if (!wakeLock || !this.wanted || this.sentinel || this.requesting) {
      return;
    }
    this.requesting = true;
    try {
      const sentinel = await wakeLock.request('screen');
      if (!this.wanted) {
        void sentinel.release().catch(() => undefined);
        return;
      }
      this.sentinel = sentinel;
      this.holding.set(true);
      sentinel.addEventListener('release', () => {
        if (this.sentinel === sentinel) {
          this.sentinel = null;
          this.holding.set(false);
        }
      });
    } catch {
      // The browser may refuse (battery saver, hidden page): the next visibility change tries again.
    } finally {
      this.requesting = false;
    }
  }
}
