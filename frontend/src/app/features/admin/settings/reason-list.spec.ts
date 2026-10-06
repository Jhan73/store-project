import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { Reason } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
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

function type(host: HTMLElement, selector: string, value: string) {
  const input = host.querySelector<HTMLInputElement>(selector)!;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

function chooseType(host: HTMLElement, value: string) {
  const select = host.querySelector<HTMLSelectElement>('#reason-type')!;
  select.value = value;
  select.dispatchEvent(new Event('change'));
}

function submit(host: HTMLElement) {
  host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
}

function rows(host: HTMLElement) {
  return Array.from(host.querySelectorAll('tbody tr')).map((row) => row.textContent ?? '');
}

describe('ReasonList', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('lists the reasons of the first type with their state', async () => {
    const { host } = await render();

    const [first, second] = rows(host);
    expect(first).toContain('Spilled');
    expect(first).toContain('Activo');
    expect(second).toContain('Wrong order');
    expect(second).toContain('Inactivo');
  });

  it('loads the reasons of the type that is picked', async () => {
    const { fixture, host, http } = await render();

    chooseType(host, 'CASH_OUT');
    http
      .expectOne(`${ADMIN}/reasons?type=CASH_OUT`)
      .flush([{ ...spilled, id: 'r9', type: 'CASH_OUT', code: 'Supplier' }]);
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(1);
    expect(rows(host)[0]).toContain('Supplier');
  });

  it('shows only the answer to the latest pick when answers arrive out of order', async () => {
    const { fixture, host, http } = await render();

    chooseType(host, 'COMP');
    chooseType(host, 'CASH_OUT');
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

    chooseType(host, 'COMP');
    http.expectOne(`${ADMIN}/reasons?type=COMP`).flush([]);
    await fixture.whenStable();
    type(host, '#reason-code', ' Birthday ');
    submit(host);
    const request = http.expectOne(`${ADMIN}/reasons`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ type: 'COMP', code: 'Birthday' });
    request.flush({ ...spilled, id: 'r3', type: 'COMP', code: 'Birthday' });
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(1);
    expect(rows(host)[0]).toContain('Birthday');
    expect(host.querySelector<HTMLInputElement>('#reason-code')!.value).toBe('');
  });

  it('does not send a blank code', async () => {
    const { fixture, host, http } = await render([]);

    type(host, '#reason-code', '   ');
    submit(host);
    await fixture.whenStable();

    http.expectNone(`${ADMIN}/reasons`);
    expect(host.querySelector('#reason-code')!.getAttribute('aria-invalid')).toBe('true');
  });

  it('keeps the typed code and tells the user when the code is already used', async () => {
    const { fixture, host, http } = await render([]);

    type(host, '#reason-code', 'Spilled');
    submit(host);
    http.expectOne(`${ADMIN}/reasons`).flush(
      { type: 'about:blank', status: 409, code: 'store.reason-code-already-used', correlationId: 'c' },
      { status: 409, statusText: 'Conflict' },
    );
    await fixture.whenStable();

    expect(host.querySelector<HTMLInputElement>('#reason-code')!.value).toBe('Spilled');
    expect(rows(host)).toHaveLength(0);
  });

  it('deactivates and reactivates with the version of the reason and blocks the button meanwhile', async () => {
    const { fixture, host, http } = await render();
    const button = (id: string) =>
      host.querySelector<HTMLButtonElement>(`[data-testid="toggle-${id}"]`)!;

    button('r1').click();
    await fixture.whenStable();
    expect(button('r1').disabled).toBe(true);
    button('r1').click();
    const off = http.expectOne(`${ADMIN}/reasons/r1/deactivate`);
    expect(off.request.headers.get('If-Match')).toBe('"1"');
    off.flush({ ...spilled, active: false, etag: '"2"' });
    await fixture.whenStable();
    expect(rows(host)[0]).toContain('Inactivo');
    expect(button('r1').disabled).toBe(false);

    button('r2').click();
    const on = http.expectOne(`${ADMIN}/reasons/r2/reactivate`);
    expect(on.request.headers.get('If-Match')).toBe('"4"');
    on.flush({ ...mistake, active: true, etag: '"5"' });
    await fixture.whenStable();
    expect(rows(host)[1]).toContain('Activo');
  });

  it('uses the version returned by the last toggle on the next one', async () => {
    const { fixture, host, http } = await render();
    const button = host.querySelector<HTMLButtonElement>('[data-testid="toggle-r1"]')!;

    button.click();
    http
      .expectOne(`${ADMIN}/reasons/r1/deactivate`)
      .flush({ ...spilled, active: false, etag: '"2"' });
    await fixture.whenStable();
    button.click();

    expect(http.expectOne(`${ADMIN}/reasons/r1/reactivate`).request.headers.get('If-Match')).toBe(
      '"2"',
    );
  });

  it('re-enables the button and reloads the current type when a toggle is stale', async () => {
    const { fixture, host, http } = await render();

    host.querySelector<HTMLButtonElement>('[data-testid="toggle-r1"]')!.click();
    http
      .expectOne(`${ADMIN}/reasons/r1/deactivate`)
      .flush(stale, { status: 412, statusText: 'Precondition Failed' });
    await fixture.whenStable();
    http
      .expectOne(`${ADMIN}/reasons?type=VOID`)
      .flush([{ ...spilled, code: 'Fresh', etag: '"9"' }, mistake]);
    await fixture.whenStable();

    expect(rows(host)[0]).toContain('Fresh');
    expect(host.querySelector<HTMLButtonElement>('[data-testid="toggle-r1"]')!.disabled).toBe(false);
  });
});
