import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { Station } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { ConfirmAction } from '../../../shared/state/confirm-action';
import { CatalogApi } from './catalog-api';
import { StationList } from './station-list';

const STATIONS = 'http://api.test/api/v1/admin/stations';

const main: Station = { id: 's1', name: 'Main', defaultStation: true, active: true, etag: '"1"' };
const bar: Station = { id: 's2', name: 'Bar', defaultStation: false, active: true, etag: '"4"' };

const ask = vi.fn(() => Promise.resolve(true));

async function render(stations: Station[] = [main, bar]) {
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
  const fixture = TestBed.createComponent(StationList);
  const http = TestBed.inject(HttpTestingController);
  await fixture.whenStable();
  http.expectOne(STATIONS).flush(stations);
  await fixture.whenStable();
  const host = fixture.nativeElement as HTMLElement;
  return { fixture, host, http, messages: TestBed.inject(MessageService) };
}

function field() {
  return document.body.querySelector<HTMLInputElement>('#station-name')!;
}

function type(value: string) {
  field().value = value;
  field().dispatchEvent(new Event('input'));
}

function submit() {
  document.body.querySelector<HTMLFormElement>('[role="dialog"] form')!.dispatchEvent(new Event('submit'));
}

async function openAdd(fixture: { whenStable: () => Promise<unknown> }, host: HTMLElement) {
  host.querySelector<HTMLButtonElement>('[data-testid="add-station"]')!.click();
  await fixture.whenStable();
}

function rows(host: HTMLElement) {
  return Array.from(host.querySelectorAll('tbody tr')).map((row) => row.textContent ?? '');
}

describe('StationList', () => {
  beforeEach(() => ask.mockReset().mockResolvedValue(true));
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('shows the state of each station and keeps Deactivate away from the default one', async () => {
    const { host } = await render([main, { ...bar, active: false }]);

    const [first, second] = rows(host);
    expect(first).toContain('Activa');
    expect(second).toContain('Inactiva');
    expect(host.querySelector('[data-testid="toggle-s1"]')).toBeNull();
    expect(host.querySelector('[data-testid="toggle-s2"]')).toBeTruthy();
  });

  it('asks what happens to the kitchen, then deactivates with the version', async () => {
    const { fixture, host, http } = await render();

    host.querySelector<HTMLButtonElement>('[data-testid="toggle-s2"]')!.click();
    await fixture.whenStable();
    expect(ask).toHaveBeenCalledWith(expect.objectContaining({ message: expect.stringContaining('Bar') }));
    const request = http.expectOne(`${STATIONS}/s2/deactivate`);
    expect(request.request.headers.get('If-Match')).toBe('"4"');
    request.flush({ ...bar, active: false, etag: '"5"' });
    await fixture.whenStable();

    expect(rows(host)[1]).toContain('Inactiva');
  });

  it('sends nothing when the deactivation is declined', async () => {
    ask.mockResolvedValue(false);
    const { fixture, host, http } = await render();

    host.querySelector<HTMLButtonElement>('[data-testid="toggle-s2"]')!.click();
    await fixture.whenStable();

    http.expectNone(`${STATIONS}/s2/deactivate`);
  });

  it('reactivates straight away without asking', async () => {
    const { fixture, host, http } = await render([main, { ...bar, active: false }]);

    host.querySelector<HTMLButtonElement>('[data-testid="toggle-s2"]')!.click();
    await fixture.whenStable();
    expect(ask).not.toHaveBeenCalled();
    http.expectOne(`${STATIONS}/s2/reactivate`).flush({ ...bar, active: true, etag: '"6"' });
    await fixture.whenStable();

    expect(rows(host)[1]).toContain('Activa');
  });

  it('keeps the station and tells the user when categories still use it', async () => {
    const { fixture, host, http, messages } = await render();
    const add = vi.spyOn(messages, 'add');

    host.querySelector<HTMLButtonElement>('[data-testid="toggle-s2"]')!.click();
    await fixture.whenStable();
    http.expectOne(`${STATIONS}/s2/deactivate`).flush(
      { type: 'about:blank', status: 409, code: 'catalog.station-has-active-categories', correlationId: 'c' },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();

    expect(add).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
    expect(rows(host)[1]).toContain('Activa');
  });

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

    await openAdd(fixture, host);
    type('Bar');
    submit();
    const request = http.expectOne(STATIONS);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Bar' });
    request.flush(bar, { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(2);
    expect(document.body.querySelector('#station-name')).toBeNull();
  });

  it('does not send a blank name', async () => {
    const { fixture, host } = await render([main]);

    await openAdd(fixture, host);
    type('   ');
    submit();
    await fixture.whenStable();

    expect(field().getAttribute('aria-invalid')).toBe('true');
  });

  it('renames a station with the version it was listed with', async () => {
    const { fixture, host, http } = await render();

    host.querySelector<HTMLButtonElement>('[data-testid="edit-s2"]')!.click();
    await fixture.whenStable();
    expect(field().value).toBe('Bar');
    type('Cold bar');
    submit();
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
    type('Cold bar');
    submit();
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
    type('Cold bar');
    submit();
    http.expectOne(`${STATIONS}/s2`).flush(
      { type: 'about:blank', status: 412, code: 'common.precondition-failed', correlationId: 'c' },
      { status: 412, statusText: 'Precondition Failed' },
    );
    await fixture.whenStable();
    http.expectOne(STATIONS).flush([main, { ...bar, name: 'Juice bar', etag: '"5"' }]);
    await fixture.whenStable();

    expect(field().value).toBe('Juice bar');
    submit();
    const retry = http.expectOne(`${STATIONS}/s2`);
    expect(retry.request.headers.get('If-Match')).toBe('"5"');
    retry.flush({ ...bar, name: 'Juice bar', etag: '"6"' });
  });

  it('leaves edit mode when the station no longer exists after a failed rename', async () => {
    const { fixture, host, http } = await render();

    host.querySelector<HTMLButtonElement>('[data-testid="edit-s2"]')!.click();
    await fixture.whenStable();
    submit();
    http.expectOne(`${STATIONS}/s2`).flush(
      { type: 'about:blank', status: 404, code: 'common.not-found', correlationId: 'c' },
      { status: 404, statusText: 'Not Found' },
    );
    await fixture.whenStable();
    http.expectOne(STATIONS).flush([main]);
    await fixture.whenStable();

    expect(document.body.querySelector('#station-name')).toBeNull();
  });

  it('shows a duplicate name as a notification and keeps what was typed', async () => {
    const { fixture, host, http, messages } = await render();
    const add = vi.spyOn(messages, 'add');

    await openAdd(fixture, host);
    type('Main');
    submit();
    http.expectOne(STATIONS).flush(
      { type: 'about:blank', status: 409, code: 'catalog.station-name-already-used', correlationId: 'c-2' },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();

    expect(add).toHaveBeenCalledWith(
      expect.objectContaining({ summary: expect.stringContaining('estación') }),
    );
    expect(field().value).toBe('Main');
  });

  it('marks the field the server rejected', async () => {
    const { fixture, host, http } = await render();

    await openAdd(fixture, host);
    type('x');
    submit();
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

    expect(field().getAttribute('aria-invalid')).toBe('true');
  });
});
