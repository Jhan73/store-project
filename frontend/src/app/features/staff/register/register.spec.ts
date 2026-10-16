import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { Register } from './register';
import { RegisterApi } from './register-api';
import { pen, problem, shift } from '../../../testing/register-fixtures';

const BASE = 'http://api.test/api/v1';
const notFound = problem(404, 'instore.shift-not-found');

describe('Register', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
        MessageService,
        RegisterApi,
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const host = (fixture: ComponentFixture<Register>) => fixture.nativeElement as HTMLElement;
  const find = <T extends HTMLElement>(fixture: ComponentFixture<Register>, testId: string) =>
    host(fixture).querySelector<T>(`[data-testid="${testId}"]`);
  const text = (fixture: ComponentFixture<Register>, testId: string) => find(fixture, testId)?.textContent ?? '';

  async function openScreen(current: ReturnType<typeof shift> | null) {
    const fixture = TestBed.createComponent(Register);
    await fixture.whenStable();
    const read = http.expectOne(`${BASE}/shifts/current`);
    if (current) {
      read.flush(current);
    } else {
      read.flush(notFound, { status: 404, statusText: 'Not Found' });
    }
    http.expectOne(`${BASE}/store/board-thresholds`).flush({ timeZone: 'America/Lima' });
    if (!current) {
      http.expectOne(`${BASE}/catalog/menu`).flush({
        categories: [{ id: 'c', products: [{ id: 'p', price: pen('9.00') }] }],
      });
    }
    await fixture.whenStable();
    if (current) {
      flushPanels();
      await fixture.whenStable();
    }
    return fixture;
  }

  function flushPanels() {
    http.expectOne(`${BASE}/shifts/s1/cash-movements`).flush([]);
    http.expectOne(`${BASE}/reasons?type=CASH_OUT`).flush([]);
  }

  it('offers to open a shift when none is open', async () => {
    const fixture = await openScreen(null);

    expect(find(fixture, 'open-form')).not.toBeNull();
    expect(find(fixture, 'shift-summary')).toBeNull();
  });

  it('opens a shift with the typed float', async () => {
    const fixture = await openScreen(null);
    const input = find<HTMLInputElement>(fixture, 'float')!;
    input.value = '50';
    input.dispatchEvent(new Event('input'));

    find<HTMLButtonElement>(fixture, 'open-submit')!.click();
    await fixture.whenStable();

    const request = http.expectOne(`${BASE}/shifts`);
    expect(request.request.body).toEqual({ openingFloat: pen('50.00') });
    expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
    request.flush(shift(), { status: 201, statusText: 'Created' });
    await fixture.whenStable();
    flushPanels();
    await fixture.whenStable();

    expect(find(fixture, 'open-form')).toBeNull();
    expect(text(fixture, 'shift-summary')).toContain('50.00');
  });

  it('refuses a float that is not an amount without calling the API', async () => {
    const fixture = await openScreen(null);
    const input = find<HTMLInputElement>(fixture, 'float')!;
    input.value = 'abc';
    input.dispatchEvent(new Event('input'));

    find<HTMLButtonElement>(fixture, 'open-submit')!.click();
    await fixture.whenStable();

    expect(find(fixture, 'float-error')).not.toBeNull();
  });

  it('shows the open shift with its opening float and never an expected cash', async () => {
    const fixture = await openScreen(shift());

    expect(text(fixture, 'shift-summary')).toContain('50.00');
    expect(text(fixture, 'shift-summary')).toContain('9:00');
    expect(find(fixture, 'open-form')).toBeNull();
    expect(find(fixture, 'expected-cash')).toBeNull();
  });

  it('lets a failed read be retried', async () => {
    const fixture = TestBed.createComponent(Register);
    await fixture.whenStable();
    http.expectOne(`${BASE}/shifts/current`).flush(
      { type: 'about:blank', status: 500, code: 'common.internal-error', correlationId: 'c' },
      { status: 500, statusText: 'Server Error' },
    );
    http.expectOne(`${BASE}/store/board-thresholds`).flush({ timeZone: 'America/Lima' });
    await fixture.whenStable();

    expect(find(fixture, 'load-failed')).not.toBeNull();
    find<HTMLButtonElement>(fixture, 'retry')!.click();
    http.expectOne(`${BASE}/shifts/current`).flush(shift());
    http.expectOne(`${BASE}/store/board-thresholds`).flush({ timeZone: 'America/Lima' });
    await fixture.whenStable();
    flushPanels();

    expect(find(fixture, 'shift-summary')).not.toBeNull();
  });

  it('shows the cash movements of the open shift and none without a shift', async () => {
    const open = await openScreen(shift());
    expect(host(open).querySelector('app-cash-movements')).not.toBeNull();
  });

  it('closes the shift from a dialog and keeps the closed shift read-only on the screen', async () => {
    const fixture = await openScreen(shift());

    find<HTMLButtonElement>(fixture, 'close-open')!.click();
    await fixture.whenStable();
    const count = document.body.querySelector<HTMLInputElement>('[data-testid="close-count"]')!;
    count.value = '100';
    count.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    document.body.querySelector<HTMLButtonElement>('[data-testid="close-confirm"]')!.click();
    await fixture.whenStable();
    http.expectOne(`${BASE}/shifts/s1/close`).flush(
      shift({
        status: 'CLOSED',
        closeKind: 'COUNTED',
        closedAt: '2026-10-13T22:00:00Z',
        countedCash: pen('100.00'),
        expectedCash: pen('100.00'),
        difference: pen('0.00'),
      }),
    );
    await fixture.whenStable();
    document.body.querySelector<HTMLButtonElement>('[data-testid="close-done"]')!.click();
    await fixture.whenStable();

    expect(find(fixture, 'shift-summary')).toBeNull();
    expect(text(fixture, 'closed-summary')).toContain('100.00');
    expect(find(fixture, 'close-open')).toBeNull();
    expect(host(fixture).querySelector('app-cash-movements')).toBeNull();

    find<HTMLButtonElement>(fixture, 'new-shift')!.click();
    await fixture.whenStable();

    expect(find(fixture, 'open-form')).not.toBeNull();
  });

  it('shows no cash movements panel without a shift', async () => {
    const none = await openScreen(null);
    expect(host(none).querySelector('app-cash-movements')).toBeNull();
  });
});
