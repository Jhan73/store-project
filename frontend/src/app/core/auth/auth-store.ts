import { isPlatformBrowser } from '@angular/common';
import { computed, inject, Injectable, PLATFORM_ID, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import type { LoginRequest, LoginResponse, Role } from '../api/api-types';
import { AuthApi } from './auth-api';
import { runExclusive } from './refresh-lock';

export interface Session {
  readonly accessToken: string;
  readonly userId: string;
  readonly role: Role;
}

const REFRESH_LOCK = 'auth-refresh';

function toSession({ accessToken, userId, role }: LoginResponse): Session {
  return { accessToken, userId, role };
}

@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly api = inject(AuthApi);
  private readonly isBrowser = isPlatformBrowser(inject(PLATFORM_ID));
  private readonly state = signal<Session | null>(null);
  private restoration: Promise<void> | null = null;
  private inFlight: Promise<boolean> | null = null;

  readonly session = this.state.asReadonly();
  readonly isAuthenticated = computed(() => this.state() !== null);
  readonly role = computed(() => this.state()?.role ?? null);

  accessToken(): string | null {
    return this.state()?.accessToken ?? null;
  }

  async login(credentials: LoginRequest): Promise<void> {
    this.state.set(toSession(await firstValueFrom(this.api.login(credentials))));
    this.restoration ??= Promise.resolve();
  }

  // Never runs on the server: tokens do not exist there.
  restore(): Promise<void> {
    this.restoration ??= this.isBrowser
      ? this.refresh().then(() => undefined)
      : Promise.resolve();
    return this.restoration;
  }

  // Concurrent callers in this tab share one request; the lock serializes it across tabs.
  refresh(): Promise<boolean> {
    this.inFlight ??= runExclusive(REFRESH_LOCK, () => this.requestRefresh()).finally(() => {
      this.inFlight = null;
    });
    return this.inFlight;
  }

  async logout(): Promise<void> {
    try {
      await firstValueFrom(this.api.logout());
    } catch {
      // The local session ends either way; the refresh cookie expires on its own.
    } finally {
      this.state.set(null);
    }
  }

  private async requestRefresh(): Promise<boolean> {
    try {
      this.state.set(toSession(await firstValueFrom(this.api.refresh())));
      return true;
    } catch {
      this.state.set(null);
      return false;
    }
  }
}
