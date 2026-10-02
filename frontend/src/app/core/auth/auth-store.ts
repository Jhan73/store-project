import { isPlatformBrowser } from '@angular/common';
import { computed, inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { firstValueFrom, timeout } from 'rxjs';
import type { LoginRequest, LoginResponse, Role } from '../api/api-types';
import { AuthApi } from './auth-api';
import { runExclusive } from './refresh-lock';

export interface Session {
  readonly accessToken: string;
  readonly userId: string;
  readonly role: Role;
}

// `rejected`: the server refused the cookie (session over). `unavailable`: no verdict, try again later.
export type RefreshOutcome = 'refreshed' | 'rejected' | 'unavailable';

const REFRESH_LOCK = 'auth-refresh';
// A hung refresh would otherwise hold the cross-tab lock, and every tab's refresh, forever.
const REFRESH_TIMEOUT_MS = 10_000;

function toSession({ accessToken, userId, role }: LoginResponse): Session {
  return { accessToken, userId, role };
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
  private restoration: Promise<void> | null = null;
  private inFlight: Promise<RefreshOutcome> | null = null;
  // Bumped by login and logout so a refresh that started before them cannot change the session afterwards.
  private generation = 0;
  private ended = false;

  readonly session = this.state.asReadonly();
  readonly isAuthenticated = computed(() => this.state() !== null);
  readonly role = computed(() => this.state()?.role ?? null);
  // The last refresh failure that was not a rejection (network, 5xx, timeout); cleared by the next success.
  readonly refreshError = this.failure.asReadonly();

  accessToken(): string | null {
    return this.state()?.accessToken ?? null;
  }

  // True once the server refused the session, until the next login.
  hasEnded(): boolean {
    return this.ended;
  }

  async login(credentials: LoginRequest): Promise<void> {
    const response = await firstValueFrom(this.api.login(credentials));
    this.generation++;
    this.ended = false;
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

  // Concurrent callers in this tab share one request; the lock serializes it across tabs.
  refresh(): Promise<RefreshOutcome> {
    this.inFlight ??= runExclusive(REFRESH_LOCK, () => this.requestRefresh()).finally(() => {
      this.inFlight = null;
    });
    return this.inFlight;
  }

  async logout(): Promise<void> {
    this.generation++;
    this.ended = true;
    this.restoration = Promise.resolve();
    try {
      await firstValueFrom(this.api.logout());
    } catch {
      // The local session ends either way; the refresh cookie expires on its own.
    } finally {
      this.state.set(null);
    }
  }

  private async requestRefresh(): Promise<RefreshOutcome> {
    const generation = this.generation;
    try {
      const response = await firstValueFrom(this.api.refresh().pipe(timeout(REFRESH_TIMEOUT_MS)));
      if (generation !== this.generation) {
        return 'rejected';
      }
      this.state.set(toSession(response));
      this.ended = false;
      this.failure.set(null);
      return 'refreshed';
    } catch (error) {
      if (generation !== this.generation) {
        return 'rejected';
      }
      if (isRejection(error)) {
        this.state.set(null);
        this.ended = true;
        this.failure.set(null);
        return 'rejected';
      }
      this.failure.set(error);
      return 'unavailable';
    }
  }
}
