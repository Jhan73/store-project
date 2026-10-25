import { isPlatformBrowser } from '@angular/common';
import { computed, DestroyRef, inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { AuthStore } from './auth-store';

// Screens that stay on for weeks lose the session when the refresh token family ends; they warn this many days before.
export const SESSION_WARNING_DAYS = 3;
const DAY_MS = 24 * 60 * 60 * 1000;
const TICK_MS = 60_000;

@Injectable({ providedIn: 'root' })
export class SessionExpiryWarning {
  private readonly auth = inject(AuthStore);
  private readonly now = signal(Date.now());

  // Whole days left once inside the warning window (0 means under a day, or already past); null means no warning.
  readonly daysLeft = computed<number | null>(() => {
    const endsAt = this.auth.sessionExpiresAt();
    if (endsAt === null) {
      return null;
    }
    const left = endsAt - this.now();
    return left > SESSION_WARNING_DAYS * DAY_MS ? null : Math.max(0, Math.floor(left / DAY_MS));
  });

  constructor() {
    if (isPlatformBrowser(inject(PLATFORM_ID))) {
      const tick = setInterval(() => this.now.set(Date.now()), TICK_MS);
      inject(DestroyRef).onDestroy(() => clearInterval(tick));
    }
  }
}
