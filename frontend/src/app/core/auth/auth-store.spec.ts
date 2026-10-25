import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_ORIGIN } from '../api/api-config';
import { AuthStore } from './auth-store';

const REFRESH = 'http://api.test/api/v1/auth/refresh';
const LOGIN = 'http://api.test/api/v1/auth/login';
const LOGOUT = 'http://api.test/api/v1/auth/logout';
const UNAUTHORIZED = { status: 401, statusText: 'Unauthorized' };
const credentials = { email: 'a@b.pe', password: 'x' };

function session(accessToken: string, role: 'ADMIN' | 'CASHIER' = 'CASHIER') {
  return { accessToken, tokenType: 'Bearer', userId: 'u1', role };
}

function tokenExpiring(inSeconds: number): string {
  const payload = btoa(JSON.stringify({ exp: Math.floor(Date.now() / 1000) + inSeconds }))
    .replace(/\+/g, '-')
    .replace(/\//g, '_')
    .replace(/=+$/, '');
  return `header.${payload}.signature`;
}

describe('AuthStore', () => {
  let store: AuthStore;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    store = TestBed.inject(AuthStore);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    http.verify();
    vi.unstubAllGlobals();
  });

  async function signIn(token = 'jwt-1') {
    const login = store.login(credentials);
    http.expectOne(LOGIN).flush(session(token));
    await login;
  }

  it('starts anonymous', () => {
    expect(store.isAuthenticated()).toBe(false);
    expect(store.accessToken()).toBeNull();
    expect(store.role()).toBeNull();
  });

  it('keeps the session in memory after login', async () => {
    const done = store.login(credentials);
    http.expectOne(LOGIN).flush(session('jwt-1', 'ADMIN'));
    await done;

    expect(store.accessToken()).toBe('jwt-1');
    expect(store.role()).toBe('ADMIN');
    expect(store.isAuthenticated()).toBe(true);
    expect(localStorage.length).toBe(0);
    expect(sessionStorage.length).toBe(0);
  });

  describe('session expiry', () => {
    const endsAt = '2026-11-24T10:00:00Z';

    it('knows when the session ends from the login answer and renews it with each refresh', async () => {
      const done = store.login(credentials);
      http.expectOne(LOGIN).flush({ ...session('jwt-1'), sessionExpiresAt: endsAt });
      await done;
      expect(store.sessionExpiresAt()).toBe(Date.parse(endsAt));

      const refreshed = store.refresh();
      http.expectOne(REFRESH).flush({ ...session('jwt-2'), sessionExpiresAt: '2026-11-25T10:00:00Z' });
      await refreshed;

      expect(store.sessionExpiresAt()).toBe(Date.parse('2026-11-25T10:00:00Z'));
    });

    it('does not know it when there is no session or the answer carries no valid date', async () => {
      expect(store.sessionExpiresAt()).toBeNull();

      const done = store.login(credentials);
      http.expectOne(LOGIN).flush({ ...session('jwt-1'), sessionExpiresAt: 'not a date' });
      await done;

      expect(store.sessionExpiresAt()).toBeNull();
    });

    it('says the session ended once the server refused it, until the next login', async () => {
      await signIn();
      expect(store.sessionEnded()).toBe(false);

      const refreshed = store.refresh();
      http.expectOne(REFRESH).flush(null, UNAUTHORIZED);
      await refreshed;
      expect(store.sessionEnded()).toBe(true);

      await signIn('jwt-2');
      expect(store.sessionEnded()).toBe(false);
    });
  });

  it('leaves the store anonymous when login is rejected', async () => {
    const done = store.login(credentials);
    http.expectOne(LOGIN).flush({ code: 'auth.invalid-credentials' }, UNAUTHORIZED);

    await expect(done).rejects.toBeDefined();
    expect(store.isAuthenticated()).toBe(false);
  });

  describe('restore', () => {
    it('restores the session from the refresh cookie', async () => {
      const done = store.restore();
      http.expectOne(REFRESH).flush(session('jwt-2'));
      await done;

      expect(store.accessToken()).toBe('jwt-2');
    });

    it('stays anonymous without a valid cookie, without failing', async () => {
      const done = store.restore();
      http.expectOne(REFRESH).flush({ code: 'auth.invalid-refresh-token' }, UNAUTHORIZED);

      await expect(done).resolves.toBeUndefined();
      expect(store.isAuthenticated()).toBe(false);
    });

    it('retries on the next call after a transient failure instead of caching it', async () => {
      const first = store.restore();
      http.expectOne(REFRESH).flush(null, { status: 503, statusText: 'x' });
      await first;
      expect(store.isAuthenticated()).toBe(false);

      const second = store.restore();
      http.expectOne(REFRESH).flush(session('jwt-2'));
      await second;

      expect(store.accessToken()).toBe('jwt-2');
    });

    it('caches a definitive rejection', async () => {
      const first = store.restore();
      http.expectOne(REFRESH).flush(null, UNAUTHORIZED);
      await first;

      await store.restore();

      http.expectNone(REFRESH);
    });

    it('runs once, however many callers ask', async () => {
      const first = store.restore();
      const second = store.restore();
      http.expectOne(REFRESH).flush(session('jwt-2'));
      await Promise.all([first, second]);
      await store.restore();

      http.expectNone(REFRESH);
    });

    it('does nothing after a login that already happened', async () => {
      await signIn();

      await store.restore();

      http.expectNone(REFRESH);
    });
  });

  describe('refresh', () => {
    it('shares one request among concurrent callers (single-flight)', async () => {
      const calls = [store.refresh(), store.refresh(), store.refresh()];

      http.expectOne(REFRESH).flush(session('jwt-3'));

      expect(await Promise.all(calls)).toEqual(['refreshed', 'refreshed', 'refreshed']);
      expect(store.accessToken()).toBe('jwt-3');
    });

    it('allows a new refresh once the previous one settled', async () => {
      const first = store.refresh();
      http.expectOne(REFRESH).flush(session('jwt-3'));
      await first;

      const second = store.refresh();
      http.expectOne(REFRESH).flush(session('jwt-4'));

      await second;
      expect(store.accessToken()).toBe('jwt-4');
    });

    it('clears the session when the refresh is rejected', async () => {
      await signIn();

      const done = store.refresh();
      http.expectOne(REFRESH).flush({ code: 'auth.invalid-refresh-token' }, UNAUTHORIZED);

      expect(await done).toBe('rejected');
      expect(store.isAuthenticated()).toBe(false);
    });

    it.each([
      ['a 503 while the environment starts', { status: 503, statusText: 'Service Unavailable' }],
      ['a gateway error', { status: 502, statusText: 'Bad Gateway' }],
    ])('keeps the session on %s', async (_label, init) => {
      await signIn();

      const done = store.refresh();
      http.expectOne(REFRESH).flush({ code: 'common.service-unavailable' }, init);

      expect(await done).toBe('unavailable');
      expect(store.accessToken()).toBe('jwt-1');
      expect(store.refreshError()).toMatchObject({ status: init.status });
    });

    it('keeps the session on a network failure', async () => {
      await signIn();

      const done = store.refresh();
      http.expectOne(REFRESH).error(new ProgressEvent('error'));

      expect(await done).toBe('unavailable');
      expect(store.isAuthenticated()).toBe(true);
      expect(store.refreshError()).toMatchObject({ status: 0 });
    });

    it('gives up on a refresh that hangs, so it cannot hold the lock forever', async () => {
      vi.useFakeTimers();
      await signIn();

      const done = store.refresh();
      const request = http.expectOne(REFRESH);
      vi.advanceTimersByTime(10_000);

      expect(await done).toBe('unavailable');
      expect(request.cancelled).toBe(true);
      expect(store.isAuthenticated()).toBe(true);
      vi.useRealTimers();
    });

    it('forgets the transient failure once a refresh succeeds', async () => {
      const failed = store.refresh();
      http.expectOne(REFRESH).flush(null, { status: 503, statusText: 'x' });
      await failed;

      const ok = store.refresh();
      http.expectOne(REFRESH).flush(session('jwt-3'));
      await ok;

      expect(store.refreshError()).toBeNull();
    });

    it('discards a refresh that finishes after logout', async () => {
      await signIn();

      const refresh = store.refresh();
      const logout = store.logout();
      http.expectOne(LOGOUT).flush(null, { status: 204, statusText: 'No Content' });
      await logout;
      http.expectOne(REFRESH).flush(session('late'));

      expect(await refresh).toBe('rejected');
      expect(store.isAuthenticated()).toBe(false);
    });

    it('keeps a refresh valid when the logout request could not reach the server', async () => {
      await signIn();

      const refresh = store.refresh();
      const logout = store.logout();
      http.expectOne(LOGOUT).error(new ProgressEvent('error'));
      await logout;
      http.expectOne(REFRESH).flush(session('renewed'));

      expect(await refresh).toBe('refreshed');
      expect(store.isAuthenticated()).toBe(true);
    });

    it('remembers that the session ended until the next login', async () => {
      await signIn();
      expect(store.hasEnded()).toBe(false);

      const done = store.refresh();
      http.expectOne(REFRESH).flush(null, UNAUTHORIZED);
      await done;
      expect(store.hasEnded()).toBe(true);

      await signIn('jwt-9');
      expect(store.hasEnded()).toBe(false);
    });

    it('ends the session when another tab signed in as someone else', async () => {
      await signIn();

      const done = store.refresh();
      http.expectOne(REFRESH).flush({ ...session('jwt-b'), userId: 'u2' });

      expect(await done).toBe('rejected');
      expect(store.isAuthenticated()).toBe(false);
      expect(store.accessToken()).toBeNull();
      expect(store.hasEnded()).toBe(true);
      expect(store.identityChanged()).toBe(true);
    });

    it('does not sign the other user out on the server when it drops their identity', async () => {
      await signIn();

      const done = store.refresh();
      http.expectOne(REFRESH).flush({ ...session('jwt-b'), userId: 'u2' });
      await done;

      http.expectNone(LOGOUT);
    });

    it('does not restore the other identity by itself afterwards', async () => {
      await signIn();
      const done = store.refresh();
      http.expectOne(REFRESH).flush({ ...session('jwt-b'), userId: 'u2' });
      await done;

      await store.restore();

      http.expectNone(REFRESH);
      expect(store.isAuthenticated()).toBe(false);
    });

    it('keeps the session when the refresh brings the same user', async () => {
      await signIn();

      const done = store.refresh();
      http.expectOne(REFRESH).flush(session('jwt-2'));

      expect(await done).toBe('refreshed');
      expect(store.accessToken()).toBe('jwt-2');
      expect(store.identityChanged()).toBe(false);
    });

    it('forgets the identity change once someone signs in again', async () => {
      await signIn();
      const done = store.refresh();
      http.expectOne(REFRESH).flush({ ...session('jwt-b'), userId: 'u2' });
      await done;

      await signIn('jwt-c');

      expect(store.identityChanged()).toBe(false);
    });

    it('serializes refreshes across tabs with the Web Locks API', async () => {
      const request = vi.fn((_name: string, callback: () => Promise<unknown>) => callback());
      vi.stubGlobal('navigator', { locks: { request } });

      const done = store.refresh();
      await vi.waitFor(() => http.expectOne(REFRESH).flush(session('jwt-5')));
      await done;

      expect(request).toHaveBeenCalledWith('auth-refresh', expect.any(Function));
    });
  });

  describe('logout', () => {
    it('clears the session and revokes the cookie', async () => {
      await signIn();

      const done = store.logout();
      http.expectOne(LOGOUT).flush(null, { status: 204, statusText: 'No Content' });
      await done;

      expect(store.isAuthenticated()).toBe(false);
    });

    it('keeps the session and reports it when the server cannot be reached', async () => {
      await signIn();

      const done = store.logout();
      http.expectOne(LOGOUT).error(new ProgressEvent('error'));

      expect(await done).toBe(false);
      expect(store.isAuthenticated()).toBe(true);
      expect(store.hasEnded()).toBe(false);
    });

    it('keeps the session when the server fails the sign out', async () => {
      await signIn();

      const done = store.logout();
      http.expectOne(LOGOUT).flush(null, { status: 503, statusText: 'Unavailable' });

      expect(await done).toBe(false);
      expect(store.isAuthenticated()).toBe(true);
    });

    it('signs out for good on a retry after a failure', async () => {
      await signIn();
      const failed = store.logout();
      http.expectOne(LOGOUT).error(new ProgressEvent('error'));
      await failed;

      const retry = store.logout();
      http.expectOne(LOGOUT).flush(null, { status: 204, statusText: 'No Content' });

      expect(await retry).toBe(true);
      expect(store.isAuthenticated()).toBe(false);
      expect(store.hasEnded()).toBe(true);
    });

    it('counts a rejected cookie as signed out', async () => {
      await signIn();

      const done = store.logout();
      http.expectOne(LOGOUT).flush(null, UNAUTHORIZED);

      expect(await done).toBe(true);
      expect(store.isAuthenticated()).toBe(false);
    });
  });

  describe('ensureFresh', () => {
    it('refreshes a token that expires inside the window', async () => {
      await signIn(tokenExpiring(10));

      const done = store.ensureFresh(30_000);
      http.expectOne(REFRESH).flush(session('jwt-new'));
      await done;

      expect(store.accessToken()).toBe('jwt-new');
    });

    it('leaves a token that stays valid through the window', async () => {
      await signIn(tokenExpiring(600));

      await store.ensureFresh(30_000);

      http.expectNone(REFRESH);
    });

    it('leaves a token it cannot read alone', async () => {
      await signIn('opaque');

      await store.ensureFresh(30_000);

      http.expectNone(REFRESH);
    });

    it('does nothing without a session', async () => {
      await store.ensureFresh(30_000);

      http.expectNone(REFRESH);
    });
  });

  describe('sign-out hooks', () => {
    it('runs before the server is called and waits for them', async () => {
      await signIn();
      let release!: () => void;
      const hook = vi.fn(() => new Promise<void>((resolve) => (release = resolve)));
      store.onSignOut(hook);

      const done = store.logout();
      expect(hook).toHaveBeenCalled();
      http.expectNone(LOGOUT);

      release();
      await vi.waitFor(() => http.expectOne(LOGOUT).flush(null, { status: 204, statusText: 'No Content' }));
      expect(await done).toBe(true);
    });

    it('does not let a failing hook stop the sign out', async () => {
      await signIn();
      store.onSignOut(() => Promise.reject(new Error('boom')));

      const done = store.logout();
      await vi.waitFor(() => http.expectOne(LOGOUT).flush(null, { status: 204, statusText: 'No Content' }));

      expect(await done).toBe(true);
    });

    it('stops waiting for a hook that never answers', async () => {
      vi.useFakeTimers();
      await signIn();
      store.onSignOut(() => new Promise<void>(() => undefined));

      const done = store.logout();
      await vi.advanceTimersByTimeAsync(10_000);
      http.expectOne(LOGOUT).flush(null, { status: 204, statusText: 'No Content' });

      expect(await done).toBe(true);
      vi.useRealTimers();
    });

    it('forgets a hook that was removed', async () => {
      await signIn();
      const hook = vi.fn();
      store.onSignOut(hook)();

      const done = store.logout();
      await vi.waitFor(() => http.expectOne(LOGOUT).flush(null, { status: 204, statusText: 'No Content' }));
      await done;

      expect(hook).not.toHaveBeenCalled();
    });
  });
});
