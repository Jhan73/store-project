import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { DeliveryZone } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { UndoNotice } from '../../../shared/state/undo-notice';
import { numberIsInvalid, typeNumber } from '../../../testing/primeng-controls';
import { clickPastUndo } from '../../../testing/undo-window';
import { SettingsApi } from './settings-api';
import { ZoneList } from './zone-list';

const ADMIN = 'http://api.test/api/v1/admin';

const centro: DeliveryZone = {
  id: 'z1',
  name: 'Centro',
  fee: { amount: '4.00', currency: 'PEN' },
  deliveryMinutes: 30,
  minimumOrder: { amount: '15.00', currency: 'PEN' },
  freeDeliveryThreshold: null,
  active: true,
  etag: '"2"',
};
const norte: DeliveryZone = {
  ...centro,
  id: 'z2',
  name: 'Norte',
  fee: { amount: '6.50', currency: 'PEN' },
  minimumOrder: null,
  freeDeliveryThreshold: { amount: '50.00', currency: 'PEN' },
};

const stale = {
  type: 'about:blank',
  status: 412,
  code: 'common.precondition-failed',
  correlationId: 'c',
};

async function render(zones: DeliveryZone[] = [centro, norte]) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      MessageService,
      SettingsApi,
      { provide: API_ORIGIN, useValue: 'http://api.test' },
    ],
  });
  const fixture = TestBed.createComponent(ZoneList);
  const http = TestBed.inject(HttpTestingController);
  await fixture.whenStable();
  http.expectOne(`${ADMIN}/delivery-zones`).flush(zones);
  http.expectOne(`${ADMIN}/settings`).flush({ currency: 'PEN' }, { headers: { ETag: '"1"' } });
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

describe('ZoneList', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('lists the zones with their fee, delivery time and state', async () => {
    const { host } = await render([centro, { ...norte, active: false }]);

    const [first, second] = rows(host);
    expect(first).toContain('Centro');
    expect(first).toContain('4.00');
    expect(first).toContain('30');
    expect(first).toContain('15.00');
    expect(first).toContain('Activa');
    expect(second).toContain('Norte');
    expect(second).toContain('50.00');
    expect(second).toContain('Inactiva');
  });

  it('opens the form in a dialog and creates a zone with the store currency', async () => {
    const { fixture, host, http } = await render([]);

    await open(fixture, host, 'add-zone');
    type('#zone-name', ' Sur ');
    type('#zone-fee', '5,5');
    typeNumber(body(), 'zone-minutes', '45');
    submit();
    const request = http.expectOne(`${ADMIN}/delivery-zones`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      name: 'Sur',
      fee: { amount: '5.50', currency: 'PEN' },
      deliveryMinutes: 45,
      minimumOrder: null,
      freeDeliveryThreshold: null,
    });
    request.flush({ ...centro, id: 'z3', name: 'Sur' });
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(1);
    expect(rows(host)[0]).toContain('Sur');
    expect(body().querySelector('#zone-name')).toBeNull();
  });

  it('sends the optional amounts when they are filled in', async () => {
    const { fixture, host, http } = await render([]);

    await open(fixture, host, 'add-zone');
    type('#zone-name', 'Sur');
    type('#zone-fee', '0');
    typeNumber(body(), 'zone-minutes', '45');
    type('#zone-minimum', '20');
    type('#zone-free', '60,5');
    submit();

    expect(http.expectOne(`${ADMIN}/delivery-zones`).request.body).toEqual({
      name: 'Sur',
      fee: { amount: '0.00', currency: 'PEN' },
      deliveryMinutes: 45,
      minimumOrder: { amount: '20.00', currency: 'PEN' },
      freeDeliveryThreshold: { amount: '60.50', currency: 'PEN' },
    });
  });

  it('does not send a zone with a bad name, fee, time or optional amount', async () => {
    const { fixture, host, http } = await render([]);

    await open(fixture, host, 'add-zone');
    type('#zone-name', '   ');
    type('#zone-fee', 'abc');
    typeNumber(body(), 'zone-minutes', '0');
    type('#zone-minimum', 'x');
    submit();
    await fixture.whenStable();

    http.expectNone(`${ADMIN}/delivery-zones`);
    expect(numberIsInvalid(body(), 'zone-minutes')).toBe(true);
    for (const id of ['name', 'fee', 'minimum']) {
      expect(body().querySelector(`#zone-${id}`)!.getAttribute('aria-invalid')).toBe('true');
    }
    expect(body().querySelector('#zone-fee')!.closest('app-form-field')!.textContent).toContain(
      'Escribe un monto',
    );
  });

  it('edits a zone with the version it was listed with and can clear an optional amount', async () => {
    const { fixture, host, http } = await render();

    await open(fixture, host, 'edit-z1');
    expect(value('#zone-name')).toBe('Centro');
    expect(value('#zone-fee')).toBe('4.00');
    expect(value('#zone-minimum')).toBe('15.00');
    type('#zone-minimum', '');
    typeNumber(body(), 'zone-minutes', '35');
    submit();
    const request = http.expectOne(`${ADMIN}/delivery-zones/z1`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.headers.get('If-Match')).toBe('"2"');
    expect(request.request.body).toEqual({
      name: 'Centro',
      fee: { amount: '4.00', currency: 'PEN' },
      deliveryMinutes: 35,
      minimumOrder: null,
      freeDeliveryThreshold: null,
    });
    request.flush({ ...centro, deliveryMinutes: 35, minimumOrder: null, etag: '"3"' });
    await fixture.whenStable();

    expect(rows(host)[0]).toContain('35');
    expect(body().querySelector('#zone-name')).toBeNull();
  });

  it('shows a deactivated zone as inactive at once and sends it with its version after the undo window', async () => {
    const { fixture, host, http } = await render();

    clickPastUndo(button(host, 'toggle-z1'));
    await fixture.whenStable();
    expect(rows(host)[0]).toContain('Inactiva');
    expect(button(host, 'toggle-z1').disabled).toBe(true);
    const off = http.expectOne(`${ADMIN}/delivery-zones/z1/deactivate`);
    expect(off.request.headers.get('If-Match')).toBe('"2"');
    off.flush({ ...centro, active: false, etag: '"3"' });
    await fixture.whenStable();

    expect(rows(host)[0]).toContain('Inactiva');
    expect(button(host, 'toggle-z1').disabled).toBe(false);
  });

  it('undoing a deactivation sends nothing', async () => {
    const { fixture, host } = await render();
    const notice = TestBed.inject(UndoNotice);

    button(host, 'toggle-z1').click();
    await fixture.whenStable();
    expect(rows(host)[0]).toContain('Inactiva');
    notice.undo(notice.active()[0]);
    await fixture.whenStable();

    expect(rows(host)[0]).toContain('Activa');
    expect(rows(host)[0]).not.toContain('Inactiva');
  });

  it('reactivates straight away with the version of the zone and blocks the button meanwhile', async () => {
    const { fixture, host, http } = await render([centro, { ...norte, active: false }]);

    button(host, 'toggle-z2').click();
    await fixture.whenStable();
    expect(button(host, 'toggle-z2').disabled).toBe(true);
    const on = http.expectOne(`${ADMIN}/delivery-zones/z2/reactivate`);
    expect(on.request.headers.get('If-Match')).toBe('"2"');
    on.flush({ ...norte, active: true, etag: '"3"' });
    await fixture.whenStable();

    expect(rows(host)[1]).toContain('Activa');
    expect(rows(host)[1]).not.toContain('Inactiva');
  });

  it('reloads when a deactivation is stale and shows the zone as the server has it', async () => {
    const { fixture, host, http } = await render();

    clickPastUndo(button(host, 'toggle-z1'));
    http
      .expectOne(`${ADMIN}/delivery-zones/z1/deactivate`)
      .flush(stale, { status: 412, statusText: 'Precondition Failed' });
    await fixture.whenStable();
    http.expectOne(`${ADMIN}/delivery-zones`).flush([{ ...centro, name: 'Fresh', etag: '"9"' }, norte]);
    await fixture.whenStable();

    expect(rows(host)[0]).toContain('Fresh');
    expect(rows(host)[0]).not.toContain('Inactiva');
    expect(button(host, 'toggle-z1').disabled).toBe(false);
  });

  it('retries a stale save with the refreshed version and shows the current values', async () => {
    const { fixture, host, http } = await render();

    await open(fixture, host, 'edit-z1');
    type('#zone-name', 'Mine');
    submit();
    http
      .expectOne(`${ADMIN}/delivery-zones/z1`)
      .flush(stale, { status: 412, statusText: 'Precondition Failed' });
    await fixture.whenStable();
    http.expectOne(`${ADMIN}/delivery-zones`).flush([{ ...centro, name: 'Fresh', etag: '"9"' }, norte]);
    await fixture.whenStable();

    expect(value('#zone-name')).toBe('Fresh');
    submit();
    const retry = http.expectOne(`${ADMIN}/delivery-zones/z1`);
    expect(retry.request.headers.get('If-Match')).toBe('"9"');
    retry.flush({ ...centro, name: 'Fresh', etag: '"10"' });
  });

  it('closes the dialog when the zone no longer exists after a failed save', async () => {
    const { fixture, host, http } = await render();

    await open(fixture, host, 'edit-z1');
    submit();
    http.expectOne(`${ADMIN}/delivery-zones/z1`).flush(
      { type: 'about:blank', status: 404, code: 'store.delivery-zone-not-found', correlationId: 'c' },
      { status: 404, statusText: 'Not Found' },
    );
    await fixture.whenStable();
    http.expectOne(`${ADMIN}/delivery-zones`).flush([norte]);
    await fixture.whenStable();

    expect(body().querySelector('#zone-name')).toBeNull();
  });

  it('blocks the buttons while a save is in flight', async () => {
    const { fixture, host, http } = await render();

    await open(fixture, host, 'edit-z1');
    submit();
    await fixture.whenStable();
    expect(button(host, 'edit-z2').disabled).toBe(true);
    http.expectOne(`${ADMIN}/delivery-zones/z1`).flush({ ...centro, etag: '"3"' });
    await fixture.whenStable();

    expect(button(host, 'edit-z2').disabled).toBe(false);
  });

  it('applies only the latest reload when answers arrive out of order', async () => {
    const { fixture, host, http } = await render();

    clickPastUndo(button(host, 'toggle-z1'));
    clickPastUndo(button(host, 'toggle-z2'));
    http
      .expectOne(`${ADMIN}/delivery-zones/z1/deactivate`)
      .flush(stale, { status: 412, statusText: 'Precondition Failed' });
    http
      .expectOne(`${ADMIN}/delivery-zones/z2/deactivate`)
      .flush(stale, { status: 412, statusText: 'Precondition Failed' });
    await fixture.whenStable();
    const [first, second] = http.match(`${ADMIN}/delivery-zones`);
    second.flush([{ ...centro, name: 'Newest' }]);
    first.flush([{ ...centro, name: 'Older' }]);
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(1);
    expect(rows(host)[0]).toContain('Newest');
  });

  it('marks the field the server rejected', async () => {
    const { fixture, host, http } = await render([]);

    await open(fixture, host, 'add-zone');
    type('#zone-name', 'Sur');
    type('#zone-fee', '5');
    typeNumber(body(), 'zone-minutes', '45');
    submit();
    http.expectOne(`${ADMIN}/delivery-zones`).flush(
      {
        type: 'about:blank',
        status: 400,
        code: 'common.validation-failed',
        correlationId: 'c',
        errors: [{ field: 'name', constraint: 'NotBlank' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(body().querySelector('#zone-name')!.getAttribute('aria-invalid')).toBe('true');
  });
});
