import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { Category, Product, ProductPage } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { ConfirmAction } from '../../../shared/state/confirm-action';
import { UndoNotice } from '../../../shared/state/undo-notice';
import { chooseOption, filterOptions, selectedLabel } from '../../../testing/primeng-controls';
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
  deletable: false,
  etag: '"2"',
  ...overrides,
});

const categories: Category[] = [
  { id: 'c1', name: 'Juices', displayOrder: 1, stationId: 's1', active: true, deletable: false, etag: '"1"' },
  { id: 'c2', name: 'Snacks', displayOrder: 2, stationId: 's1', active: true, deletable: false, etag: '"1"' },
];

const ask = vi.fn(() => Promise.resolve(true));

const page = (content: Product[], number = 0, totalPages = 1): ProductPage => ({
  content,
  page: number,
  size: 100,
  totalElements: content.length,
  totalPages,
});

function setup() {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      MessageService,
      CatalogApi,
      { provide: ConfirmAction, useValue: { ask } },
      { provide: API_ORIGIN, useValue: 'http://api.test' },
    ],
  });
  const fixture = TestBed.createComponent(ProductList);
  const http = TestBed.inject(HttpTestingController);
  return { fixture, http };
}

async function render(first: ProductPage = page([product('p1', 'Orange juice'), product('p2', 'Beet shot')])) {
  const { fixture, http } = setup();
  await fixture.whenStable();
  http.expectOne(`${API}/admin/categories`).flush(categories);
  http.expectOne(`${API}/admin/products?page=0&size=100`).flush(first);
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
const switchOf = (host: HTMLElement, testId: string) =>
  host.querySelector<HTMLInputElement>(`p-toggleswitch[data-testid="${testId}"] input`)!;
const mixed = () => page([product('p1', 'Orange juice'), product('p3', 'Chips', { categoryId: 'c2' })]);

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

  it('filters by category without asking the server again', async () => {
    const { fixture, host } = await render(mixed());

    expect(selectedLabel(host, 'product-category-filter')).toBe('Todas');
    await chooseOption(fixture, host, 'product-category-filter', 'Snacks');

    expect(rows(host)).toHaveLength(1);
    expect(rows(host)[0]).toContain('Chips');
    expect(selectedLabel(host, 'product-category-filter')).toBe('Snacks');
  });

  it('searches the categories before filtering by one', async () => {
    const { fixture, host } = await render(mixed());

    expect(await filterOptions(fixture, host, 'product-category-filter', 'snack')).toEqual(['Snacks']);
    await chooseOption(fixture, host, 'product-category-filter', 'Snacks');

    expect(rows(host)).toHaveLength(1);
  });

  it('clears the category filter to list every category again', async () => {
    const { fixture, host } = await render(mixed());
    await chooseOption(fixture, host, 'product-category-filter', 'Snacks');

    host.querySelector<SVGElement>('p-select .p-select-clear-icon')!.dispatchEvent(new Event('click'));
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(2);
    expect(selectedLabel(host, 'product-category-filter')).toBe('Todas');
  });

  it('reads every page of the catalog', async () => {
    const { fixture, http } = setup();
    await fixture.whenStable();
    http.expectOne(`${API}/admin/categories`).flush(categories);
    http
      .expectOne(`${API}/admin/products?page=0&size=100`)
      .flush(page([product('p1', 'Orange juice')], 0, 2));
    await fixture.whenStable();
    http
      .expectOne(`${API}/admin/products?page=1&size=100`)
      .flush(page([product('p9', 'Zesty shot')], 1, 2));
    await fixture.whenStable();

    expect(rows(fixture.nativeElement as HTMLElement)).toHaveLength(2);
  });

  it('pages the rows in the table', async () => {
    const many = Array.from({ length: 25 }, (_, n) => product(`p${n}`, `Juice ${n}`));
    const { fixture, host } = await render(page(many));

    expect(rows(host)).toHaveLength(20);
    host.querySelector<HTMLElement>('.p-paginator-next')!.click();
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(5);
  });

  it('marks a product sold out right away without saving the product', async () => {
    const { fixture, host, http } = await render();

    switchOf(host, 'available-p1').click();
    const request = http.expectOne(`${API}/catalog/products/p1/availability`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ available: false });
    request.flush({ id: 'p1', available: false });
    await fixture.whenStable();

    expect(switchOf(host, 'available-p1').checked).toBe(false);
  });

  it('puts the availability switch back when the change fails', async () => {
    const { fixture, host, http, messages } = await render();
    const add = vi.spyOn(messages, 'add');

    switchOf(host, 'available-p1').click();
    await fixture.whenStable();
    http.expectOne(`${API}/catalog/products/p1/availability`).flush(
      { type: 'about:blank', status: 404, code: 'catalog.product-not-found', correlationId: 'c' },
      { status: 404, statusText: 'Not Found' },
    );
    await fixture.whenStable();

    expect(add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
    expect(switchOf(host, 'available-p1').checked).toBe(true);
  });

  it('blocks the availability switch while its change is being saved', async () => {
    const { fixture, host, http } = await render();
    const box = () => switchOf(host, 'available-p1');

    box().click();
    await fixture.whenStable();
    expect(box().disabled).toBe(true);
    box().click();
    http.expectOne(`${API}/catalog/products/p1/availability`).flush({ id: 'p1', available: false });
    await fixture.whenStable();

    expect(box().disabled).toBe(false);
    expect(box().checked).toBe(false);
  });

  it('shows a deactivated product as inactive at once and sends the change after the undo window', async () => {
    const { fixture, host, http } = await render();
    vi.useFakeTimers();

    click(host, 'toggle-p1');
    vi.advanceTimersByTime(5_000);
    vi.useRealTimers();
    const deactivate = http.expectOne(`${API}/admin/products/p1/deactivate`);
    expect(deactivate.request.headers.get('If-Match')).toBe('"2"');
    deactivate.flush(product('p1', 'Orange juice', { active: false, etag: '"3"' }));
    await fixture.whenStable();

    expect(rows(host)[0]).toContain('Inactivo');
  });

  it('undoing a deactivation sends nothing and keeps the product active', async () => {
    const { fixture, host } = await render();
    const notice = TestBed.inject(UndoNotice);

    click(host, 'toggle-p1');
    await fixture.whenStable();
    expect(rows(host)[0]).toContain('Inactivo');
    expect(notice.active()).toHaveLength(1);
    notice.undo(notice.active()[0]);
    await fixture.whenStable();

    expect(rows(host)[0]).not.toContain('Inactivo');
  });

  it('reactivates an inactive product straight away with its version', async () => {
    const { fixture, host, http } = await render(
      page([product('p2', 'Beet shot', { active: false, etag: '"5"' })]),
    );

    click(host, 'toggle-p2');
    const reactivate = http.expectOne(`${API}/admin/products/p2/reactivate`);
    expect(reactivate.request.headers.get('If-Match')).toBe('"5"');
    reactivate.flush(product('p2', 'Beet shot', { etag: '"6"' }));
    await fixture.whenStable();

    expect(rows(host)[0]).not.toContain('Inactivo');
  });

  it('offers Delete instead of Deactivate for a product that was never used', async () => {
    const { host } = await render(
      page([product('p1', 'Orange juice', { deletable: true }), product('p2', 'Beet shot')]),
    );

    expect(host.querySelector('[data-testid="delete-p1"]')).toBeTruthy();
    expect(host.querySelector('[data-testid="toggle-p1"]')).toBeNull();
    expect(host.querySelector('[data-testid="delete-p2"]')).toBeNull();
    expect(host.querySelector('[data-testid="toggle-p2"]')).toBeTruthy();
  });

  it('disables Delete while the request is in flight so a double click sends one DELETE', async () => {
    ask.mockClear();
    const { fixture, host, http } = await render(page([product('p1', 'Orange juice', { deletable: true })]));
    const button = () => host.querySelector<HTMLButtonElement>('[data-testid="delete-p1"]')!;

    click(host, 'delete-p1');
    await fixture.whenStable();
    const request = http.expectOne(`${API}/admin/products/p1`);
    expect(button().disabled).toBe(true);
    click(host, 'delete-p1');
    await fixture.whenStable();
    http.expectNone(`${API}/admin/products/p1`);

    request.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();
    expect(host.querySelector('[data-testid="delete-p1"]')).toBeNull();
  });

  it('enables Delete again when the request fails', async () => {
    ask.mockClear();
    const { fixture, host, http } = await render(page([product('p1', 'Orange juice', { deletable: true })]));

    click(host, 'delete-p1');
    await fixture.whenStable();
    http.expectOne(`${API}/admin/products/p1`).flush(
      { type: 'about:blank', status: 500, code: 'common.internal-error', correlationId: 'c' },
      { status: 500, statusText: 'Server Error' },
    );
    await fixture.whenStable();

    expect(host.querySelector<HTMLButtonElement>('[data-testid="delete-p1"]')!.disabled).toBe(false);
  });

  it('asks before deleting, sends the version and drops the row', async () => {
    ask.mockClear();
    const { fixture, host, http } = await render(
      page([product('p1', 'Orange juice', { deletable: true }), product('p2', 'Beet shot')]),
    );

    click(host, 'delete-p1');
    await fixture.whenStable();
    expect(ask).toHaveBeenCalledWith(expect.objectContaining({ message: expect.stringContaining('Orange juice') }));
    const request = http.expectOne(`${API}/admin/products/p1`);
    expect(request.request.method).toBe('DELETE');
    expect(request.request.headers.get('If-Match')).toBe('"2"');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(1);
    expect(rows(host)[0]).toContain('Beet shot');
  });

  it('sends nothing when the deletion is declined', async () => {
    ask.mockResolvedValueOnce(false);
    const { fixture, host, http } = await render(page([product('p1', 'Orange juice', { deletable: true })]));

    click(host, 'delete-p1');
    await fixture.whenStable();

    http.expectNone(`${API}/admin/products/p1`);
  });

  it('reloads the list when the product was sold since it was listed', async () => {
    const { fixture, host, http } = await render(page([product('p1', 'Orange juice', { deletable: true })]));

    click(host, 'delete-p1');
    await fixture.whenStable();
    http.expectOne(`${API}/admin/products/p1`).flush(
      { type: 'about:blank', status: 409, code: 'catalog.product-in-use', correlationId: 'c' },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();
    http
      .expectOne(`${API}/admin/products?page=0&size=100`)
      .flush(page([product('p1', 'Orange juice', { deletable: false })]));
    await fixture.whenStable();

    expect(host.querySelector('[data-testid="toggle-p1"]')).toBeTruthy();
    expect(host.querySelector('[data-testid="delete-p1"]')).toBeNull();
  });

  it('reloads the list when a product changed in the meantime', async () => {
    const { fixture, host, http } = await render();
    vi.useFakeTimers();

    click(host, 'toggle-p1');
    vi.advanceTimersByTime(5_000);
    vi.useRealTimers();
    http.expectOne(`${API}/admin/products/p1/deactivate`).flush(
      { type: 'about:blank', status: 412, code: 'common.precondition-failed', correlationId: 'c' },
      { status: 412, statusText: 'Precondition Failed' },
    );
    await fixture.whenStable();
    http
      .expectOne(`${API}/admin/products?page=0&size=100`)
      .flush(page([product('p1', 'Fresh orange juice', { etag: '"9"' })]));
    await fixture.whenStable();

    expect(rows(host)[0]).toContain('Fresh orange juice');
    expect(rows(host)[0]).not.toContain('Inactivo');
  });
});
