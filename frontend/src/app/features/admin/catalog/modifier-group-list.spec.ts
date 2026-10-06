import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { ModifierGroup } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { CatalogApi } from './catalog-api';
import { ModifierGroupList } from './modifier-group-list';

const GROUPS = 'http://api.test/api/v1/admin/modifier-groups';
const option = (id: string) => ({
  id,
  name: id,
  priceDelta: { amount: '0.00', currency: 'PEN' },
  allergens: [],
  available: true,
});

const size: ModifierGroup = {
  id: 'g1',
  name: 'Size',
  required: true,
  minChoices: 1,
  maxChoices: 1,
  etag: '"7"',
  options: [option('o1'), option('o2')],
};
const extras: ModifierGroup = {
  id: 'g2',
  name: 'Extras',
  required: false,
  minChoices: 0,
  maxChoices: 3,
  etag: '"2"',
  options: [option('o3')],
};

async function render(groups: ModifierGroup[] = [size, extras]) {
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
  const fixture = TestBed.createComponent(ModifierGroupList);
  const http = TestBed.inject(HttpTestingController);
  await fixture.whenStable();
  http.expectOne(GROUPS).flush(groups);
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
  host.querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`)!.click();

describe('ModifierGroupList', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('lists the groups with their rule and option count, and links to create and edit', async () => {
    const { host } = await render();

    const [first, second] = rows(host);
    expect(first).toContain('Size');
    expect(first).toContain('Obligatorio');
    expect(first).toContain('1 a 1');
    expect(first).toContain('2 opciones');
    expect(second).toContain('Opcional');
    expect(second).toContain('0 a 3');
    expect(second).toContain('1 opción');
    expect(host.querySelector('a[href="/admin/catalog/modifier-groups/new"]')).not.toBeNull();
    expect(host.querySelector('a[href="/admin/catalog/modifier-groups/g1"]')).not.toBeNull();
  });

  it('asks for confirmation before deleting, and deleting nothing when it is cancelled', async () => {
    const { fixture, host } = await render();

    click(host, 'delete-g1');
    await fixture.whenStable();
    expect(host.querySelector('[data-testid="confirm-delete-g1"]')).not.toBeNull();

    click(host, 'cancel-delete-g1');
    await fixture.whenStable();
    expect(host.querySelector('[data-testid="confirm-delete-g1"]')).toBeNull();
    expect(rows(host)).toHaveLength(2);
  });

  it('deletes a group with the version it was listed with', async () => {
    const { fixture, host, http } = await render();

    click(host, 'delete-g1');
    await fixture.whenStable();
    click(host, 'confirm-delete-g1');
    const request = http.expectOne(`${GROUPS}/g1`);
    expect(request.request.method).toBe('DELETE');
    expect(request.request.headers.get('If-Match')).toBe('"7"');
    request.flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(1);
    expect(rows(host)[0]).toContain('Extras');
  });

  it('keeps a group that products still use and says why', async () => {
    const { fixture, host, http, messages } = await render();
    const add = vi.spyOn(messages, 'add');

    click(host, 'delete-g1');
    await fixture.whenStable();
    click(host, 'confirm-delete-g1');
    http.expectOne(`${GROUPS}/g1`).flush(
      { type: 'about:blank', status: 409, code: 'catalog.modifier-group-in-use', correlationId: 'c' },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();

    expect(add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
    expect(rows(host)).toHaveLength(2);
  });
});
