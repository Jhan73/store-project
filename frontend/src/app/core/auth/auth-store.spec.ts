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

      expect(await Promise.all(calls)).toEqual([true, true, true]);
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

      expect(await done).toBe(false);
      expect(store.isAuthenticated()).toBe(false);
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

    it('clears the session even when the server cannot be reached', async () => {
      await signIn();

      const done = store.logout();
      http.expectOne(LOGOUT).error(new ProgressEvent('error'));
      await done;

      expect(store.isAuthenticated()).toBe(false);
    });
  });
});
