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
  optionLabels,
  selectedLabel,
  typeNumber,
} from '../../../testing/primeng-controls';
import { ConfirmAction } from '../../../shared/state/confirm-action';
import { CatalogApi } from './catalog-api';
import { CategoryList } from './category-list';

const ADMIN = 'http://api.test/api/v1/admin';

const stations: Station[] = [
  { id: 's1', name: 'Main', defaultStation: true, active: true, etag: '"1"' },
  { id: 's2', name: 'Bar', defaultStation: false, active: true, etag: '"1"' },
  { id: 's3', name: 'Retired', defaultStation: false, active: false, etag: '"1"' },
];
const juices: Category = {
  id: 'c1',
  name: 'Juices',
  displayOrder: 1,
  stationId: 's1',
  active: true,
  deletable: false,
  etag: '"2"',
};
const snacks: Category = { ...juices, id: 'c2', name: 'Snacks', displayOrder: 2, stationId: 's2' };

const ask = vi.fn(() => Promise.resolve(true));

async function openAdd(fixture: { whenStable: () => Promise<unknown> }, host: HTMLElement) {
  host.querySelector<HTMLButtonElement>('[data-testid="add-category"]')!.click();
  await fixture.whenStable();
}

async function render(categories: Category[] = [juices, snacks]) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      MessageService,
      CatalogApi,
      { provide: ConfirmAction, useValue: { ask } },
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

function type(selector: string, value: string) {
  const input = document.body.querySelector<HTMLInputElement>(selector)!;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

function submit() {
  document.body.querySelector<HTMLFormElement>('[role="dialog"] form')!.dispatchEvent(new Event('submit'));
}

function rows(host: HTMLElement) {
  return Array.from(host.querySelectorAll('tbody tr')).map((row) => row.textContent ?? '');
}

describe('CategoryList', () => {
  beforeEach(() => ask.mockClear());
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
    await openAdd(fixture, host);

    type('#category-name', 'Salads');
    typeNumber(document.body, 'category-order', '3');
    submit();
    const request = http.expectOne(`${ADMIN}/categories`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Salads', displayOrder: 3 });
    request.flush({ ...juices, id: 'c3', name: 'Salads', displayOrder: 3 });
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(1);
    expect(rows(host)[0]).toContain('Salads');
  });

  it('offers only active stations for a category', async () => {
    const { fixture, host } = await render([]);
    await openAdd(fixture, host);

    expect(await optionLabels(fixture, document.body, 'category-station')).toEqual(['Main', 'Bar']);
  });

  it('creates a category on the chosen station', async () => {
    const { fixture, host, http } = await render([]);
    await openAdd(fixture, host);

    expect(selectedLabel(document.body, 'category-station')).toBe('Estación predeterminada');
    type('#category-name', 'Shots');
    typeNumber(document.body, 'category-order', '1');
    await chooseOption(fixture, document.body, 'category-station', 'Bar');
    submit();

    expect(http.expectOne(`${ADMIN}/categories`).request.body).toEqual({
      name: 'Shots',
      displayOrder: 1,
      stationId: 's2',
    });
  });

  it('searches the stations before choosing one', async () => {
    const { fixture, host, http } = await render([]);
    await openAdd(fixture, host);

    type('#category-name', 'Shots');
    typeNumber(document.body, 'category-order', '1');
    expect(await filterOptions(fixture, document.body, 'category-station', 'ba')).toEqual(['Bar']);
    await chooseOption(fixture, document.body, 'category-station', 'Bar');
    submit();

    expect(http.expectOne(`${ADMIN}/categories`).request.body.stationId).toBe('s2');
  });

  it('edits a category with the version it was listed with', async () => {
    const { fixture, host, http } = await render();

    host.querySelector<HTMLButtonElement>('[data-testid="edit-c2"]')!.click();
    await fixture.whenStable();
    expect(document.body.querySelector<HTMLInputElement>('#category-name')!.value).toBe('Snacks');
    expect(selectedLabel(document.body, 'category-station')).toBe('Bar');
    expect(document.body.querySelector('[role="dialog"] p-select .p-select-clear-icon')).toBeNull();
    type('#category-name', 'Bites');
    submit();
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
    await fixture.whenStable();
    expect(ask).toHaveBeenCalledWith(expect.objectContaining({ message: expect.stringContaining('Juices') }));
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

  it('offers Delete instead of Deactivate for an empty category', async () => {
    const { host } = await render([{ ...juices, deletable: true }, snacks]);

    expect(host.querySelector('[data-testid="delete-c1"]')).toBeTruthy();
    expect(host.querySelector('[data-testid="toggle-c1"]')).toBeNull();
    expect(host.querySelector('[data-testid="delete-c2"]')).toBeNull();
    expect(host.querySelector('[data-testid="toggle-c2"]')).toBeTruthy();
  });

  it('asks before deleting, sends the version and drops the row', async () => {
    const { fixture, host, http } = await render([{ ...juices, deletable: true }, snacks]);

    host.querySelector<HTMLButtonElement>('[data-testid="delete-c1"]')!.click();
    await fixture.whenStable();
    expect(ask).toHaveBeenCalledWith(expect.objectContaining({ message: expect.stringContaining('Juices') }));
    const request = http.expectOne(`${ADMIN}/categories/c1`);
    expect(request.request.method).toBe('DELETE');
    expect(request.request.headers.get('If-Match')).toBe('"2"');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(1);
    expect(rows(host)[0]).toContain('Snacks');
  });

  it('disables Delete while the request is in flight so a double click sends one DELETE', async () => {
    const { fixture, host, http } = await render([{ ...juices, deletable: true }]);
    const button = () => host.querySelector<HTMLButtonElement>('[data-testid="delete-c1"]')!;

    button().click();
    await fixture.whenStable();
    const request = http.expectOne(`${ADMIN}/categories/c1`);
    expect(button().disabled).toBe(true);
    button().click();
    await fixture.whenStable();
    http.expectNone(`${ADMIN}/categories/c1`);

    request.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();
    expect(host.querySelector('[data-testid="delete-c1"]')).toBeNull();
  });

  it('enables Delete again when the request fails', async () => {
    const { fixture, host, http } = await render([{ ...juices, deletable: true }]);
    const button = () => host.querySelector<HTMLButtonElement>('[data-testid="delete-c1"]')!;

    button().click();
    await fixture.whenStable();
    http.expectOne(`${ADMIN}/categories/c1`).flush(
      { type: 'about:blank', status: 500, code: 'common.internal-error', correlationId: 'c' },
      { status: 500, statusText: 'Server Error' },
    );
    await fixture.whenStable();

    expect(button().disabled).toBe(false);
  });

  it('reads the stations again when a category cannot go back on a station that was retired', async () => {
    const { fixture, host, http } = await render([{ ...juices, active: false, deletable: false }]);

    host.querySelector<HTMLButtonElement>('[data-testid="toggle-c1"]')!.click();
    await fixture.whenStable();
    http.expectOne(`${ADMIN}/categories/c1/reactivate`).flush(
      { type: 'about:blank', status: 409, code: 'catalog.station-inactive', correlationId: 'c' },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();

    http.expectOne(`${ADMIN}/categories`).flush([{ ...juices, active: false }]);
    http.expectOne(`${ADMIN}/stations`).flush([{ ...stations[0], active: false }, stations[1]]);
    await fixture.whenStable();
    await openAdd(fixture, host);
    expect(await optionLabels(fixture, document.body, 'category-station')).toEqual(['Bar']);
  });

  it('sends nothing when the deletion is declined', async () => {
    ask.mockResolvedValueOnce(false);
    const { fixture, host, http } = await render([{ ...juices, deletable: true }]);

    host.querySelector<HTMLButtonElement>('[data-testid="delete-c1"]')!.click();
    await fixture.whenStable();

    http.expectNone(`${ADMIN}/categories/c1`);
  });

  it('reloads the list when the category got products since it was listed', async () => {
    const { fixture, host, http } = await render([{ ...juices, deletable: true }]);

    host.querySelector<HTMLButtonElement>('[data-testid="delete-c1"]')!.click();
    await fixture.whenStable();
    http.expectOne(`${ADMIN}/categories/c1`).flush(
      { type: 'about:blank', status: 409, code: 'catalog.category-in-use', correlationId: 'c' },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();
    http.expectOne(`${ADMIN}/categories`).flush([{ ...juices, deletable: false }]);
    await fixture.whenStable();

    expect(host.querySelector('[data-testid="toggle-c1"]')).toBeTruthy();
    expect(host.querySelector('[data-testid="delete-c1"]')).toBeNull();
  });

  it('reloads the list when the category changed in the meantime', async () => {
    const { fixture, host, http } = await render();

    host.querySelector<HTMLButtonElement>('[data-testid="toggle-c1"]')!.click();
    await fixture.whenStable();
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
    type('#category-name', 'Mine');
    submit();
    http
      .expectOne(`${ADMIN}/categories/c1`)
      .flush(stale, { status: 412, statusText: 'Precondition Failed' });
    await fixture.whenStable();
    http
      .expectOne(`${ADMIN}/categories`)
      .flush([{ ...juices, name: 'Fresh juices', etag: '"9"' }, snacks]);
    await fixture.whenStable();

    expect(document.body.querySelector<HTMLInputElement>('#category-name')!.value).toBe('Fresh juices');
    submit();
    const retry = http.expectOne(`${ADMIN}/categories/c1`);
    expect(retry.request.headers.get('If-Match')).toBe('"9"');
    retry.flush({ ...juices, name: 'Fresh juices', etag: '"10"' });
  });

  it('leaves edit mode when the category no longer exists after a failed save', async () => {
    const { fixture, host, http } = await render();

    host.querySelector<HTMLButtonElement>('[data-testid="edit-c1"]')!.click();
    await fixture.whenStable();
    submit();
    http.expectOne(`${ADMIN}/categories/c1`).flush(
      { type: 'about:blank', status: 404, code: 'common.not-found', correlationId: 'c' },
      { status: 404, statusText: 'Not Found' },
    );
    await fixture.whenStable();
    http.expectOne(`${ADMIN}/categories`).flush([snacks]);
    await fixture.whenStable();

    expect(document.body.querySelector('#category-name')).toBeNull();
  });

  it('does not deactivate when the confirmation is declined', async () => {
    ask.mockResolvedValueOnce(false);
    const { fixture, host } = await render();

    host.querySelector<HTMLButtonElement>('[data-testid="toggle-c1"]')!.click();
    await fixture.whenStable();
  });
});
