import { isPlatformBrowser } from '@angular/common';
import { computed, inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { firstValueFrom, timeout } from 'rxjs';
import type { LoginRequest, LoginResponse, Role } from '../api/api-types';
import { AuthApi } from './auth-api';
import { runExclusive } from './refresh-lock';

export interface Session {
  // When the refresh token family ends and the sign-in must be repeated; null when the server did not say.
  readonly sessionExpiresAt: number | null;
  readonly accessToken: string;
  readonly userId: string;
  readonly role: Role;
}

// `rejected`: the server refused the cookie (session over). `unavailable`: no verdict, try again later.
export type RefreshOutcome = 'refreshed' | 'rejected' | 'unavailable';

const REFRESH_LOCK = 'auth-refresh';
// A hung refresh would otherwise hold the cross-tab lock, and every tab's refresh, forever.
const REFRESH_TIMEOUT_MS = 10_000;
// A sign-out hook (flushing queued commands) that never answers must not keep the employee signed in.
const SIGN_OUT_HOOK_TIMEOUT_MS = 5_000;

function expiryOf(token: string): number | null {
  try {
    const payload = token.split('.')[1]?.replace(/-/g, '+').replace(/_/g, '/');
    const exp = payload === undefined ? undefined : (JSON.parse(atob(payload)) as { exp?: unknown }).exp;
    return typeof exp === 'number' ? exp * 1000 : null;
  } catch {
    return null;
  }
}

function instantOf(value: string | undefined): number | null {
  const instant = value === undefined ? Number.NaN : Date.parse(value);
  return Number.isNaN(instant) ? null : instant;
}

function toSession({ accessToken, userId, role, sessionExpiresAt }: LoginResponse): Session {
  return { accessToken, userId, role, sessionExpiresAt: instantOf(sessionExpiresAt) };
}

function isRejection(error: unknown): boolean {
  return typeof error === 'object' && error !== null && (error as { status?: unknown }).status === 401;
}

@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly api = inject(AuthApi);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly state = signal<Session | null>(null);
  private readonly failure = signal<unknown>(null);
  private readonly identityShift = signal(false);
  private restoration: Promise<void> | null = null;
  private inFlight: Promise<RefreshOutcome> | null = null;
  // Bumped by login and logout so a refresh that started before them cannot change the session afterwards.
  private generation = 0;
  private readonly endedState = signal(false);
  private readonly signOutHooks = new Set<() => Promise<void> | void>();

  readonly session = this.state.asReadonly();
  readonly isAuthenticated = computed(() => this.state() !== null);
  readonly role = computed(() => this.state()?.role ?? null);
  // The last refresh failure that was not a rejection (network, 5xx, timeout); cleared by the next success.
  readonly refreshError = this.failure.asReadonly();
  // True when a refresh brought another user's session (the cookie is shared between tabs); until the next login.
  readonly identityChanged = this.identityShift.asReadonly();
  // True once this tab's session is gone (refused, signed out or another identity), until the next login.
  readonly sessionEnded = this.endedState.asReadonly();
  // When the refresh token family ends; null when nobody is signed in or the server did not say.
  readonly sessionExpiresAt = computed(() => this.state()?.sessionExpiresAt ?? null);

  accessToken(): string | null {
    return this.state()?.accessToken ?? null;
  }

  // True once this tab's session is gone (refused, signed out or another identity), until the next login.
  hasEnded(): boolean {
    return this.endedState();
  }

  async login(credentials: LoginRequest): Promise<void> {
    const response = await firstValueFrom(this.api.login(credentials));
    this.generation++;
    this.endedState.set(false);
    this.identityShift.set(false);
    this.failure.set(null);
    this.state.set(toSession(response));
    this.restoration ??= Promise.resolve();
  }

  // Never runs on the server: tokens do not exist there. A transient failure is not remembered, so the next caller retries.
  restore(): Promise<void> {
    if (!this.restoration) {
      this.restoration = this.isBrowser
        ? this.refresh().then((outcome) => {
            if (outcome === 'unavailable') {
              this.restoration = null;
            }
          })
        : Promise.resolve();
    }
    return this.restoration;
  }

  // Renews the token when it would expire within `windowMs`, so work that must still be authorized then
  // (a command flushed as the page closes) does not depend on a refresh that cannot finish by then.
  async ensureFresh(windowMs: number): Promise<void> {
    const token = this.accessToken();
    const expiry = token === null ? null : expiryOf(token);
    if (expiry !== null && expiry - Date.now() < windowMs) {
      await this.refresh();
    }
  }

  // Runs before the server is told to end the session, while the token still works. Returns how to remove it.
  onSignOut(hook: () => Promise<void> | void): () => void {
    this.signOutHooks.add(hook);
    return () => this.signOutHooks.delete(hook);
  }

  // Concurrent callers in this tab share one request; the lock serializes it across tabs.
  refresh(): Promise<RefreshOutcome> {
    this.inFlight ??= runExclusive(REFRESH_LOCK, () => this.requestRefresh()).finally(() => {
      this.inFlight = null;
    });
    return this.inFlight;
  }

  // False when the server could not revoke the cookie: the session stays, because dropping it locally
  // would let the next restore reopen it on a shared device.
  async logout(): Promise<boolean> {
    if (this.signOutHooks.size > 0) {
      await this.runSignOutHooks();
    }
    try {
      await firstValueFrom(this.api.logout());
    } catch (error) {
      if (!isRejection(error)) {
        return false;
      }
    }
    this.generation++;
    this.endedState.set(true);
    this.restoration = Promise.resolve();
    this.state.set(null);
    return true;
  }

  private async runSignOutHooks(): Promise<void> {
    const running = Promise.allSettled([...this.signOutHooks].map(async (hook) => hook()));
    let timer: ReturnType<typeof setTimeout> | undefined;
    const gaveUp = new Promise<void>((resolve) => (timer = setTimeout(resolve, SIGN_OUT_HOOK_TIMEOUT_MS)));
    await Promise.race([running, gaveUp]);
    clearTimeout(timer);
  }

  private async requestRefresh(): Promise<RefreshOutcome> {
    const generation = this.generation;
    try {
      const response = await firstValueFrom(this.api.refresh().pipe(timeout(REFRESH_TIMEOUT_MS)));
      if (generation !== this.generation) {
        return 'rejected';
      }
      const previous = this.state();
      if (previous !== null && previous.userId !== response.userId) {
        // Not a logout: the cookie now belongs to the other user, and revoking it would sign them out.
        this.state.set(null);
        this.endedState.set(true);
        this.identityShift.set(true);
        this.failure.set(null);
        return 'rejected';
      }
      this.state.set(toSession(response));
      this.endedState.set(false);
      this.failure.set(null);
      return 'refreshed';
    } catch (error) {
      if (generation !== this.generation) {
        return 'rejected';
      }
      if (isRejection(error)) {
        this.state.set(null);
        this.endedState.set(true);
        this.failure.set(null);
        return 'rejected';
      }
      this.failure.set(error);
      return 'unavailable';
    }
  }
}
