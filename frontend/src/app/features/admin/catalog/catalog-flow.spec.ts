import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { routes } from '../../../app.routes';
import { API_ORIGIN } from '../../../core/api/api-config';
import { authInterceptor } from '../../../core/auth/auth-interceptor';
import { AuthStore } from '../../../core/auth/auth-store';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { AppPreset } from '../../../core/theme/app-preset';
import { chooseOption } from '../../../testing/primeng-controls';

const API = 'http://api.test/api/v1';

const sizeGroup = {
  id: 'g1',
  name: 'Size',
  required: true,
  minChoices: 1,
  maxChoices: 1,
  etag: '"1"',
  options: [
    {
      id: 'o1',
      name: 'Large',
      priceDelta: { amount: '2.00', currency: 'PEN' },
      allergens: [],
      available: true,
    },
  ],
};
const juices = {
  id: 'c1',
  name: 'Juices',
  displayOrder: 1,
  stationId: 's1',
  active: true,
  etag: '"1"',
};
const orange = {
  id: 'p1',
  name: 'Orange juice',
  description: null,
  categoryId: 'c1',
  price: { amount: '9.50', currency: 'PEN' },
  displayOrder: 1,
  quickSalePinned: false,
  allergens: [],
  modifierGroupIds: ['g1'],
  imageUrl: null,
  active: true,
  available: true,
  etag: '"1"',
};

describe('admin catalog flow', () => {
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
    const login = TestBed.inject(AuthStore).login({ email: 'admin@b.pe', password: 'x' });
    http
      .expectOne(`${API}/auth/login`)
      .flush({ accessToken: 'jwt', tokenType: 'Bearer', userId: 'u', role: 'ADMIN' });
    await login;
  });

  afterEach(() => http.verify());

  const page = () => harness.routeNativeElement as HTMLElement;
  const settle = async () => {
    await new Promise((resolve) => setTimeout(resolve));
    await harness.fixture.whenStable();
  };

  function type(selector: string, value: string) {
    const input = page().querySelector<HTMLInputElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  }

  const choose = (id: string, label: string) =>
    chooseOption(harness.fixture, page(), id, label);

  it('lets an administrator create a modifier group and then a product that uses it', async () => {
    const router = TestBed.inject(Router);

    await harness.navigateByUrl('/admin/catalog/modifier-groups/new');
    http.expectOne(`${API}/admin/settings`).flush({ currency: 'PEN' });
    http.expectOne(`${API}/admin/allergens`).flush(['MILK']);
    await settle();
    type('#group-name', 'Size');
    page().querySelector<HTMLInputElement>('p-checkbox #group-required')!.click();
    type('#option-0-name', 'Large');
    type('#option-0-price', '2');
    page().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    const createGroup = http.expectOne(`${API}/admin/modifier-groups`);
    expect(createGroup.request.method).toBe('POST');
    createGroup.flush(sizeGroup, { status: 201, statusText: 'Created' });
    await settle();
    http.expectOne(`${API}/admin/modifier-groups`).flush([sizeGroup]);
    await settle();
    expect(router.url).toBe('/admin/catalog/modifier-groups');
    expect(page().textContent).toContain('Size');

    await harness.navigateByUrl('/admin/catalog/products/new');
    http.expectOne(`${API}/admin/settings`).flush({ currency: 'PEN' });
    http.expectOne(`${API}/admin/allergens`).flush(['MILK']);
    http.expectOne(`${API}/admin/categories`).flush([juices]);
    http.expectOne(`${API}/admin/modifier-groups`).flush([sizeGroup]);
    await settle();
    type('#product-name', 'Orange juice');
    await choose('product-category', 'Juices');
    type('#product-price', '9.50');
    await choose('product-group-add', 'Size');
    page().querySelector<HTMLButtonElement>('[data-testid="add-group"]')!.click();
    await settle();
    page().querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    const createProduct = http.expectOne(`${API}/admin/products`);
    expect(createProduct.request.body).toMatchObject({
      name: 'Orange juice',
      categoryId: 'c1',
      price: { amount: '9.50', currency: 'PEN' },
      modifierGroupIds: ['g1'],
    });
    createProduct.flush(orange, { status: 201, statusText: 'Created' });
    await settle();

    http.expectOne(`${API}/admin/settings`).flush({ currency: 'PEN' });
    http.expectOne(`${API}/admin/allergens`).flush(['MILK']);
    http.expectOne(`${API}/admin/categories`).flush([juices]);
    http.expectOne(`${API}/admin/modifier-groups`).flush([sizeGroup]);
    http.expectOne(`${API}/admin/products/p1`).flush(orange);
    await settle();
    expect(router.url).toBe('/admin/catalog/products/p1');
    expect(page().querySelector<HTMLInputElement>('#product-name')!.value).toBe('Orange juice');
    expect(page().textContent).toContain('Size');
  }, 40_000);
});
