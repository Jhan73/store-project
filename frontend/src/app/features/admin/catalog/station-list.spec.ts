import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { Station } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { CatalogApi } from './catalog-api';
import { StationList } from './station-list';

const STATIONS = 'http://api.test/api/v1/admin/stations';

const main: Station = { id: 's1', name: 'Main', defaultStation: true, etag: '"1"' };
const bar: Station = { id: 's2', name: 'Bar', defaultStation: false, etag: '"4"' };

async function render(stations: Station[] = [main, bar]) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      MessageService,
      CatalogApi,
      { provide: API_ORIGIN, useValue: 'http://api.test' },
    ],
  });
  const fixture = TestBed.createComponent(StationList);
  const http = TestBed.inject(HttpTestingController);
  await fixture.whenStable();
  http.expectOne(STATIONS).flush(stations);
  await fixture.whenStable();
  const host = fixture.nativeElement as HTMLElement;
  return { fixture, host, http, messages: TestBed.inject(MessageService) };
}

function type(host: HTMLElement, selector: string, value: string) {
  const input = host.querySelector<HTMLInputElement>(selector)!;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

function rows(host: HTMLElement) {
  return Array.from(host.querySelectorAll('tbody tr')).map((row) => row.textContent ?? '');
}

describe('StationList', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('lists the stations and marks the default one', async () => {
    const { host } = await render();

    const [first, second] = rows(host);
    expect(first).toContain('Main');
    expect(first).toContain('Predeterminada');
    expect(second).toContain('Bar');
    expect(second).not.toContain('Predeterminada');
  });

  it('creates a station and shows it in the list', async () => {
    const { fixture, host, http } = await render([main]);

    type(host, '#station-name', 'Bar');
    host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    const request = http.expectOne(STATIONS);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Bar' });
    request.flush(bar, { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(2);
    expect(host.querySelector<HTMLInputElement>('#station-name')!.value).toBe('');
  });

  it('does not send a blank name', async () => {
    const { fixture, host } = await render([main]);

    type(host, '#station-name', '   ');
    host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();

    expect(host.querySelector('#station-name')!.getAttribute('aria-invalid')).toBe('true');
  });

  it('renames a station with the version it was listed with', async () => {
    const { fixture, host, http } = await render();

    host.querySelector<HTMLButtonElement>('[data-testid="edit-s2"]')!.click();
    await fixture.whenStable();
    expect(host.querySelector<HTMLInputElement>('#station-name')!.value).toBe('Bar');
    type(host, '#station-name', 'Cold bar');
    host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    const request = http.expectOne(`${STATIONS}/s2`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.headers.get('If-Match')).toBe('"4"');
    request.flush({ ...bar, name: 'Cold bar', etag: '"5"' });
    await fixture.whenStable();

    expect(rows(host)[1]).toContain('Cold bar');
  });

  it('reloads the list and says so when the station changed in the meantime', async () => {
    const { fixture, host, http, messages } = await render();
    const add = vi.spyOn(messages, 'add');

    host.querySelector<HTMLButtonElement>('[data-testid="edit-s2"]')!.click();
    await fixture.whenStable();
    type(host, '#station-name', 'Cold bar');
    host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    http.expectOne(`${STATIONS}/s2`).flush(
      { type: 'about:blank', status: 412, code: 'common.precondition-failed', correlationId: 'c-1' },
      { status: 412, statusText: 'Precondition Failed' },
    );
    await fixture.whenStable();
    http.expectOne(STATIONS).flush([main, { ...bar, name: 'Juice bar', etag: '"5"' }]);
    await fixture.whenStable();

    expect(add).toHaveBeenCalledWith(
      expect.objectContaining({ severity: 'error', summary: expect.stringContaining('cambiaron') }),
    );
    expect(rows(host)[1]).toContain('Juice bar');
  });

  it('retries a stale rename with the refreshed version and shows the current name', async () => {
    const { fixture, host, http } = await render();

    host.querySelector<HTMLButtonElement>('[data-testid="edit-s2"]')!.click();
    await fixture.whenStable();
    type(host, '#station-name', 'Cold bar');
    host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    http.expectOne(`${STATIONS}/s2`).flush(
      { type: 'about:blank', status: 412, code: 'common.precondition-failed', correlationId: 'c' },
      { status: 412, statusText: 'Precondition Failed' },
    );
    await fixture.whenStable();
    http.expectOne(STATIONS).flush([main, { ...bar, name: 'Juice bar', etag: '"5"' }]);
    await fixture.whenStable();

    expect(host.querySelector<HTMLInputElement>('#station-name')!.value).toBe('Juice bar');
    host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    const retry = http.expectOne(`${STATIONS}/s2`);
    expect(retry.request.headers.get('If-Match')).toBe('"5"');
    retry.flush({ ...bar, name: 'Juice bar', etag: '"6"' });
  });

  it('leaves edit mode when the station no longer exists after a failed rename', async () => {
    const { fixture, host, http } = await render();

    host.querySelector<HTMLButtonElement>('[data-testid="edit-s2"]')!.click();
    await fixture.whenStable();
    host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    http.expectOne(`${STATIONS}/s2`).flush(
      { type: 'about:blank', status: 404, code: 'common.not-found', correlationId: 'c' },
      { status: 404, statusText: 'Not Found' },
    );
    await fixture.whenStable();
    http.expectOne(STATIONS).flush([main]);
    await fixture.whenStable();

    expect(host.querySelector<HTMLInputElement>('#station-name')!.value).toBe('');
    expect(host.querySelector('form [type="button"]')).toBeNull();
  });

  it('shows a duplicate name as a notification and keeps what was typed', async () => {
    const { fixture, host, http, messages } = await render();
    const add = vi.spyOn(messages, 'add');

    type(host, '#station-name', 'Main');
    host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    http.expectOne(STATIONS).flush(
      { type: 'about:blank', status: 409, code: 'catalog.station-name-already-used', correlationId: 'c-2' },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();

    expect(add).toHaveBeenCalledWith(
      expect.objectContaining({ summary: expect.stringContaining('estación') }),
    );
    expect(host.querySelector<HTMLInputElement>('#station-name')!.value).toBe('Main');
  });

  it('marks the field the server rejected', async () => {
    const { fixture, host, http } = await render();

    type(host, '#station-name', 'x');
    host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    http.expectOne(STATIONS).flush(
      {
        type: 'about:blank',
        status: 400,
        code: 'common.validation-failed',
        correlationId: 'c-3',
        errors: [{ field: 'name', constraint: 'Size' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(host.querySelector('#station-name')!.getAttribute('aria-invalid')).toBe('true');
  });
});
