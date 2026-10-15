import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { CashMovement } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { chooseOption } from '../../../testing/primeng-controls';
import { pen, shift } from '../../../testing/register-fixtures';
import { CashMovements } from './cash-movements';
import { RegisterApi } from './register-api';
import { ShiftStore } from './shift-store';

const BASE = 'http://api.test/api/v1';
const MOVEMENTS = `${BASE}/shifts/s1/cash-movements`;

function movement(overrides: Partial<CashMovement> = {}): CashMovement {
  return {
    id: 'm1',
    shiftId: 's1',
    type: 'OUT',
    amount: pen('5.00'),
    reasonCode: 'Change',
    note: null,
    reference: 'F-100',
    recordedAt: '2026-10-13T15:30:00Z',
    recordedBy: 'u1',
    ...overrides,
  };
}

describe('CashMovements', () => {
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        MessageService,
        RegisterApi,
        ShiftStore,
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  const host = (fixture: ComponentFixture<CashMovements>) => fixture.nativeElement as HTMLElement;
  const find = <T extends HTMLElement>(fixture: ComponentFixture<CashMovements>, testId: string) =>
    host(fixture).querySelector<T>(`[data-testid="${testId}"]`);
  const type = (fixture: ComponentFixture<CashMovements>, testId: string, value: string) => {
    const input = find<HTMLInputElement | HTMLTextAreaElement>(fixture, testId)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };

  async function open(movements: CashMovement[] = [], reasons = [{ code: 'Change' }, { code: 'Other' }]) {
    const store = TestBed.inject(ShiftStore);
    store.load();
    http.expectOne(`${BASE}/shifts/current`).flush(shift());
    http.expectOne(`${BASE}/store/board-thresholds`).flush({ timeZone: 'America/Lima' });
    const fixture = TestBed.createComponent(CashMovements);
    await fixture.whenStable();
    http.expectOne(MOVEMENTS).flush(movements);
    http.expectOne(`${BASE}/reasons?type=CASH_OUT`).flush(reasons);
    await fixture.whenStable();
    return fixture;
  }

  it('lists the movements of the shift', async () => {
    const fixture = await open([
      movement(),
      movement({ id: 'm2', type: 'IN', amount: pen('20.00'), reasonCode: null, note: 'Sencillo', reference: null }),
    ]);

    const rows = host(fixture).querySelectorAll('tbody tr');
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('Salida');
    expect(rows[0].textContent).toContain('5.00');
    expect(rows[0].textContent).toContain('Change');
    expect(rows[0].textContent).toContain('F-100');
    expect(rows[0].textContent).toContain('10:30');
    expect(rows[1].textContent).toContain('Entrada');
    expect(rows[1].textContent).toContain('Sencillo');
  });

  it('says so when the shift has no movements', async () => {
    const fixture = await open([]);

    expect(find(fixture, 'no-movements')).not.toBeNull();
  });

  it('records a cash-out with a reason and a reference, under one key per intent', async () => {
    const fixture = await open();
    await chooseOption(fixture, host(fixture), 'mv-reason', 'Change');
    type(fixture, 'mv-amount', '5');
    type(fixture, 'mv-reference', 'F-100');
    await fixture.whenStable();

    find<HTMLButtonElement>(fixture, 'mv-submit')!.click();
    await fixture.whenStable();

    const request = http.expectOne(MOVEMENTS);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      type: 'OUT',
      amount: pen('5.00'),
      reasonCode: 'Change',
      note: null,
      reference: 'F-100',
    });
    expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
    request.flush({}, { status: 201, statusText: 'Created' });
    http.expectOne(MOVEMENTS).flush([movement()]);
    await fixture.whenStable();

    expect(host(fixture).querySelectorAll('tbody tr')).toHaveLength(1);
    expect(find<HTMLInputElement>(fixture, 'mv-amount')!.value).toBe('');
  });

  it('asks for a reason before a cash-out can be recorded', async () => {
    const fixture = await open();
    type(fixture, 'mv-amount', '5');
    await fixture.whenStable();

    expect(find<HTMLButtonElement>(fixture, 'mv-submit')!.disabled).toBe(true);
  });

  it('asks for a note when the reason is Other', async () => {
    const fixture = await open();
    await chooseOption(fixture, host(fixture), 'mv-reason', 'Otro');
    type(fixture, 'mv-amount', '5');
    await fixture.whenStable();

    expect(find<HTMLButtonElement>(fixture, 'mv-submit')!.disabled).toBe(true);
    expect(find(fixture, 'note-required')).not.toBeNull();

    type(fixture, 'mv-note', 'Taxi');
    await fixture.whenStable();
    expect(find<HTMLButtonElement>(fixture, 'mv-submit')!.disabled).toBe(false);
  });

  it('records a cash-in with a note and no reason', async () => {
    const fixture = await open();
    await chooseOption(fixture, host(fixture), 'mv-type', 'Entrada');
    type(fixture, 'mv-amount', '20,5');
    await fixture.whenStable();
    expect(find<HTMLButtonElement>(fixture, 'mv-submit')!.disabled).toBe(true);
    expect(find(fixture, 'mv-reason-box')).toBeNull();

    type(fixture, 'mv-note', 'Sencillo del banco');
    await fixture.whenStable();
    find<HTMLButtonElement>(fixture, 'mv-submit')!.click();
    await fixture.whenStable();

    const request = http.expectOne(MOVEMENTS);
    expect(request.request.body).toEqual({
      type: 'IN',
      amount: pen('20.50'),
      reasonCode: null,
      note: 'Sencillo del banco',
      reference: null,
    });
    request.flush({}, { status: 201, statusText: 'Created' });
    http.expectOne(MOVEMENTS).flush([]);
  });

  it('refuses an amount that is not a positive amount', async () => {
    const fixture = await open();
    await chooseOption(fixture, host(fixture), 'mv-reason', 'Change');
    type(fixture, 'mv-amount', '0');
    await fixture.whenStable();

    expect(find<HTMLButtonElement>(fixture, 'mv-submit')!.disabled).toBe(true);
  });
});
