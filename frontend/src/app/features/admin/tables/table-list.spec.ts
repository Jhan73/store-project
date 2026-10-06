import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { AdminTable } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { TableList } from './table-list';
import { TablesApi } from './tables-api';

const ADMIN = 'http://api.test/api/v1/admin/tables';

const one: AdminTable = {
  id: 't1',
  name: 'Mesa 1',
  area: 'Terraza',
  displayOrder: 1,
  active: true,
  etag: '"2"',
};
const two: AdminTable = { ...one, id: 't2', name: 'Mesa 2', area: null, displayOrder: 2 };

const stale = {
  type: 'about:blank',
  status: 412,
  code: 'common.precondition-failed',
  correlationId: 'c',
};

async function render(tables: AdminTable[] = [one, two]) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      MessageService,
      TablesApi,
      { provide: API_ORIGIN, useValue: 'http://api.test' },
    ],
  });
  const fixture = TestBed.createComponent(TableList);
  const http = TestBed.inject(HttpTestingController);
  await fixture.whenStable();
  http.expectOne(ADMIN).flush(tables);
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

function click(host: HTMLElement, testId: string) {
  host.querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`)!.click();
}

function rows(host: HTMLElement) {
  return Array.from(host.querySelectorAll('tbody tr')).map((row) => row.textContent ?? '');
}

function value(host: HTMLElement, selector: string) {
  return host.querySelector<HTMLInputElement>(selector)!.value;
}

describe('TableList', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('lists the tables with their area, order and state', async () => {
    const { host } = await render([one, { ...two, active: false }]);

    const [first, second] = rows(host);
    expect(first).toContain('Mesa 1');
    expect(first).toContain('Terraza');
    expect(first).toContain('Activa');
    expect(second).toContain('Mesa 2');
    expect(second).toContain('Inactiva');
  });

  it('creates a table, with no area when it is left blank', async () => {
    const { fixture, host, http } = await render([]);

    type(host, '#table-name', ' Mesa 3 ');
    type(host, '#table-order', '3');
    submit(host);
    const request = http.expectOne(ADMIN);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Mesa 3', area: null, displayOrder: 3 });
    request.flush({ ...one, id: 't3', name: 'Mesa 3', area: null, displayOrder: 3 });
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(1);
    expect(value(host, '#table-name')).toBe('');
  });

  it('places a created table by its display order, then by name', async () => {
    const { fixture, host, http } = await render([one, two]);

    type(host, '#table-name', 'Barra');
    type(host, '#table-order', '0');
    submit(host);
    http
      .expectOne(ADMIN)
      .flush({ ...one, id: 't3', name: 'Barra', area: null, displayOrder: 0 });
    await fixture.whenStable();

    const [first, second, third] = rows(host);
    expect(first).toContain('Barra');
    expect(second).toContain('Mesa 1');
    expect(third).toContain('Mesa 2');
  });

  it('moves an edited table to the position of its new display order', async () => {
    const { fixture, host, http } = await render([one, two]);

    click(host, 'edit-t2');
    await fixture.whenStable();
    type(host, '#table-order', '0');
    submit(host);
    http.expectOne(`${ADMIN}/t2`).flush({ ...two, displayOrder: 0, etag: '"3"' });
    await fixture.whenStable();

    expect(rows(host)[0]).toContain('Mesa 2');
    expect(rows(host)[1]).toContain('Mesa 1');
  });

  it('creates a table with an area', async () => {
    const { host, http } = await render([]);

    type(host, '#table-name', 'Barra 1');
    type(host, '#table-area', ' Barra ');
    type(host, '#table-order', '0');
    submit(host);

    expect(http.expectOne(ADMIN).request.body).toEqual({
      name: 'Barra 1',
      area: 'Barra',
      displayOrder: 0,
    });
  });

  it('does not send a table with a bad name, area or order', async () => {
    const { fixture, host, http } = await render([]);

    type(host, '#table-name', 'x'.repeat(61));
    type(host, '#table-area', 'y'.repeat(61));
    type(host, '#table-order', '-1');
    submit(host);
    await fixture.whenStable();

    http.expectNone(ADMIN);
    for (const id of ['name', 'area', 'order']) {
      expect(host.querySelector(`#table-${id}`)!.getAttribute('aria-invalid')).toBe('true');
    }
  });

  it('edits a table with the version it was listed with', async () => {
    const { fixture, host, http } = await render();

    click(host, 'edit-t1');
    await fixture.whenStable();
    expect(value(host, '#table-name')).toBe('Mesa 1');
    expect(value(host, '#table-area')).toBe('Terraza');
    expect(value(host, '#table-order')).toBe('1');
    type(host, '#table-area', '');
    type(host, '#table-order', '5');
    submit(host);
    const request = http.expectOne(`${ADMIN}/t1`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.headers.get('If-Match')).toBe('"2"');
    expect(request.request.body).toEqual({ name: 'Mesa 1', area: null, displayOrder: 5 });
    request.flush({ ...one, area: null, displayOrder: 5, etag: '"3"' });
    await fixture.whenStable();

    expect(rows(host)[0]).not.toContain('Terraza');
    expect(value(host, '#table-name')).toBe('');
  });

  it('keeps the typed name and tells the user when the name is already used', async () => {
    const { fixture, host, http } = await render([]);

    type(host, '#table-name', 'Mesa 1');
    type(host, '#table-order', '1');
    submit(host);
    http.expectOne(ADMIN).flush(
      { type: 'about:blank', status: 409, code: 'instore.table-name-already-used', correlationId: 'c' },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();

    expect(value(host, '#table-name')).toBe('Mesa 1');
    expect(rows(host)).toHaveLength(0);
  });

  it('deactivates and reactivates with the version of the table and blocks the button meanwhile', async () => {
    const { fixture, host, http } = await render([one, { ...two, active: false }]);
    const button = (id: string) =>
      host.querySelector<HTMLButtonElement>(`[data-testid="toggle-${id}"]`)!;

    button('t1').click();
    await fixture.whenStable();
    expect(button('t1').disabled).toBe(true);
    button('t1').click();
    const off = http.expectOne(`${ADMIN}/t1/deactivate`);
    expect(off.request.headers.get('If-Match')).toBe('"2"');
    off.flush({ ...one, active: false, etag: '"3"' });
    await fixture.whenStable();
    expect(rows(host)[0]).toContain('Inactiva');
    expect(button('t1').disabled).toBe(false);

    button('t2').click();
    const on = http.expectOne(`${ADMIN}/t2/reactivate`);
    expect(on.request.headers.get('If-Match')).toBe('"2"');
    on.flush({ ...two, active: true, etag: '"3"' });
    await fixture.whenStable();
    expect(rows(host)[1]).toContain('Activa');
  });

  it('re-enables the button and reloads when a toggle is stale', async () => {
    const { fixture, host, http } = await render();

    click(host, 'toggle-t1');
    http
      .expectOne(`${ADMIN}/t1/deactivate`)
      .flush(stale, { status: 412, statusText: 'Precondition Failed' });
    await fixture.whenStable();
    http.expectOne(ADMIN).flush([{ ...one, name: 'Fresh', etag: '"9"' }, two]);
    await fixture.whenStable();

    expect(rows(host)[0]).toContain('Fresh');
    expect(host.querySelector<HTMLButtonElement>('[data-testid="toggle-t1"]')!.disabled).toBe(false);
  });

  it('retries a stale save with the refreshed version and shows the current values', async () => {
    const { fixture, host, http } = await render();

    click(host, 'edit-t1');
    await fixture.whenStable();
    type(host, '#table-name', 'Mine');
    submit(host);
    http.expectOne(`${ADMIN}/t1`).flush(stale, { status: 412, statusText: 'Precondition Failed' });
    await fixture.whenStable();
    http.expectOne(ADMIN).flush([{ ...one, name: 'Fresh', etag: '"9"' }, two]);
    await fixture.whenStable();

    expect(value(host, '#table-name')).toBe('Fresh');
    submit(host);
    const retry = http.expectOne(`${ADMIN}/t1`);
    expect(retry.request.headers.get('If-Match')).toBe('"9"');
    retry.flush({ ...one, name: 'Fresh', etag: '"10"' });
  });

  it('leaves edit mode when the table no longer exists after a failed save', async () => {
    const { fixture, host, http } = await render();

    click(host, 'edit-t1');
    await fixture.whenStable();
    submit(host);
    http.expectOne(`${ADMIN}/t1`).flush(
      { type: 'about:blank', status: 404, code: 'instore.table-not-found', correlationId: 'c' },
      { status: 404, statusText: 'Not Found' },
    );
    await fixture.whenStable();
    http.expectOne(ADMIN).flush([two]);
    await fixture.whenStable();

    expect(value(host, '#table-name')).toBe('');
    expect(host.querySelector('form [type="button"]')).toBeNull();
  });

  it('saves with the version returned by a toggle of the table being edited', async () => {
    const { fixture, host, http } = await render();

    click(host, 'edit-t1');
    await fixture.whenStable();
    click(host, 'toggle-t1');
    http.expectOne(`${ADMIN}/t1/deactivate`).flush({ ...one, active: false, etag: '"7"' });
    await fixture.whenStable();
    submit(host);

    expect(http.expectOne(`${ADMIN}/t1`).request.headers.get('If-Match')).toBe('"7"');
  });

  it('keeps unsaved typing when an unrelated table goes stale', async () => {
    const { fixture, host, http } = await render();

    click(host, 'edit-t1');
    await fixture.whenStable();
    type(host, '#table-name', 'Mine');
    click(host, 'toggle-t2');
    http
      .expectOne(`${ADMIN}/t2/deactivate`)
      .flush(stale, { status: 412, statusText: 'Precondition Failed' });
    await fixture.whenStable();
    http.expectOne(ADMIN).flush([one, { ...two, name: 'Fresh', etag: '"9"' }]);
    await fixture.whenStable();

    expect(rows(host)[1]).toContain('Fresh');
    expect(value(host, '#table-name')).toBe('Mine');
  });

  it('re-points the form when a toggle of the table being edited goes stale', async () => {
    const { fixture, host, http } = await render();

    click(host, 'edit-t1');
    await fixture.whenStable();
    type(host, '#table-name', 'Mine');
    click(host, 'toggle-t1');
    http
      .expectOne(`${ADMIN}/t1/deactivate`)
      .flush(stale, { status: 412, statusText: 'Precondition Failed' });
    await fixture.whenStable();
    http.expectOne(ADMIN).flush([{ ...one, name: 'Fresh', etag: '"9"' }, two]);
    await fixture.whenStable();

    expect(value(host, '#table-name')).toBe('Fresh');
  });

  it('blocks the edit buttons while a save is in flight', async () => {
    const { fixture, host, http } = await render();
    const edit = (id: string) => host.querySelector<HTMLButtonElement>(`[data-testid="edit-${id}"]`)!;

    click(host, 'edit-t1');
    await fixture.whenStable();
    submit(host);
    await fixture.whenStable();
    expect(edit('t2').disabled).toBe(true);
    edit('t2').click();
    http
      .expectOne(`${ADMIN}/t1`)
      .flush({ ...one, etag: '"3"' });
    await fixture.whenStable();

    expect(edit('t2').disabled).toBe(false);
    expect(value(host, '#table-name')).toBe('');
  });

  it('applies only the latest reload when answers arrive out of order', async () => {
    const { fixture, host, http } = await render();

    click(host, 'toggle-t1');
    click(host, 'toggle-t2');
    http
      .expectOne(`${ADMIN}/t1/deactivate`)
      .flush(stale, { status: 412, statusText: 'Precondition Failed' });
    http
      .expectOne(`${ADMIN}/t2/deactivate`)
      .flush(stale, { status: 412, statusText: 'Precondition Failed' });
    await fixture.whenStable();
    const [first, second] = http.match(ADMIN);
    second.flush([{ ...one, name: 'Newest' }]);
    first.flush([{ ...one, name: 'Older' }]);
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(1);
    expect(rows(host)[0]).toContain('Newest');
  });
});
