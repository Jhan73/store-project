import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { Category, Station } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import {
  chooseOption,
  filterOptions,
  selectedLabel,
  typeNumber,
} from '../../../testing/primeng-controls';
import { CatalogApi } from './catalog-api';
import { CategoryList } from './category-list';

const ADMIN = 'http://api.test/api/v1/admin';

const stations: Station[] = [
  { id: 's1', name: 'Main', defaultStation: true, etag: '"1"' },
  { id: 's2', name: 'Bar', defaultStation: false, etag: '"1"' },
];
const juices: Category = {
  id: 'c1',
  name: 'Juices',
  displayOrder: 1,
  stationId: 's1',
  active: true,
  etag: '"2"',
};
const snacks: Category = { ...juices, id: 'c2', name: 'Snacks', displayOrder: 2, stationId: 's2' };

async function render(categories: Category[] = [juices, snacks]) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      MessageService,
      CatalogApi,
      { provide: API_ORIGIN, useValue: 'http://api.test' },
    ],
  });
  const fixture = TestBed.createComponent(CategoryList);
  const http = TestBed.inject(HttpTestingController);
  await fixture.whenStable();
  http.expectOne(`${ADMIN}/categories`).flush(categories);
  http.expectOne(`${ADMIN}/stations`).flush(stations);
  await fixture.whenStable();
  return { fixture, host: fixture.nativeElement as HTMLElement, http };
}

function type(host: HTMLElement, selector: string, value: string) {
  const input = host.querySelector<HTMLInputElement>(selector)!;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

function submit(host: HTMLElement) {
  host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
}

function rows(host: HTMLElement) {
  return Array.from(host.querySelectorAll('tbody tr')).map((row) => row.textContent ?? '');
}

describe('CategoryList', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('lists the categories with their station and state', async () => {
    const { host } = await render([juices, { ...snacks, active: false }]);

    const [first, second] = rows(host);
    expect(first).toContain('Juices');
    expect(first).toContain('Main');
    expect(first).toContain('Activa');
    expect(second).toContain('Snacks');
    expect(second).toContain('Bar');
    expect(second).toContain('Inactiva');
  });

  it('creates a category on the default station when none is chosen', async () => {
    const { fixture, host, http } = await render([]);

    type(host, '#category-name', 'Salads');
    typeNumber(host, 'category-order', '3');
    submit(host);
    const request = http.expectOne(`${ADMIN}/categories`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Salads', displayOrder: 3 });
    request.flush({ ...juices, id: 'c3', name: 'Salads', displayOrder: 3 });
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(1);
    expect(rows(host)[0]).toContain('Salads');
  });

  it('creates a category on the chosen station', async () => {
    const { fixture, host, http } = await render([]);

    expect(selectedLabel(host, 'category-station')).toBe('Estación predeterminada');
    type(host, '#category-name', 'Shots');
    typeNumber(host, 'category-order', '1');
    await chooseOption(fixture, host, 'category-station', 'Bar');
    submit(host);

    expect(http.expectOne(`${ADMIN}/categories`).request.body).toEqual({
      name: 'Shots',
      displayOrder: 1,
      stationId: 's2',
    });
  });

  it('searches the stations before choosing one', async () => {
    const { fixture, host, http } = await render([]);

    type(host, '#category-name', 'Shots');
    typeNumber(host, 'category-order', '1');
    expect(await filterOptions(fixture, host, 'category-station', 'ba')).toEqual(['Bar']);
    await chooseOption(fixture, host, 'category-station', 'Bar');
    submit(host);

    expect(http.expectOne(`${ADMIN}/categories`).request.body.stationId).toBe('s2');
  });

  it('edits a category with the version it was listed with', async () => {
    const { fixture, host, http } = await render();

    host.querySelector<HTMLButtonElement>('[data-testid="edit-c2"]')!.click();
    await fixture.whenStable();
    expect(host.querySelector<HTMLInputElement>('#category-name')!.value).toBe('Snacks');
    expect(selectedLabel(host, 'category-station')).toBe('Bar');
    expect(host.querySelector('p-select .p-select-clear-icon')).toBeNull();
    type(host, '#category-name', 'Bites');
    submit(host);
    const request = http.expectOne(`${ADMIN}/categories/c2`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.headers.get('If-Match')).toBe('"2"');
    expect(request.request.body).toEqual({ name: 'Bites', displayOrder: 2, stationId: 's2' });
    request.flush({ ...snacks, name: 'Bites', etag: '"3"' });
    await fixture.whenStable();

    expect(rows(host)[1]).toContain('Bites');
  });

  it('deactivates an active category and reactivates an inactive one', async () => {
    const { fixture, host, http } = await render([juices, { ...snacks, active: false }]);

    host.querySelector<HTMLButtonElement>('[data-testid="toggle-c1"]')!.click();
    const deactivate = http.expectOne(`${ADMIN}/categories/c1/deactivate`);
    expect(deactivate.request.headers.get('If-Match')).toBe('"2"');
    deactivate.flush({ ...juices, active: false, etag: '"3"' });
    await fixture.whenStable();
    expect(rows(host)[0]).toContain('Inactiva');

    host.querySelector<HTMLButtonElement>('[data-testid="toggle-c2"]')!.click();
    const reactivate = http.expectOne(`${ADMIN}/categories/c2/reactivate`);
    expect(reactivate.request.headers.get('If-Match')).toBe('"2"');
    reactivate.flush({ ...snacks, active: true, etag: '"3"' });
    await fixture.whenStable();
    expect(rows(host)[1]).toContain('Activa');
  });

  it('reloads the list when the category changed in the meantime', async () => {
    const { fixture, host, http } = await render();

    host.querySelector<HTMLButtonElement>('[data-testid="toggle-c1"]')!.click();
    http.expectOne(`${ADMIN}/categories/c1/deactivate`).flush(
      { type: 'about:blank', status: 412, code: 'common.precondition-failed', correlationId: 'c' },
      { status: 412, statusText: 'Precondition Failed' },
    );
    await fixture.whenStable();
    http.expectOne(`${ADMIN}/categories`).flush([{ ...juices, name: 'Fresh juices', etag: '"9"' }]);
    await fixture.whenStable();

    expect(rows(host)[0]).toContain('Fresh juices');
  });

  const stale = {
    type: 'about:blank',
    status: 412,
    code: 'common.precondition-failed',
    correlationId: 'c',
  };

  it('retries a stale save with the refreshed version and shows the current values', async () => {
    const { fixture, host, http } = await render();

    host.querySelector<HTMLButtonElement>('[data-testid="edit-c1"]')!.click();
    await fixture.whenStable();
    type(host, '#category-name', 'Mine');
    submit(host);
    http
      .expectOne(`${ADMIN}/categories/c1`)
      .flush(stale, { status: 412, statusText: 'Precondition Failed' });
    await fixture.whenStable();
    http
      .expectOne(`${ADMIN}/categories`)
      .flush([{ ...juices, name: 'Fresh juices', etag: '"9"' }, snacks]);
    await fixture.whenStable();

    expect(host.querySelector<HTMLInputElement>('#category-name')!.value).toBe('Fresh juices');
    submit(host);
    const retry = http.expectOne(`${ADMIN}/categories/c1`);
    expect(retry.request.headers.get('If-Match')).toBe('"9"');
    retry.flush({ ...juices, name: 'Fresh juices', etag: '"10"' });
  });

  it('leaves edit mode when the category no longer exists after a failed save', async () => {
    const { fixture, host, http } = await render();

    host.querySelector<HTMLButtonElement>('[data-testid="edit-c1"]')!.click();
    await fixture.whenStable();
    submit(host);
    http.expectOne(`${ADMIN}/categories/c1`).flush(
      { type: 'about:blank', status: 404, code: 'common.not-found', correlationId: 'c' },
      { status: 404, statusText: 'Not Found' },
    );
    await fixture.whenStable();
    http.expectOne(`${ADMIN}/categories`).flush([snacks]);
    await fixture.whenStable();

    expect(host.querySelector<HTMLInputElement>('#category-name')!.value).toBe('');
    expect(host.querySelector('form [type="button"]')).toBeNull();
  });

  it('saves with the version returned by a toggle of the category being edited', async () => {
    const { fixture, host, http } = await render();

    host.querySelector<HTMLButtonElement>('[data-testid="edit-c1"]')!.click();
    await fixture.whenStable();
    host.querySelector<HTMLButtonElement>('[data-testid="toggle-c1"]')!.click();
    http
      .expectOne(`${ADMIN}/categories/c1/deactivate`)
      .flush({ ...juices, active: false, etag: '"7"' });
    await fixture.whenStable();
    submit(host);

    expect(http.expectOne(`${ADMIN}/categories/c1`).request.headers.get('If-Match')).toBe('"7"');
  });
});
