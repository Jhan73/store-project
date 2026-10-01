import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { API_ORIGIN } from '../api/api-config';
import { authInterceptor } from './auth-interceptor';
import { AuthStore } from './auth-store';

const ORIGIN = 'http://api.test';
const DATA = `${ORIGIN}/api/v1/tables`;
const OTHER = `${ORIGIN}/api/v1/staff`;
const REFRESH = `${ORIGIN}/api/v1/auth/refresh`;
const LOGIN = `${ORIGIN}/api/v1/auth/login`;
const UNAUTHORIZED = { status: 401, statusText: 'Unauthorized' };

function session(accessToken: string) {
  return { accessToken, tokenType: 'Bearer', userId: 'u1', role: 'CASHIER' };
}

describe('authInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;
  let store: AuthStore;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
        { provide: API_ORIGIN, useValue: ORIGIN },
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    store = TestBed.inject(AuthStore);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    const login = store.login({ email: 'a@b.pe', password: 'x' });
    http.expectOne(LOGIN).flush(session('old'));
    await login;
  });

  afterEach(() => http.verify());

  it('attaches the bearer token to API requests', () => {
    client.get(DATA).subscribe();

    const request = http.expectOne(DATA);
    expect(request.request.headers.get('Authorization')).toBe('Bearer old');
    request.flush([]);
  });

  it('never attaches the token to another origin', () => {
    client.get('https://cdn.example.com/menu.json').subscribe();

    const request = http.expectOne('https://cdn.example.com/menu.json');
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush({});
  });

  it('does not attach a token to the auth endpoints', () => {
    client.post(REFRESH, null).subscribe();

    const request = http.expectOne(REFRESH);
    expect(request.request.headers.has('Authorization')).toBe(false);
    request.flush(session('x'));
  });

  it('refreshes once on 401 and retries the request with the new token', async () => {
    const result = firstValueFrom(client.get(DATA));

    http.expectOne(DATA).flush(null, UNAUTHORIZED);
    http.expectOne(REFRESH).flush(session('new'));
    const retry = await vi.waitFor(() => http.expectOne(DATA));
    expect(retry.request.headers.get('Authorization')).toBe('Bearer new');
    retry.flush(['table']);

    expect(await result).toEqual(['table']);
  });

  it('shares a single refresh among concurrent 401s', async () => {
    const first = firstValueFrom(client.get(DATA));
    const second = firstValueFrom(client.get(OTHER));

    http.expectOne(DATA).flush(null, UNAUTHORIZED);
    http.expectOne(OTHER).flush(null, UNAUTHORIZED);
    http.expectOne(REFRESH).flush(session('new'));
    (await vi.waitFor(() => http.expectOne(DATA))).flush('a');
    (await vi.waitFor(() => http.expectOne(OTHER))).flush('b');

    expect(await Promise.all([first, second])).toEqual(['a', 'b']);
  });

  it('reuses a token another request already renewed instead of refreshing again', async () => {
    const first = firstValueFrom(client.get(DATA));
    const second = firstValueFrom(client.get(OTHER));
    const firstRequest = http.expectOne(DATA);
    const secondRequest = http.expectOne(OTHER);

    firstRequest.flush(null, UNAUTHORIZED);
    http.expectOne(REFRESH).flush(session('new'));
    (await vi.waitFor(() => http.expectOne(DATA))).flush('a');
    secondRequest.flush(null, UNAUTHORIZED);

    const retry = await vi.waitFor(() => http.expectOne(OTHER));
    expect(retry.request.headers.get('Authorization')).toBe('Bearer new');
    retry.flush('b');
    expect(await Promise.all([first, second])).toEqual(['a', 'b']);
  });

  it('retries only once when the retried request is also rejected', async () => {
    const result = firstValueFrom(client.get(DATA));

    http.expectOne(DATA).flush(null, UNAUTHORIZED);
    http.expectOne(REFRESH).flush(session('new'));
    (await vi.waitFor(() => http.expectOne(DATA))).flush(null, UNAUTHORIZED);

    await expect(result).rejects.toMatchObject({ status: 401 });
    http.expectNone(REFRESH);
  });

  it('clears the session and goes to login when the refresh fails', async () => {
    const result = firstValueFrom(client.get(DATA));

    http.expectOne(DATA).flush(null, UNAUTHORIZED);
    http.expectOne(REFRESH).flush(null, UNAUTHORIZED);

    await expect(result).rejects.toMatchObject({ status: 401 });
    expect(store.isAuthenticated()).toBe(false);
    expect(navigate).toHaveBeenCalledWith(['/login'], expect.anything());
  });

  it('never retries or refreshes on a 401 from an auth endpoint', async () => {
    const result = firstValueFrom(client.post(LOGIN, {}));

    http.expectOne(LOGIN).flush(null, UNAUTHORIZED);

    await expect(result).rejects.toMatchObject({ status: 401 });
    http.expectNone(REFRESH);
  });

  it('does not refresh on 403', async () => {
    const result = firstValueFrom(client.get(DATA));

    http.expectOne(DATA).flush(null, { status: 403, statusText: 'Forbidden' });

    await expect(result).rejects.toMatchObject({ status: 403 });
    http.expectNone(REFRESH);
    expect(store.isAuthenticated()).toBe(true);
  });

  it('does not refresh requests to other origins', async () => {
    const result = firstValueFrom(client.get('https://cdn.example.com/x'));

    http.expectOne('https://cdn.example.com/x').flush(null, UNAUTHORIZED);

    await expect(result).rejects.toMatchObject({ status: 401 });
    http.expectNone(REFRESH);
  });
});
