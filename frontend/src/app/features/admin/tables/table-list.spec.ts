import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { AdminTable } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { UndoNotice } from '../../../shared/state/undo-notice';
import { numberIsInvalid, numberText, typeNumber } from '../../../testing/primeng-controls';
import { clickPastUndo } from '../../../testing/undo-window';
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

const body = () => document.body;

function type(selector: string, value: string) {
  const input = body().querySelector<HTMLInputElement>(selector)!;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

function submit() {
  body().querySelector<HTMLFormElement>('[role="dialog"] form')!.dispatchEvent(new Event('submit'));
}

function button(host: HTMLElement, testId: string) {
  return host.querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`)!;
}

async function open(fixture: { whenStable(): Promise<unknown> }, host: HTMLElement, testId: string) {
  button(host, testId).click();
  await fixture.whenStable();
}

function rows(host: HTMLElement) {
  return Array.from(host.querySelectorAll('tbody tr')).map((row) => row.textContent ?? '');
}

function value(selector: string) {
  return body().querySelector<HTMLInputElement>(selector)!.value;
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

  it('creates a table from a dialog, with no area when it is left blank', async () => {
    const { fixture, host, http } = await render([]);

    await open(fixture, host, 'add-table');
    type('#table-name', ' Mesa 3 ');
    typeNumber(body(), 'table-order', '3');
    submit();
    const request = http.expectOne(ADMIN);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ name: 'Mesa 3', area: null, displayOrder: 3 });
    request.flush({ ...one, id: 't3', name: 'Mesa 3', area: null, displayOrder: 3 });
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(1);
    expect(body().querySelector('#table-name')).toBeNull();
  });

  it('places a created table by its display order, then by name', async () => {
    const { fixture, host, http } = await render([one, two]);

    await open(fixture, host, 'add-table');
    type('#table-name', 'Barra');
    typeNumber(body(), 'table-order', '0');
    submit();
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

    await open(fixture, host, 'edit-t2');
    typeNumber(body(), 'table-order', '0');
    submit();
    http.expectOne(`${ADMIN}/t2`).flush({ ...two, displayOrder: 0, etag: '"3"' });
    await fixture.whenStable();

    expect(rows(host)[0]).toContain('Mesa 2');
    expect(rows(host)[1]).toContain('Mesa 1');
  });

  it('creates a table with an area', async () => {
    const { fixture, host, http } = await render([]);

    await open(fixture, host, 'add-table');
    type('#table-name', 'Barra 1');
    type('#table-area', ' Barra ');
    typeNumber(body(), 'table-order', '0');
    submit();

    expect(http.expectOne(ADMIN).request.body).toEqual({
      name: 'Barra 1',
      area: 'Barra',
      displayOrder: 0,
    });
  });

  it('does not send a table with a bad name, area or order', async () => {
    const { fixture, host, http } = await render([]);

    await open(fixture, host, 'add-table');
    type('#table-name', 'x'.repeat(61));
    type('#table-area', 'y'.repeat(61));
    typeNumber(body(), 'table-order', '-1');
    submit();
    await fixture.whenStable();

    http.expectNone(ADMIN);
    expect(numberIsInvalid(body(), 'table-order')).toBe(true);
    for (const id of ['name', 'area']) {
      expect(body().querySelector(`#table-${id}`)!.getAttribute('aria-invalid')).toBe('true');
    }
  });

  it('edits a table with the version it was listed with', async () => {
    const { fixture, host, http } = await render();

    await open(fixture, host, 'edit-t1');
    expect(value('#table-name')).toBe('Mesa 1');
    expect(value('#table-area')).toBe('Terraza');
    expect(numberText(body(), 'table-order')).toBe('1');
    type('#table-area', '');
    typeNumber(body(), 'table-order', '5');
    submit();
    const request = http.expectOne(`${ADMIN}/t1`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.headers.get('If-Match')).toBe('"2"');
    expect(request.request.body).toEqual({ name: 'Mesa 1', area: null, displayOrder: 5 });
    request.flush({ ...one, area: null, displayOrder: 5, etag: '"3"' });
    await fixture.whenStable();

    expect(rows(host)[0]).not.toContain('Terraza');
    expect(body().querySelector('#table-name')).toBeNull();
  });

  it('keeps the typed name and tells the user when the name is already used', async () => {
    const { fixture, host, http } = await render([]);

    await open(fixture, host, 'add-table');
    type('#table-name', 'Mesa 1');
    typeNumber(body(), 'table-order', '1');
    submit();
    http.expectOne(ADMIN).flush(
      { type: 'about:blank', status: 409, code: 'instore.table-name-already-used', correlationId: 'c' },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();

    expect(value('#table-name')).toBe('Mesa 1');
    expect(rows(host)).toEqual(['Todavía no hay mesas.']);
  });

  it('shows a deactivated table as inactive at once and sends it with its version after the undo window', async () => {
    const { fixture, host, http } = await render();

    clickPastUndo(button(host, 'toggle-t1'));
    await fixture.whenStable();
    expect(rows(host)[0]).toContain('Inactiva');
    expect(button(host, 'toggle-t1').disabled).toBe(true);
    const off = http.expectOne(`${ADMIN}/t1/deactivate`);
    expect(off.request.headers.get('If-Match')).toBe('"2"');
    off.flush({ ...one, active: false, etag: '"3"' });
    await fixture.whenStable();

    expect(rows(host)[0]).toContain('Inactiva');
    expect(button(host, 'toggle-t1').disabled).toBe(false);
  });

  it('undoing a deactivation sends nothing', async () => {
    const { fixture, host } = await render();
    const notice = TestBed.inject(UndoNotice);

    button(host, 'toggle-t1').click();
    await fixture.whenStable();
    notice.undo(notice.active()[0]);
    await fixture.whenStable();

    expect(rows(host)[0]).not.toContain('Inactiva');
  });

  it('reactivates straight away with the version of the table', async () => {
    const { fixture, host, http } = await render([one, { ...two, active: false }]);

    button(host, 'toggle-t2').click();
    await fixture.whenStable();
    expect(button(host, 'toggle-t2').disabled).toBe(true);
    const on = http.expectOne(`${ADMIN}/t2/reactivate`);
    expect(on.request.headers.get('If-Match')).toBe('"2"');
    on.flush({ ...two, active: true, etag: '"3"' });
    await fixture.whenStable();

    expect(rows(host)[1]).not.toContain('Inactiva');
  });

  it('re-enables the button and reloads when a deactivation is stale', async () => {
    const { fixture, host, http } = await render();

    clickPastUndo(button(host, 'toggle-t1'));
    http
      .expectOne(`${ADMIN}/t1/deactivate`)
      .flush(stale, { status: 412, statusText: 'Precondition Failed' });
    await fixture.whenStable();
    http.expectOne(ADMIN).flush([{ ...one, name: 'Fresh', etag: '"9"' }, two]);
    await fixture.whenStable();

    expect(rows(host)[0]).toContain('Fresh');
    expect(rows(host)[0]).not.toContain('Inactiva');
    expect(button(host, 'toggle-t1').disabled).toBe(false);
  });

  it('retries a stale save with the refreshed version and shows the current values', async () => {
    const { fixture, host, http } = await render();

    await open(fixture, host, 'edit-t1');
    type('#table-name', 'Mine');
    submit();
    http.expectOne(`${ADMIN}/t1`).flush(stale, { status: 412, statusText: 'Precondition Failed' });
    await fixture.whenStable();
    http.expectOne(ADMIN).flush([{ ...one, name: 'Fresh', etag: '"9"' }, two]);
    await fixture.whenStable();

    expect(value('#table-name')).toBe('Fresh');
    submit();
    const retry = http.expectOne(`${ADMIN}/t1`);
    expect(retry.request.headers.get('If-Match')).toBe('"9"');
    retry.flush({ ...one, name: 'Fresh', etag: '"10"' });
  });

  it('closes the dialog when the table no longer exists after a failed save', async () => {
    const { fixture, host, http } = await render();

    await open(fixture, host, 'edit-t1');
    submit();
    http.expectOne(`${ADMIN}/t1`).flush(
      { type: 'about:blank', status: 404, code: 'instore.table-not-found', correlationId: 'c' },
      { status: 404, statusText: 'Not Found' },
    );
    await fixture.whenStable();
    http.expectOne(ADMIN).flush([two]);
    await fixture.whenStable();

    expect(body().querySelector('#table-name')).toBeNull();
  });

  it('blocks the buttons while a save is in flight', async () => {
    const { fixture, host, http } = await render();

    await open(fixture, host, 'edit-t1');
    submit();
    await fixture.whenStable();
    expect(button(host, 'edit-t2').disabled).toBe(true);
    http.expectOne(`${ADMIN}/t1`).flush({ ...one, etag: '"3"' });
    await fixture.whenStable();

    expect(button(host, 'edit-t2').disabled).toBe(false);
  });

  it('applies only the latest reload when answers arrive out of order', async () => {
    const { fixture, host, http } = await render();

    clickPastUndo(button(host, 'toggle-t1'));
    clickPastUndo(button(host, 'toggle-t2'));
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
