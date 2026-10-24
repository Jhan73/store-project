import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { Reason } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { UndoNotice } from '../../../shared/state/undo-notice';
import { chooseOption, filterOptions, selectedLabel } from '../../../testing/primeng-controls';
import { clickPastUndo } from '../../../testing/undo-window';
import { ReasonList } from './reason-list';
import { SettingsApi } from './settings-api';

const ADMIN = 'http://api.test/api/v1/admin';

const spilled: Reason = { id: 'r1', type: 'VOID', code: 'Spilled', active: true, etag: '"1"' };
const mistake: Reason = { id: 'r2', type: 'VOID', code: 'Wrong order', active: false, etag: '"4"' };

const stale = {
  type: 'about:blank',
  status: 412,
  code: 'common.precondition-failed',
  correlationId: 'c',
};

async function render(reasons: Reason[] = [spilled, mistake]) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      MessageService,
      SettingsApi,
      { provide: API_ORIGIN, useValue: 'http://api.test' },
    ],
  });
  const fixture = TestBed.createComponent(ReasonList);
  const http = TestBed.inject(HttpTestingController);
  await fixture.whenStable();
  http.expectOne(`${ADMIN}/reasons?type=VOID`).flush(reasons);
  await fixture.whenStable();
  return { fixture, host: fixture.nativeElement as HTMLElement, http };
}

function type(selector: string, value: string) {
  const input = document.body.querySelector<HTMLInputElement>(selector)!;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

const chooseType = (
  fixture: { whenStable(): Promise<unknown> },
  host: HTMLElement,
  label: string,
) => chooseOption(fixture, host, 'reason-type', label);

function submit() {
  document.body.querySelector<HTMLFormElement>('[role="dialog"] form')!.dispatchEvent(new Event('submit'));
}

async function openAdd(fixture: { whenStable(): Promise<unknown> }, host: HTMLElement) {
  host.querySelector<HTMLButtonElement>('[data-testid="add-reason"]')!.click();
  await fixture.whenStable();
}

const dialogInput = () => document.body.querySelector<HTMLInputElement>('#reason-code');

function rows(host: HTMLElement) {
  return Array.from(host.querySelectorAll('tbody tr')).map((row) => row.textContent ?? '');
}

describe('ReasonList', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('lists the reasons of the first type with their state', async () => {
    const { host } = await render();

    const [first, second] = rows(host);
    expect(selectedLabel(host, 'reason-type')).toBe('Anulaciones');
    expect(first).toContain('Spilled');
    expect(first).toContain('Activo');
    expect(second).toContain('Wrong order');
    expect(second).toContain('Inactivo');
  });

  it('loads the reasons of the type that is picked', async () => {
    const { fixture, host, http } = await render();

    await chooseType(fixture, host, 'Salidas de caja');
    http
      .expectOne(`${ADMIN}/reasons?type=CASH_OUT`)
      .flush([{ ...spilled, id: 'r9', type: 'CASH_OUT', code: 'Supplier' }]);
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(1);
    expect(rows(host)[0]).toContain('Supplier');
  });

  it('searches the types and loads the one that is picked', async () => {
    const { fixture, host, http } = await render();

    expect(await filterOptions(fixture, host, 'reason-type', 'caja')).toEqual(['Salidas de caja']);
    await chooseType(fixture, host, 'Salidas de caja');
    http.expectOne(`${ADMIN}/reasons?type=CASH_OUT`).flush([]);
    await fixture.whenStable();

    expect(selectedLabel(host, 'reason-type')).toBe('Salidas de caja');
  });

  it('shows only the answer to the latest pick when answers arrive out of order', async () => {
    const { fixture, host, http } = await render();

    await chooseType(fixture, host, 'Cortesías');
    await chooseType(fixture, host, 'Salidas de caja');
    http
      .expectOne(`${ADMIN}/reasons?type=CASH_OUT`)
      .flush([{ ...spilled, id: 'r9', type: 'CASH_OUT', code: 'Supplier' }]);
    http
      .expectOne(`${ADMIN}/reasons?type=COMP`)
      .flush([{ ...spilled, id: 'r8', type: 'COMP', code: 'Friend' }]);
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(1);
    expect(rows(host)[0]).toContain('Supplier');
  });

  it('creates a reason of the picked type', async () => {
    const { fixture, host, http } = await render([]);

    await chooseType(fixture, host, 'Cortesías');
    http.expectOne(`${ADMIN}/reasons?type=COMP`).flush([]);
    await fixture.whenStable();
    await openAdd(fixture, host);
    type('#reason-code', ' Birthday ');
    submit();
    const request = http.expectOne(`${ADMIN}/reasons`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ type: 'COMP', code: 'Birthday' });
    request.flush({ ...spilled, id: 'r3', type: 'COMP', code: 'Birthday' });
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(1);
    expect(rows(host)[0]).toContain('Birthday');
    expect(dialogInput()).toBeNull();
  });

  it('does not send a blank code', async () => {
    const { fixture, host, http } = await render([]);

    await openAdd(fixture, host);
    type('#reason-code', '   ');
    submit();
    await fixture.whenStable();

    http.expectNone(`${ADMIN}/reasons`);
    expect(dialogInput()!.getAttribute('aria-invalid')).toBe('true');
  });

  it('keeps the typed code and tells the user when the code is already used', async () => {
    const { fixture, host, http } = await render([]);

    await openAdd(fixture, host);
    type('#reason-code', 'Spilled');
    submit();
    http.expectOne(`${ADMIN}/reasons`).flush(
      { type: 'about:blank', status: 409, code: 'store.reason-code-already-used', correlationId: 'c' },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();

    expect(dialogInput()!.value).toBe('Spilled');
    expect(rows(host)).toEqual(['No hay resultados.']);
  });

  it('shows a deactivated reason as inactive at once and sends it with its version after the undo window', async () => {
    const { fixture, host, http } = await render();
    const button = (id: string) =>
      host.querySelector<HTMLButtonElement>(`[data-testid="toggle-${id}"]`)!;

    clickPastUndo(button('r1'));
    await fixture.whenStable();
    expect(rows(host)[0]).toContain('Inactivo');
    expect(button('r1').disabled).toBe(true);
    const off = http.expectOne(`${ADMIN}/reasons/r1/deactivate`);
    expect(off.request.headers.get('If-Match')).toBe('"1"');
    off.flush({ ...spilled, active: false, etag: '"2"' });
    await fixture.whenStable();

    expect(rows(host)[0]).toContain('Inactivo');
    expect(button('r1').disabled).toBe(false);
  });

  it('undoing a deactivation sends nothing', async () => {
    const { fixture, host } = await render();
    const notice = TestBed.inject(UndoNotice);

    host.querySelector<HTMLButtonElement>('[data-testid="toggle-r1"]')!.click();
    await fixture.whenStable();
    notice.undo(notice.active()[0]);
    await fixture.whenStable();

    expect(rows(host)[0]).not.toContain('Inactivo');
  });

  it('reactivates straight away with the version of the reason', async () => {
    const { fixture, host, http } = await render();

    host.querySelector<HTMLButtonElement>('[data-testid="toggle-r2"]')!.click();
    const on = http.expectOne(`${ADMIN}/reasons/r2/reactivate`);
    expect(on.request.headers.get('If-Match')).toBe('"4"');
    on.flush({ ...mistake, active: true, etag: '"5"' });
    await fixture.whenStable();

    expect(rows(host)[1]).not.toContain('Inactivo');
  });

  it('re-enables the button and reloads the current type when a deactivation is stale', async () => {
    const { fixture, host, http } = await render();

    clickPastUndo(host.querySelector<HTMLButtonElement>('[data-testid="toggle-r1"]')!);
    http
      .expectOne(`${ADMIN}/reasons/r1/deactivate`)
      .flush(stale, { status: 412, statusText: 'Precondition Failed' });
    await fixture.whenStable();
    http
      .expectOne(`${ADMIN}/reasons?type=VOID`)
      .flush([{ ...spilled, code: 'Fresh', etag: '"9"' }, mistake]);
    await fixture.whenStable();

    expect(rows(host)[0]).toContain('Fresh');
    expect(rows(host)[0]).not.toContain('Inactivo');
    expect(host.querySelector<HTMLButtonElement>('[data-testid="toggle-r1"]')!.disabled).toBe(false);
  });
});
