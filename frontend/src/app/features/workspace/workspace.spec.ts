import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { routes } from '../../app.routes';
import { API_ORIGIN } from '../../core/api/api-config';
import { authInterceptor } from '../../core/auth/auth-interceptor';
import { AuthStore } from '../../core/auth/auth-store';
import { errorInterceptor } from '../../core/errors/error-interceptor';
import { AppPreset } from '../../core/theme/app-preset';

const LOGIN = 'http://api.test/api/v1/auth/login';
const REFRESH = 'http://api.test/api/v1/auth/refresh';
const LOGOUT = 'http://api.test/api/v1/auth/logout';

describe('staff and admin area', () => {
  let http: HttpTestingController;
  let harness: RouterTestingHarness;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(routes),
        provideHttpClient(withInterceptors([errorInterceptor, authInterceptor])),
        provideHttpClientTesting(),
        MessageService,
        providePrimeNG({ theme: { preset: AppPreset } }),
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    harness = await RouterTestingHarness.create();
  });

  afterEach(() => http.verify());

  async function signInAs(role: 'SERVER' | 'CASHIER' | 'ADMIN') {
    const login = TestBed.inject(AuthStore).login({ email: 'a@b.pe', password: 'x' });
    http.expectOne(LOGIN).flush({ accessToken: 'jwt', tokenType: 'Bearer', userId: 'u', role });
    await login;
  }

  const page = () => (harness.routeNativeElement as HTMLElement).ownerDocument;
  const url = () => TestBed.inject(Router).url;
  const navLinks = () =>
    Array.from(page().querySelectorAll('nav a')).map((link) => link.getAttribute('href'));

  it('sends a visitor without a session to login and remembers the destination', async () => {
    const navigation = harness.navigateByUrl('/staff');
    (await vi.waitFor(() => http.expectOne(REFRESH))).flush(null, {
      status: 401,
      statusText: 'Unauthorized',
    });
    await navigation;

    expect(url()).toBe('/login?returnUrl=%2Fstaff');
    expect(page().body.textContent).toContain('Iniciar sesión');
  }, 20_000);

  it('restores the session from the refresh cookie on a reload', async () => {
    const navigation = harness.navigateByUrl('/staff');
    (await vi.waitFor(() => http.expectOne(REFRESH))).flush({
      accessToken: 'jwt',
      tokenType: 'Bearer',
      userId: 'u',
      role: 'CASHIER',
    });
    await navigation;

    expect(url()).toBe('/staff');
    expect(TestBed.inject(AuthStore).role()).toBe('CASHIER');
  });

  it('shows floor staff their area without the admin entry', async () => {
    await signInAs('SERVER');

    await harness.navigateByUrl('/staff');

    expect(navLinks()).toEqual(['/staff']);
    expect(page().body.textContent).toContain('Mozo');
  });

  it('keeps floor staff out of the admin area', async () => {
    await signInAs('CASHIER');

    await harness.navigateByUrl('/admin');

    expect(url()).toBe('/forbidden');
    expect(page().body.textContent).toContain('No tienes permiso');
  });

  it('gives an administrator both areas', async () => {
    await signInAs('ADMIN');

    await harness.navigateByUrl('/admin');

    expect(navLinks()).toEqual(['/staff', '/admin', '/admin/catalog', '/admin/settings']);
    expect(url()).toBe('/admin');
  });

  it('keeps floor staff out of the store settings', async () => {
    await signInAs('CASHIER');

    await harness.navigateByUrl('/admin/settings/general');

    expect(url()).toBe('/forbidden');
  });

  it('opens the store settings on the general settings with their own navigation', async () => {
    await signInAs('ADMIN');

    await harness.navigateByUrl('/admin/settings');
    http.expectOne('http://api.test/api/v1/admin/settings').flush(
      {
        currency: 'PEN',
        timeZone: 'America/Lima',
        registerDifferenceThreshold: { amount: '5.00', currency: 'PEN' },
      },
      { headers: { ETag: '"1"' } },
    );
    await harness.fixture.whenStable();

    const subNav = Array.from(page().querySelectorAll('nav[aria-label="Configuración"] a')).map(
      (link) => link.getAttribute('href'),
    );
    expect(subNav).toEqual([
      '/admin/settings/general',
      '/admin/settings/hours',
      '/admin/settings/zones',
      '/admin/settings/reasons',
    ]);
    expect(url()).toBe('/admin/settings/general');
    expect(page().body.textContent).toContain('Configuración de la tienda');
  });

  it('keeps floor staff out of the catalog', async () => {
    await signInAs('CASHIER');

    await harness.navigateByUrl('/admin/catalog/stations');

    expect(url()).toBe('/forbidden');
  });

  it('gives an administrator the catalog with its own navigation', async () => {
    await signInAs('ADMIN');

    await harness.navigateByUrl('/admin/catalog/stations');
    http.expectOne('http://api.test/api/v1/admin/stations').flush([]);
    await harness.fixture.whenStable();

    const subNav = Array.from(page().querySelectorAll('nav[aria-label="Catálogo"] a')).map((link) =>
      link.getAttribute('href'),
    );
    expect(subNav).toEqual([
      '/admin/catalog/products',
      '/admin/catalog/categories',
      '/admin/catalog/modifier-groups',
      '/admin/catalog/stations',
    ]);
    expect(page().body.textContent).toContain('Estaciones');
  });

  it('opens the catalog on its products', async () => {
    await signInAs('ADMIN');

    await harness.navigateByUrl('/admin/catalog');
    http.expectOne('http://api.test/api/v1/admin/categories').flush([]);
    http
      .expectOne('http://api.test/api/v1/admin/products?page=0&size=20')
      .flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    await harness.fixture.whenStable();

    expect(url()).toBe('/admin/catalog/products');
  });

  it('signs out and returns to login', async () => {
    await signInAs('ADMIN');
    await harness.navigateByUrl('/admin');

    page().querySelector<HTMLButtonElement>('button[data-testid="logout"]')!.click();
    http.expectOne(LOGOUT).flush(null, { status: 204, statusText: 'No Content' });
    await harness.fixture.whenStable();

    expect(TestBed.inject(AuthStore).isAuthenticated()).toBe(false);
    expect(url()).toBe('/login');
  });
});
