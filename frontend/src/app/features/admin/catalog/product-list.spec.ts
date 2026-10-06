import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { Category, Product, ProductPage } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { CatalogApi } from './catalog-api';
import { ProductList } from './product-list';

const API = 'http://api.test/api/v1';

const product = (id: string, name: string, overrides: Partial<Product> = {}): Product => ({
  id,
  name,
  description: null,
  categoryId: 'c1',
  price: { amount: '9.50', currency: 'PEN' },
  displayOrder: 1,
  quickSalePinned: false,
  allergens: [],
  modifierGroupIds: [],
  imageUrl: null,
  active: true,
  available: true,
  etag: '"2"',
  ...overrides,
});

const categories: Category[] = [
  { id: 'c1', name: 'Juices', displayOrder: 1, stationId: 's1', active: true, etag: '"1"' },
  { id: 'c2', name: 'Snacks', displayOrder: 2, stationId: 's1', active: true, etag: '"1"' },
];

const page = (content: Product[], number = 0, totalPages = 1): ProductPage => ({
  content,
  page: number,
  size: 20,
  totalElements: content.length,
  totalPages,
});

async function render(first: ProductPage = page([product('p1', 'Orange juice'), product('p2', 'Beet shot')])) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      MessageService,
      CatalogApi,
      { provide: API_ORIGIN, useValue: 'http://api.test' },
    ],
  });
  const fixture = TestBed.createComponent(ProductList);
  const http = TestBed.inject(HttpTestingController);
  await fixture.whenStable();
  http.expectOne(`${API}/admin/categories`).flush(categories);
  http.expectOne(`${API}/admin/products?page=0&size=20`).flush(first);
  await fixture.whenStable();
  return {
    fixture,
    host: fixture.nativeElement as HTMLElement,
    http,
    messages: TestBed.inject(MessageService),
  };
}

const rows = (host: HTMLElement) =>
  Array.from(host.querySelectorAll('tbody tr')).map((row) => row.textContent ?? '');
const click = (host: HTMLElement, testId: string) =>
  host.querySelector<HTMLElement>(`[data-testid="${testId}"]`)!.click();

describe('ProductList', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('lists the products with category, price and state, and links to create and edit', async () => {
    const { host } = await render(
      page([product('p1', 'Orange juice'), product('p2', 'Beet shot', { active: false, categoryId: 'c2' })]),
    );

    const [first, second] = rows(host);
    expect(first).toContain('Orange juice');
    expect(first).toContain('Juices');
    expect(first).toContain('9.50');
    expect(first).toContain('Activo');
    expect(second).toContain('Snacks');
    expect(second).toContain('Inactivo');
    expect(host.querySelector('a[href="/admin/catalog/products/new"]')).not.toBeNull();
    expect(host.querySelector('a[href="/admin/catalog/products/p1"]')).not.toBeNull();
  });

  it('says so when there are no products', async () => {
    const { host } = await render(page([]));

    expect(host.textContent).toContain('Todavía no hay productos');
  });

  it('shows the product image with its name as the alternative text', async () => {
    const { host } = await render(
      page([product('p1', 'Orange juice', { imageUrl: 'https://cdn.test/products/a.png' })]),
    );

    const image = host.querySelector('img')!;
    expect(image.getAttribute('src')).toBe('https://cdn.test/products/a.png');
    expect(image.getAttribute('alt')).toBe('Orange juice');
  });

  it('filters by category and goes back to the first page', async () => {
    const { fixture, host, http } = await render();

    const select = host.querySelector<HTMLSelectElement>('#product-category-filter')!;
    select.value = 'c2';
    select.dispatchEvent(new Event('change'));
    http
      .expectOne(`${API}/admin/products?categoryId=c2&page=0&size=20`)
      .flush(page([product('p3', 'Chips', { categoryId: 'c2' })]));
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(1);
    expect(rows(host)[0]).toContain('Chips');
  });

  it('keeps the latest filter when an earlier response arrives late', async () => {
    const { fixture, host, http } = await render();
    const select = host.querySelector<HTMLSelectElement>('#product-category-filter')!;

    select.value = 'c1';
    select.dispatchEvent(new Event('change'));
    const early = http.expectOne(`${API}/admin/products?categoryId=c1&page=0&size=20`);
    select.value = 'c2';
    select.dispatchEvent(new Event('change'));
    const late = http.expectOne(`${API}/admin/products?categoryId=c2&page=0&size=20`);

    late.flush(page([product('p3', 'Chips', { categoryId: 'c2' })]));
    early.flush(page([product('p1', 'Orange juice')]));
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(1);
    expect(rows(host)[0]).toContain('Chips');
  });

  it('moves between pages', async () => {
    const { fixture, host, http } = await render(page([product('p1', 'Orange juice')], 0, 2));
    expect(host.textContent).toContain('Página 1 de 2');

    click(host, 'next-page');
    http
      .expectOne(`${API}/admin/products?page=1&size=20`)
      .flush(page([product('p9', 'Zesty shot')], 1, 2));
    await fixture.whenStable();
    expect(host.textContent).toContain('Página 2 de 2');
    expect(rows(host)[0]).toContain('Zesty shot');
    expect(host.querySelector<HTMLButtonElement>('[data-testid="next-page"]')!.disabled).toBe(true);

    click(host, 'previous-page');
    http.expectOne(`${API}/admin/products?page=0&size=20`).flush(page([product('p1', 'Orange juice')], 0, 2));
    await fixture.whenStable();
    expect(rows(host)[0]).toContain('Orange juice');
  });

  it('marks a product sold out right away without saving the product', async () => {
    const { fixture, host, http } = await render();

    click(host, 'available-p1');
    const request = http.expectOne(`${API}/catalog/products/p1/availability`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ available: false });
    request.flush({ id: 'p1', available: false });
    await fixture.whenStable();

    expect(host.querySelector<HTMLInputElement>('[data-testid="available-p1"]')!.checked).toBe(false);
  });

  it('puts the availability switch back when the change fails', async () => {
    const { fixture, host, http, messages } = await render();
    const add = vi.spyOn(messages, 'add');

    click(host, 'available-p1');
    http.expectOne(`${API}/catalog/products/p1/availability`).flush(
      { type: 'about:blank', status: 404, code: 'catalog.product-not-found', correlationId: 'c' },
      { status: 404, statusText: 'Not Found' },
    );
    await fixture.whenStable();

    expect(add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
    expect(host.querySelector<HTMLInputElement>('[data-testid="available-p1"]')!.checked).toBe(true);
  });

  it('deactivates an active product and reactivates an inactive one with their versions', async () => {
    const { fixture, host, http } = await render(
      page([product('p1', 'Orange juice'), product('p2', 'Beet shot', { active: false, etag: '"5"' })]),
    );

    click(host, 'toggle-p1');
    const deactivate = http.expectOne(`${API}/admin/products/p1/deactivate`);
    expect(deactivate.request.headers.get('If-Match')).toBe('"2"');
    deactivate.flush(product('p1', 'Orange juice', { active: false, etag: '"3"' }));
    await fixture.whenStable();
    expect(rows(host)[0]).toContain('Inactivo');

    click(host, 'toggle-p2');
    const reactivate = http.expectOne(`${API}/admin/products/p2/reactivate`);
    expect(reactivate.request.headers.get('If-Match')).toBe('"5"');
    reactivate.flush(product('p2', 'Beet shot', { etag: '"6"' }));
    await fixture.whenStable();
    expect(rows(host)[1]).toContain('Activo');
  });

  it('reloads the current page when a product changed in the meantime', async () => {
    const { fixture, host, http } = await render();

    click(host, 'toggle-p1');
    http.expectOne(`${API}/admin/products/p1/deactivate`).flush(
      { type: 'about:blank', status: 412, code: 'common.precondition-failed', correlationId: 'c' },
      { status: 412, statusText: 'Precondition Failed' },
    );
    await fixture.whenStable();
    http
      .expectOne(`${API}/admin/products?page=0&size=20`)
      .flush(page([product('p1', 'Fresh orange juice', { etag: '"9"' })]));
    await fixture.whenStable();

    expect(rows(host)[0]).toContain('Fresh orange juice');
  });
});
