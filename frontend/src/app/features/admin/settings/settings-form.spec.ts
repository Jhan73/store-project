import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { StoreSettings } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import {
  chooseOption,
  numberIsInvalid,
  numberText,
  selectedLabel,
  typeNumber,
} from '../../../testing/primeng-controls';
import { SettingsApi } from './settings-api';
import { SettingsForm } from './settings-form';

const URL = 'http://api.test/api/v1/admin/settings';

const settings: StoreSettings = {
  id: 'st',
  timeZone: 'America/Lima',
  currency: 'PEN',
  basePrepMinutes: 10,
  queueMinutesPerOrder: 2,
  busyModeMinutes: 15,
  boardWarningMinutes: 8,
  boardLateMinutes: 12,
  registerDifferenceThreshold: { amount: '5.00', currency: 'PEN' },
  exceptionThreshold: 3,
  onlineCapacityLimit: 20,
};

const stale = {
  type: 'about:blank',
  status: 412,
  code: 'common.precondition-failed',
  correlationId: 'c',
};

async function render() {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      MessageService,
      SettingsApi,
      { provide: API_ORIGIN, useValue: 'http://api.test' },
    ],
  });
  const fixture = TestBed.createComponent(SettingsForm);
  const http = TestBed.inject(HttpTestingController);
  const messages = TestBed.inject(MessageService);
  const toast = vi.spyOn(messages, 'add');
  await fixture.whenStable();
  http.expectOne(URL).flush(settings, { headers: { ETag: '"4"' } });
  await fixture.whenStable();
  return { fixture, host: fixture.nativeElement as HTMLElement, http, toast };
}

function type(host: HTMLElement, selector: string, value: string) {
  const input = host.querySelector<HTMLInputElement>(selector)!;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

function submit(host: HTMLElement) {
  host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
}

function value(host: HTMLElement, selector: string) {
  return host.querySelector<HTMLInputElement>(selector)!.value;
}


describe('SettingsForm', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('shows every setting as the server holds it', async () => {
    const { host } = await render();

    expect(selectedLabel(host, 'settings-timeZone')).toBe('America/Lima');
    expect(value(host, '#settings-currency')).toBe('PEN');
    expect(numberText(host, 'settings-basePrepMinutes')).toBe('10');
    expect(numberText(host, 'settings-queueMinutesPerOrder')).toBe('2');
    expect(numberText(host, 'settings-busyModeMinutes')).toBe('15');
    expect(numberText(host, 'settings-onlineCapacityLimit')).toBe('20');
    expect(numberText(host, 'settings-boardWarningMinutes')).toBe('8');
    expect(numberText(host, 'settings-boardLateMinutes')).toBe('12');
    expect(value(host, '#settings-registerDifferenceThreshold')).toBe('5.00');
    expect(numberText(host, 'settings-exceptionThreshold')).toBe('3');
  });

  it('warns about delivery zones only while the currency differs from the saved one', async () => {
    const { fixture, host, http } = await render();
    const warning = () => host.querySelector('#settings-currency-warning');

    expect(warning()).toBeNull();
    type(host, '#settings-currency', 'usd');
    await fixture.whenStable();
    expect(warning()?.textContent).toContain('zonas de reparto');

    type(host, '#settings-currency', ' pen ');
    await fixture.whenStable();
    expect(warning()).toBeNull();

    type(host, '#settings-currency', 'usd');
    submit(host);
    http.expectOne(URL).flush({ ...settings, currency: 'USD' }, { headers: { ETag: '"5"' } });
    await fixture.whenStable();
    expect(warning()).toBeNull();
  });

  it('saves untouched values back unchanged, guarded by the ETag of the read', async () => {
    const { fixture, host, http } = await render();

    submit(host);
    const request = http.expectOne(URL);
    expect(request.request.method).toBe('PUT');
    expect(request.request.headers.get('If-Match')).toBe('"4"');
    const { id, ...expected } = settings;
    expect(id).toBe('st');
    expect(request.request.body).toEqual(expected);
    request.flush(settings, { headers: { ETag: '"5"' } });
    await fixture.whenStable();

    expect(host.querySelector('[role="status"]')?.textContent).toContain('Cambios guardados');
  });

  it('sends every edited setting and shows what the server answered', async () => {
    const { fixture, host, http } = await render();

    await chooseOption(fixture, host, 'settings-timeZone', 'America/Bogota');
    type(host, '#settings-currency', 'usd');
    typeNumber(host, 'settings-basePrepMinutes', '12');
    typeNumber(host, 'settings-queueMinutesPerOrder', '0');
    typeNumber(host, 'settings-busyModeMinutes', '20');
    typeNumber(host, 'settings-onlineCapacityLimit', '30');
    typeNumber(host, 'settings-boardWarningMinutes', '6');
    typeNumber(host, 'settings-boardLateMinutes', '9');
    type(host, '#settings-registerDifferenceThreshold', '7,5');
    typeNumber(host, 'settings-exceptionThreshold', '4');
    await fixture.whenStable();
    submit(host);
    const request = http.expectOne(URL);
    expect(request.request.body).toEqual({
      timeZone: 'America/Bogota',
      currency: 'USD',
      basePrepMinutes: 12,
      queueMinutesPerOrder: 0,
      busyModeMinutes: 20,
      onlineCapacityLimit: 30,
      boardWarningMinutes: 6,
      boardLateMinutes: 9,
      registerDifferenceThreshold: { amount: '7.50', currency: 'USD' },
      exceptionThreshold: 4,
    });
    request.flush(
      { ...settings, basePrepMinutes: 12, registerDifferenceThreshold: { amount: '7.50', currency: 'PEN' } },
      { headers: { ETag: '"5"' } },
    );
    await fixture.whenStable();

    expect(value(host, '#settings-registerDifferenceThreshold')).toBe('7.50');
    expect(numberText(host, 'settings-boardLateMinutes')).toBe('12');
  });

  it('uses the ETag of the last save on the next one', async () => {
    const { fixture, host, http } = await render();

    submit(host);
    http.expectOne(URL).flush(settings, { headers: { ETag: '"5"' } });
    await fixture.whenStable();
    submit(host);

    expect(http.expectOne(URL).request.headers.get('If-Match')).toBe('"5"');
  });

  it('does not send a setting that is not a valid number', async () => {
    const { fixture, host, http } = await render();

    typeNumber(host, 'settings-basePrepMinutes', '0');
    type(host, '#settings-registerDifferenceThreshold', 'abc');
    submit(host);
    await fixture.whenStable();

    http.expectNone(URL);
    expect(numberIsInvalid(host, 'settings-basePrepMinutes')).toBe(true);
    expect(
      host.querySelector('#settings-registerDifferenceThreshold')!.getAttribute('aria-invalid'),
    ).toBe('true');
  });

  it('does not send board thresholds where late is not after warning', async () => {
    const { fixture, host, http } = await render();

    typeNumber(host, 'settings-boardWarningMinutes', '12');
    typeNumber(host, 'settings-boardLateMinutes', '12');
    submit(host);
    await fixture.whenStable();

    http.expectNone(URL);
    expect(host.textContent).toContain('El tiempo de atraso debe ser mayor');
  });

  it('marks the fields the server rejected', async () => {
    const { fixture, host, http } = await render();

    submit(host);
    http.expectOne(URL).flush(
      {
        type: 'about:blank',
        status: 400,
        code: 'common.validation-failed',
        correlationId: 'c',
        errors: [{ field: 'currency', constraint: 'Currency' }],
      },
      { status: 400, statusText: 'Bad Request' },
    );
    await fixture.whenStable();

    expect(host.querySelector('#settings-currency')!.getAttribute('aria-invalid')).toBe('true');
  });

  it.each([
    ['412', 412, 'common.precondition-failed'],
    ['409', 409, 'common.concurrent-modification'],
  ])(
    're-reads after a stale save (%s), shows the server values and saves again with the new ETag',
    async (_name, status, code) => {
      const { fixture, host, http, toast } = await render();

      typeNumber(host, 'settings-basePrepMinutes', '99');
      submit(host);
      http
        .expectOne(URL)
        .flush({ ...stale, status, code }, { status, statusText: 'Conflict' });
      await fixture.whenStable();
      http
        .expectOne(URL)
        .flush({ ...settings, basePrepMinutes: 11 }, { headers: { ETag: '"9"' } });
      await fixture.whenStable();

      expect(toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
      expect(numberText(host, 'settings-basePrepMinutes')).toBe('11');
      submit(host);
      const retry = http.expectOne(URL);
      expect(retry.request.headers.get('If-Match')).toBe('"9"');
      retry.flush(settings, { headers: { ETag: '"10"' } });
    },
  );

  it('ignores a second submit while the first is in flight', async () => {
    const { fixture, host, http } = await render();

    submit(host);
    submit(host);

    const request = http.expectOne(URL);
    request.flush(settings, { headers: { ETag: '"5"' } });
    await fixture.whenStable();
  });

  it('keeps the form editable and tells the user when saving fails for another reason', async () => {
    const { fixture, host, http, toast } = await render();

    typeNumber(host, 'settings-basePrepMinutes', '12');
    submit(host);
    http.expectOne(URL).flush(
      { type: 'about:blank', status: 422, code: 'store.invalid-board-thresholds', correlationId: 'c' },
      { status: 422, statusText: 'Unprocessable' },
    );
    await fixture.whenStable();

    expect(toast).toHaveBeenCalled();
    expect(numberText(host, 'settings-basePrepMinutes')).toBe('12');
    submit(host);
    expect(http.expectOne(URL).request.headers.get('If-Match')).toBe('"4"');
  });
});
