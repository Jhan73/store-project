import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { DayOfWeek, OpeningHour } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { OpeningHoursForm } from './opening-hours-form';
import { SettingsApi } from './settings-api';

const URL = 'http://api.test/api/v1/admin/settings/opening-hours';

const WEEK: DayOfWeek[] = [
  'MONDAY',
  'TUESDAY',
  'WEDNESDAY',
  'THURSDAY',
  'FRIDAY',
  'SATURDAY',
  'SUNDAY',
];

function week(overrides: Partial<Record<DayOfWeek, Partial<OpeningHour>>> = {}): OpeningHour[] {
  return WEEK.map((dayOfWeek) => ({
    dayOfWeek,
    closed: dayOfWeek === 'SUNDAY',
    opensAt: dayOfWeek === 'SUNDAY' ? null : '08:00:00',
    closesAt: dayOfWeek === 'SUNDAY' ? null : '20:00:00',
    ...overrides[dayOfWeek],
  }));
}

function body(overrides: Partial<Record<DayOfWeek, Partial<OpeningHour>>> = {}) {
  return week(overrides);
}

const stale = {
  type: 'about:blank',
  status: 412,
  code: 'common.precondition-failed',
  correlationId: 'c',
};

async function render(hours: OpeningHour[] = week()) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      MessageService,
      SettingsApi,
      { provide: API_ORIGIN, useValue: 'http://api.test' },
    ],
  });
  const fixture = TestBed.createComponent(OpeningHoursForm);
  const http = TestBed.inject(HttpTestingController);
  const toast = vi.spyOn(TestBed.inject(MessageService), 'add');
  await fixture.whenStable();
  http.expectOne(URL).flush(hours, { headers: { ETag: '"7"' } });
  await fixture.whenStable();
  return { fixture, host: fixture.nativeElement as HTMLElement, http, toast };
}

function input(host: HTMLElement, id: string) {
  return host.querySelector<HTMLInputElement>(`#${id}`)!;
}

function closedBox(host: HTMLElement, day: string) {
  return host.querySelector<HTMLInputElement>(`p-checkbox #hours-${day}-closed`)!;
}

function type(host: HTMLElement, id: string, value: string) {
  const field = input(host, id);
  field.value = value;
  field.dispatchEvent(new Event('input'));
}

function toggleClosed(host: HTMLElement, day: DayOfWeek) {
  closedBox(host, day).click();
}

function submit(host: HTMLElement) {
  host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
}

describe('OpeningHoursForm', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('shows the seven days in week order whatever order the server answered in', async () => {
    const { host } = await render(week().reverse());

    const labels = Array.from(host.querySelectorAll('tbody th')).map((cell) => cell.textContent);
    expect(labels).toEqual(['Lunes', 'Martes', 'Miércoles', 'Jueves', 'Viernes', 'Sábado', 'Domingo']);
    expect(input(host, 'hours-MONDAY-opens').classList.contains('p-inputtext')).toBe(true);
    expect(input(host, 'hours-MONDAY-opens').value).toBe('08:00');
    expect(input(host, 'hours-MONDAY-closes').value).toBe('20:00');
    expect(closedBox(host, 'SUNDAY').checked).toBe(true);
    expect(input(host, 'hours-SUNDAY-opens').disabled).toBe(true);
    expect(input(host, 'hours-SUNDAY-closes').disabled).toBe(true);
  });

  it('saves the week untouched, guarded by the ETag of the read', async () => {
    const { host, http } = await render();

    submit(host);
    const request = http.expectOne(URL);

    expect(request.request.method).toBe('PUT');
    expect(request.request.headers.get('If-Match')).toBe('"7"');
    expect(request.request.body).toEqual(body());
    request.flush(week(), { headers: { ETag: '"8"' } });
  });

  it('sends a closed day without times and an edited day with seconds', async () => {
    const { host, http } = await render();

    toggleClosed(host, 'MONDAY');
    type(host, 'hours-TUESDAY-opens', '09:30');
    type(host, 'hours-TUESDAY-closes', '23:15');
    toggleClosed(host, 'SUNDAY');
    type(host, 'hours-SUNDAY-opens', '10:00');
    type(host, 'hours-SUNDAY-closes', '14:00');
    submit(host);

    expect(http.expectOne(URL).request.body).toEqual(
      body({
        MONDAY: { closed: true, opensAt: null, closesAt: null },
        TUESDAY: { opensAt: '09:30:00', closesAt: '23:15:00' },
        SUNDAY: { closed: false, opensAt: '10:00:00', closesAt: '14:00:00' },
      }),
    );
  });

  it('does not send an open day that has no times', async () => {
    const { fixture, host, http } = await render();

    toggleClosed(host, 'SUNDAY');
    submit(host);
    await fixture.whenStable();

    http.expectNone(URL);
    expect(input(host, 'hours-SUNDAY-opens').getAttribute('aria-invalid')).toBe('true');
  });

  it('says so when a day closes after midnight', async () => {
    const { fixture, host } = await render();

    type(host, 'hours-FRIDAY-opens', '18:00');
    type(host, 'hours-FRIDAY-closes', '02:00');
    await fixture.whenStable();

    expect(host.querySelector('[data-testid="overnight-FRIDAY"]')).not.toBeNull();
    expect(host.querySelector('[data-testid="overnight-MONDAY"]')).toBeNull();
  });

  it.each([
    ['412', 412, 'common.precondition-failed'],
    ['409', 409, 'common.concurrent-modification'],
  ])(
    're-reads after a stale save (%s), shows the server week and saves again with the new ETag',
    async (_name, status, code) => {
      const { fixture, host, http, toast } = await render();

      type(host, 'hours-MONDAY-opens', '07:00');
      submit(host);
      http.expectOne(URL).flush({ ...stale, status, code }, { status, statusText: 'Conflict' });
      await fixture.whenStable();
      http
        .expectOne(URL)
        .flush(week({ MONDAY: { opensAt: '11:00:00' } }), { headers: { ETag: '"9"' } });
      await fixture.whenStable();

      expect(toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
      expect(input(host, 'hours-MONDAY-opens').value).toBe('11:00');
      submit(host);
      const retry = http.expectOne(URL);
      expect(retry.request.headers.get('If-Match')).toBe('"9"');
      retry.flush(week(), { headers: { ETag: '"10"' } });
    },
  );

  it('uses the ETag of the last save on the next one', async () => {
    const { fixture, host, http } = await render();

    submit(host);
    http.expectOne(URL).flush(week(), { headers: { ETag: '"8"' } });
    await fixture.whenStable();
    submit(host);

    expect(http.expectOne(URL).request.headers.get('If-Match')).toBe('"8"');
  });

  it('ignores a second submit while the first is in flight', async () => {
    const { host, http } = await render();

    submit(host);
    submit(host);

    http.expectOne(URL).flush(week(), { headers: { ETag: '"8"' } });
  });

  it('tells the user when the server rejects the week and keeps what was typed', async () => {
    const { fixture, host, http, toast } = await render();

    type(host, 'hours-MONDAY-opens', '07:00');
    submit(host);
    http.expectOne(URL).flush(
      { type: 'about:blank', status: 422, code: 'store.invalid-opening-hours', correlationId: 'c' },
      { status: 422, statusText: 'Unprocessable' },
    );
    await fixture.whenStable();

    expect(toast).toHaveBeenCalled();
    expect(input(host, 'hours-MONDAY-opens').value).toBe('07:00');
  });
});
