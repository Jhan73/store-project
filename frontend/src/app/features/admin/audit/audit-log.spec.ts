import { HttpRequest, provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { AuditEntry, AuditPage, StaffMember } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { primeTranslation } from '../../../core/theme/prime-translation';
import { chooseOption, selectedLabel } from '../../../testing/primeng-controls';
import { AuditApi } from './audit-api';
import { AuditLog } from './audit-log';

const API = 'http://api.test/api/v1';
const ANA = '0199f3a2-0000-7000-8000-00000000000a';
const BETO = '0199f3a2-0000-7000-8000-00000000000b';
const ENTITY = '0199f3a2-7c1e-7b1a-8f00-3c1d2e4a5b6c';

function staff(id: string, email: string): StaffMember {
  return { id, email, role: 'ADMIN', active: true, createdAt: '2026-01-01T00:00:00Z' };
}

const ana = staff(ANA, 'ana@juguera.pe');
const beto = staff(BETO, 'beto@juguera.pe');

const update: AuditEntry = {
  id: 'e1',
  occurredAt: '2026-10-05T15:30:00Z',
  actorId: ANA,
  actorRole: 'ADMIN',
  action: 'PRODUCT_UPDATED',
  entityType: 'PRODUCT',
  entityId: ENTITY,
  before: { price: '10.00' },
  after: { price: '12.00' },
  reason: 'Ajuste de precio',
  correlationId: 'req-1',
};
const system: AuditEntry = {
  ...update,
  id: 'e2',
  occurredAt: '2026-10-05T14:00:00Z',
  actorId: null,
  actorRole: 'SYSTEM',
  action: 'USER_CREATED',
  entityType: 'USER',
  before: null,
  after: { email: 'x@y.pe' },
  reason: null,
};
const stranger: AuditEntry = { ...update, id: 'e3', actorId: '0199f3a2-ffff-7000-8000-00000000ffff' };

function page(content: AuditEntry[], overrides: Partial<AuditPage> = {}): AuditPage {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, ...overrides };
}

function problem(status: number, code: string) {
  return [
    { type: 'about:blank', status, code, correlationId: 'c' },
    { status, statusText: 'Error' },
  ] as const;
}

const isSearch = (request: HttpRequest<unknown>) => request.url === `${API}/audit-entries`;

function sent(request: TestRequest): Record<string, string | null> {
  return Object.fromEntries(
    request.request.params.keys().map((key) => [key, request.request.params.get(key)]),
  );
}

async function render(url = '/audit', members: StaffMember[] = [ana, beto]) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([{ path: 'audit', component: AuditLog, providers: [AuditApi] }]),
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      MessageService,
      providePrimeNG({ translation: primeTranslation }),
      { provide: API_ORIGIN, useValue: 'http://api.test' },
    ],
  });
  const harness = await RouterTestingHarness.create();
  const http = TestBed.inject(HttpTestingController);
  const toast = vi.spyOn(TestBed.inject(MessageService), 'add');
  await harness.navigateByUrl(url);
  http.expectOne(`${API}/admin/settings`).flush({ timeZone: 'America/Lima', currency: 'PEN' });
  http.expectOne(`${API}/staff?page=0&size=100`).flush({
    content: members,
    page: 0,
    size: 100,
    totalElements: members.length,
    totalPages: 1,
  });
  const fixture = harness.fixture;
  const host = fixture.nativeElement as HTMLElement;
  const router = TestBed.inject(Router);
  return { fixture, host, http, toast, router };
}

// Renders and answers the first search.
async function rendered(url = '/audit', first: AuditPage = page([update, system, stranger])) {
  const context = await render(url);
  context.http.expectOne(isSearch).flush(first);
  await context.fixture.whenStable();
  return context;
}

function rows(host: HTMLElement) {
  return Array.from(host.querySelectorAll('tbody tr')).map((row) => row.textContent ?? '');
}

function click(host: HTMLElement, testId: string) {
  host.querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`)!.click();
}

// The date picker only reads typed text after a key press.
function typeDate(host: HTMLElement, value: string) {
  const input = host.querySelector<HTMLInputElement>('#audit-range')!;
  input.dispatchEvent(new KeyboardEvent('keydown', { key: '0' }));
  input.value = value;
  input.dispatchEvent(new Event('input'));
  input.dispatchEvent(new Event('blur'));
}

async function press(fixture: { whenStable(): Promise<unknown> }, host: HTMLElement, testId: string) {
  click(host, testId);
  await fixture.whenStable();
}

function type(host: HTMLElement, selector: string, value: string) {
  const input = host.querySelector<HTMLInputElement>(selector)!;
  input.value = value;
  input.dispatchEvent(new Event('input'));
  input.dispatchEvent(new Event('blur'));
}

describe('AuditLog', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  describe('results', () => {
    it('waits for the store time zone and then lists the newest entries', async () => {
      const { host } = await rendered();

      const [first, second] = rows(host);
      expect(first).toContain('10:30');
      expect(first).toContain('ana@juguera.pe');
      expect(first).toContain('Administrador');
      expect(first).toContain('PRODUCT_UPDATED');
      expect(first).toContain('Producto');
      expect(first).toContain('0199f3a2…');
      expect(first).toContain('Ajuste de precio');
      expect(second).toContain('09:00');
      expect(second).toContain('Sistema');
      expect(second).toContain('USER_CREATED');
    });

    it('shows a shortened id for an actor that is not an account anymore', async () => {
      const { host } = await rendered();

      expect(rows(host)[2]).toContain('0199f3a2…');
    });

    it('keeps the full id for the tooltip', async () => {
      const { host } = await rendered();

      const id = host.querySelector('tbody tr code.id');
      expect(id?.textContent).toBe('0199f3a2…');
    });

    it('announces how many entries matched', async () => {
      const { host } = await rendered('/audit', page([update], { totalElements: 41, totalPages: 3 }));

      const live = host.querySelector('[aria-live="polite"]');
      expect(live?.textContent).toContain('41 registros');
    });

    it('labels the table for assistive technology', async () => {
      const { host } = await rendered();

      expect(host.querySelector('app-table-scroll')?.getAttribute('aria-label')).toBeTruthy();
      expect(host.querySelector('table caption')?.textContent).toBeTruthy();
    });

    it('says when nothing matches', async () => {
      const { host } = await rendered('/audit', page([], { totalElements: 0, totalPages: 0 }));

      expect(host.textContent).toContain('No hay registros con estos filtros');
      expect(host.querySelector('table')).toBeNull();
    });

    it('shows a loading state until the answer arrives', async () => {
      const { host, http, fixture } = await render();

      expect(host.textContent).toContain('Cargando');
      http.expectOne(isSearch).flush(page([update]));
      await fixture.whenStable();
      expect(host.textContent).not.toContain('Cargando');
    });

    it('shows an error state, toasts the failure and retries on request', async () => {
      const { host, http, fixture, toast } = await render();

      http.expectOne(isSearch).flush(...problem(500, 'common.internal-error'));
      await fixture.whenStable();
      expect(host.querySelector('[role="alert"]')).not.toBeNull();
      expect(toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));

      click(host, 'retry');
      http.expectOne(isSearch).flush(page([update]));
      await fixture.whenStable();
      expect(host.querySelector('[role="alert"]')).toBeNull();
      expect(rows(host)).toHaveLength(1);
    });

    it('retries the settings when they are what failed', async () => {
      TestBed.configureTestingModule({
        providers: [
          provideRouter([{ path: 'audit', component: AuditLog, providers: [AuditApi] }]),
          provideHttpClient(withInterceptors([errorInterceptor])),
          provideHttpClientTesting(),
          MessageService,
          providePrimeNG({ translation: primeTranslation }),
          { provide: API_ORIGIN, useValue: 'http://api.test' },
        ],
      });
      const harness = await RouterTestingHarness.create();
      const http = TestBed.inject(HttpTestingController);
      await harness.navigateByUrl('/audit');
      const host = harness.fixture.nativeElement as HTMLElement;

      http.expectOne(`${API}/admin/settings`).flush(...problem(500, 'common.internal-error'));
      http.expectOne(`${API}/staff?page=0&size=100`).flush(page([]));
      await harness.fixture.whenStable();
      expect(host.querySelector('[role="alert"]')).not.toBeNull();

      click(host, 'retry');
      http.expectOne(`${API}/admin/settings`).flush({ timeZone: 'America/Lima', currency: 'PEN' });
      http.expectOne(isSearch).flush(page([update]));
      await harness.fixture.whenStable();
      expect(rows(host)).toHaveLength(1);
    });

    it('applies only the latest response when searches overlap', async () => {
      const { host, http, fixture } = await rendered();

      await chooseOption(fixture, host, 'audit-entity-type', 'Personal');
      await press(fixture, host, 'search');
      const slow = http.expectOne(isSearch);
      await chooseOption(fixture, host, 'audit-entity-type', 'Mesa');
      await press(fixture, host, 'search');
      const fast = http.expectOne(isSearch);

      fast.flush(page([{ ...update, reason: 'la respuesta nueva' }]));
      slow.flush(page([{ ...update, reason: 'la respuesta vieja' }]));
      await fixture.whenStable();

      expect(rows(host).join()).toContain('la respuesta nueva');
      expect(rows(host).join()).not.toContain('la respuesta vieja');
    });
  });

  describe('filters', () => {
    it('offers the staff accounts as actors and sends the chosen id', async () => {
      const { host, http, fixture, router } = await rendered();

      await chooseOption(fixture, host, 'audit-actor', 'beto@juguera.pe');
      await press(fixture, host, 'search');

      const request = http.expectOne(isSearch);
      expect(sent(request)['actorId']).toBe(BETO);
      expect(router.url).toBe(`/audit?actorId=${BETO}`);
      request.flush(page([]));
    });

    it('filters by entity type, entity id and action', async () => {
      const { host, http, fixture, router } = await rendered();

      await chooseOption(fixture, host, 'audit-entity-type', 'Producto');
      type(host, '#audit-entity-id', ` ${ENTITY} `);
      await chooseOption(fixture, host, 'audit-action', 'PRODUCT_UPDATED');
      await press(fixture, host, 'search');

      const request = http.expectOne(isSearch);
      expect(sent(request)).toEqual({
        entityType: 'PRODUCT',
        entityId: ENTITY,
        action: 'PRODUCT_UPDATED',
        page: '0',
        size: '20',
      });
      expect(router.url).toContain('entityType=PRODUCT');
      expect(router.url).toContain(`entityId=${ENTITY}`);
      request.flush(page([]));
    });

    it('turns a date range into the exact instants of the store days, the last day included', async () => {
      const { host, http, fixture, router } = await rendered();

      typeDate(host, '01/10/2026 - 05/10/2026');
      await press(fixture, host, 'search');

      const request = http.expectOne(isSearch);
      expect(sent(request)['from']).toBe('2026-10-01T05:00:00.000Z');
      expect(sent(request)['to']).toBe('2026-10-06T04:59:59.999999Z');
      expect(router.url).toBe('/audit?from=2026-10-01&to=2026-10-05');
      request.flush(page([]));
      await fixture.whenStable();
    });

    it('takes a single picked day as that whole day', async () => {
      const { host, http, fixture } = await rendered();

      typeDate(host, '05/10/2026');
      await press(fixture, host, 'search');

      const request = http.expectOne(isSearch);
      expect(sent(request)['from']).toBe('2026-10-05T05:00:00.000Z');
      expect(sent(request)['to']).toBe('2026-10-06T04:59:59.999999Z');
      request.flush(page([]));
    });

    it('reads the filters from the URL on load', async () => {
      const { host, http, fixture } = await render(
        `/audit?from=2026-10-01&to=2026-10-05&actorId=${ANA}&entityType=USER&action=USER_CREATED&entityId=${ENTITY}`,
      );

      const request = http.expectOne(isSearch);
      expect(sent(request)).toEqual({
        actorId: ANA,
        entityType: 'USER',
        entityId: ENTITY,
        action: 'USER_CREATED',
        from: '2026-10-01T05:00:00.000Z',
        to: '2026-10-06T04:59:59.999999Z',
        page: '0',
        size: '20',
      });
      request.flush(page([]));
      await fixture.whenStable();
      expect(selectedLabel(host, 'audit-actor')).toBe('ana@juguera.pe');
      expect(selectedLabel(host, 'audit-entity-type')).toBe('Personal');
      expect(selectedLabel(host, 'audit-action')).toBe('USER_CREATED');
      expect(host.querySelector<HTMLInputElement>('#audit-range')!.value).toBe(
        '01/10/2026 - 05/10/2026',
      );
      expect(host.querySelector<HTMLInputElement>('#audit-entity-id')!.value).toBe(ENTITY);
    });

    it('follows the URL when the browser goes back', async () => {
      const { host, http, fixture, router } = await rendered('/audit?entityType=USER');

      await chooseOption(fixture, host, 'audit-entity-type', 'Mesa');
      await press(fixture, host, 'search');
      http.expectOne(isSearch).flush(page([]));
      await fixture.whenStable();
      expect(router.url).toBe('/audit?entityType=TABLE');

      await router.navigateByUrl('/audit?entityType=USER');
      const request = http.expectOne(isSearch);
      expect(sent(request)['entityType']).toBe('USER');
      request.flush(page([update]));
      await fixture.whenStable();
      expect(selectedLabel(host, 'audit-entity-type')).toBe('Personal');
    });

    it('goes back to the first page when a filter changes', async () => {
      const { host, http, fixture, router } = await rendered(
        '/audit?page=2',
        page([update], { page: 2, totalElements: 50, totalPages: 3 }),
      );

      await chooseOption(fixture, host, 'audit-entity-type', 'Mesa');
      await press(fixture, host, 'search');

      const request = http.expectOne(isSearch);
      expect(sent(request)['page']).toBe('0');
      expect(router.url).toBe('/audit?entityType=TABLE');
      request.flush(page([]));
    });

    it('searches again when the same filters are submitted twice', async () => {
      const { host, http, fixture } = await rendered();

      await press(fixture, host, 'search');

      http.expectOne(isSearch).flush(page([update]));
    });

    it('refuses a shared link whose range is reversed', async () => {
      const { host, http, fixture } = await render('/audit?from=2026-10-05&to=2026-10-01');
      await fixture.whenStable();

      http.expectNone(isSearch);
      expect(host.textContent).toContain('no puede ser posterior');
      expect(host.querySelector('#audit-range')?.getAttribute('aria-invalid')).toBe('true');
    });

    it('refuses an entity id that is not an id and marks the field', async () => {
      const { host, http, fixture } = await rendered();

      type(host, '#audit-entity-id', 'not-an-id');
      await press(fixture, host, 'search');
      await fixture.whenStable();

      http.expectNone(isSearch);
      expect(host.textContent).toContain('id válido');
      expect(host.querySelector('#audit-entity-id')?.getAttribute('aria-invalid')).toBe('true');
    });

    it('clears every filter and searches without them', async () => {
      const { host, http, fixture, router } = await rendered(`/audit?entityType=USER&entityId=${ENTITY}`);

      await press(fixture, host, 'clear');

      const request = http.expectOne(isSearch);
      expect(sent(request)).toEqual({ page: '0', size: '20' });
      expect(router.url).toBe('/audit');
      request.flush(page([update]));
      await fixture.whenStable();
      expect(host.querySelector<HTMLInputElement>('#audit-entity-id')!.value).toBe('');
      expect(selectedLabel(host, 'audit-entity-type')).not.toBe('Personal');
    });

    it('keeps an actor or type from the URL that the lists do not know', async () => {
      const { host, http, fixture } = await render('/audit?entityType=ALIEN&action=ALIEN_ACTION');

      http.expectOne(isSearch).flush(page([]));
      await fixture.whenStable();

      expect(selectedLabel(host, 'audit-entity-type')).toBe('ALIEN');
      expect(selectedLabel(host, 'audit-action')).toBe('ALIEN_ACTION');
    });
  });

  describe('pagination', () => {
    it('asks the server for the next page and writes it to the URL', async () => {
      const { host, http, fixture, router } = await rendered(
        '/audit',
        page([update], { totalElements: 45, totalPages: 3 }),
      );

      host.querySelector<HTMLButtonElement>('button.p-paginator-next')!.click();
      await fixture.whenStable();

      const request = http.expectOne(isSearch);
      expect(sent(request)['page']).toBe('1');
      expect(router.url).toBe('/audit?page=1');
      request.flush(page([update], { page: 1, totalElements: 45, totalPages: 3 }));
      await fixture.whenStable();
    });

    it('uses the page and size the server answered with', async () => {
      const { host } = await rendered(
        '/audit?page=1&size=10',
        page([update], { page: 1, size: 10, totalElements: 45, totalPages: 5 }),
      );

      const current = host.querySelector('.p-paginator-page-selected');
      expect(current?.textContent?.trim()).toBe('2');
    });

    it('has no paginator when there is nothing to page', async () => {
      const { host } = await rendered('/audit', page([], { totalElements: 0, totalPages: 0 }));

      expect(host.querySelector('p-paginator')).toBeNull();
    });
  });

  describe('detail', () => {
    it('opens the before and after of an entry in a dialog that is announced as expanded', async () => {
      const { host, fixture } = await rendered();
      const trigger = host.querySelector<HTMLButtonElement>('[data-testid="detail-e1"]')!;
      expect(trigger.getAttribute('aria-expanded')).toBe('false');

      trigger.click();
      await fixture.whenStable();

      expect(trigger.getAttribute('aria-expanded')).toBe('true');
      const dialog = document.body.querySelector('[role="dialog"]');
      expect(dialog).not.toBeNull();
      const [before, after] = Array.from(dialog!.querySelectorAll('pre')).map((pre) => pre.textContent);
      expect(before).toContain('"price": "10.00"');
      expect(after).toContain('"price": "12.00"');
    });

    it('renders a hostile value as text, never as markup', async () => {
      const hostile = { ...update, after: { note: '<img src=x onerror=alert(1)><script>boom()</script>' } };
      const { host, fixture } = await rendered('/audit', page([hostile]));

      host.querySelector<HTMLButtonElement>('[data-testid="detail-e1"]')!.click();
      await fixture.whenStable();

      const dialog = document.body.querySelector('[role="dialog"]')!;
      expect(dialog.querySelector('script')).toBeNull();
      expect(dialog.querySelector('img')).toBeNull();
      expect(dialog.textContent).toContain('<script>boom()</script>');
    });

    it('closes from its own button and reports collapsed again', async () => {
      const { host, fixture } = await rendered();
      const trigger = host.querySelector<HTMLButtonElement>('[data-testid="detail-e1"]')!;
      trigger.click();
      await fixture.whenStable();

      document.body.querySelector<HTMLButtonElement>('[data-testid="detail-close"]')!.click();
      await fixture.whenStable();

      expect(trigger.getAttribute('aria-expanded')).toBe('false');
    });
  });
});
