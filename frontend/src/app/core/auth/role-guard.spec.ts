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
    vi.spyOn(router, 'getCurrentNavigation').mockReturnValue({
      extractedUrl: router.parseUrl('/staff/tickets'),
    } as ReturnType<Router['getCurrentNavigation']>);
    const result = run('ADMIN');

    http.expectOne(REFRESH).flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(router.serializeUrl((await result) as UrlTree)).toBe(
      '/login?returnUrl=%2Fstaff%2Ftickets',
    );
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
