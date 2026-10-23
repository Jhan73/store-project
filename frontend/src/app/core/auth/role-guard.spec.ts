import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { CanMatchFn, Route, Router, UrlSegment, UrlTree, provideRouter } from '@angular/router';
import { API_ORIGIN } from '../api/api-config';
import { AuthStore } from './auth-store';
import { roleGuard } from './role-guard';

const REFRESH = 'http://api.test/api/v1/auth/refresh';

function session(role: 'SERVER' | 'CASHIER' | 'ADMIN') {
  return { accessToken: 'jwt', tokenType: 'Bearer', userId: 'u', role };
}

describe('roleGuard', () => {
  let http: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => http.verify());

  function run(...roles: Parameters<typeof roleGuard>) {
    return TestBed.runInInjectionContext(() =>
      roleGuard(...roles)(
        {} as Route,
        [new UrlSegment('staff', {})],
        {} as Parameters<CanMatchFn>[2],
      ),
    ) as Promise<boolean | UrlTree>;
  }

  it('restores the session first and lets an allowed role through', async () => {
    const result = run('SERVER', 'CASHIER', 'ADMIN');

    http.expectOne(REFRESH).flush(session('CASHIER'));

    expect(await result).toBe(true);
  });

  it('sends anonymous visitors to login, remembering where they were going', async () => {
    vi.spyOn(router, 'currentNavigation').mockReturnValue({
      extractedUrl: router.parseUrl('/staff/tickets'),
    } as ReturnType<Router['currentNavigation']>);
    const result = run('ADMIN');

    http.expectOne(REFRESH).flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(router.serializeUrl((await result) as UrlTree)).toBe(
      '/login?returnUrl=%2Fstaff%2Ftickets',
    );
  });

  it.each([
    ['a 503 while the environment starts', { status: 503, statusText: 'Service Unavailable' }],
    ['a gateway error', { status: 502, statusText: 'Bad Gateway' }],
  ])('keeps the destination on an auth outage (%s) instead of sending the user to login', async (_label, init) => {
    vi.spyOn(router, 'currentNavigation').mockReturnValue({
      extractedUrl: router.parseUrl('/staff/tickets/k1'),
    } as ReturnType<Router['currentNavigation']>);
    const result = run('SERVER', 'CASHIER', 'ADMIN');

    http.expectOne(REFRESH).flush(null, init);

    expect(router.serializeUrl((await result) as UrlTree)).toBe(
      '/service-unavailable?returnUrl=%2Fstaff%2Ftickets%2Fk1',
    );
  });

  it('keeps the destination on a network failure', async () => {
    const result = run('ADMIN');

    http.expectOne(REFRESH).error(new ProgressEvent('error'));

    expect(router.serializeUrl((await result) as UrlTree)).toMatch(/^\/service-unavailable/);
  });

  it('asks the server again on the next navigation after an outage', async () => {
    const first = run('ADMIN');
    http.expectOne(REFRESH).flush(null, { status: 503, statusText: 'Service Unavailable' });
    await first;

    const second = run('ADMIN');
    http.expectOne(REFRESH).flush(session('ADMIN'));

    expect(await second).toBe(true);
  });

  it('sends a signed-in user with the wrong role to the forbidden page', async () => {
    const result = run('ADMIN');

    http.expectOne(REFRESH).flush(session('SERVER'));

    expect(router.serializeUrl((await result) as UrlTree)).toBe('/forbidden');
  });

  it('does not call the server again once the session is known', async () => {
    const first = run('SERVER');
    http.expectOne(REFRESH).flush(session('SERVER'));
    await first;

    expect(await run('SERVER')).toBe(true);
    expect(TestBed.inject(AuthStore).role()).toBe('SERVER');
  });
});
