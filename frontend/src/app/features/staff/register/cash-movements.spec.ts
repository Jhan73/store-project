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
  const body = () => document.body;
  const inDialog = <T extends HTMLElement>(selector: string) =>
    body().querySelector<T>('[role="dialog"] ' + selector);
  const type = (selector: string, value: string) => {
    const input = inDialog<HTMLInputElement | HTMLTextAreaElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };
  const save = () => inDialog<HTMLButtonElement>('[data-testid="fd-save"]')!;
  async function openForm(fixture: ComponentFixture<CashMovements>) {
    find<HTMLButtonElement>(fixture, 'mv-open')!.click();
    await fixture.whenStable();
  }

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

    expect(host(fixture).querySelector('tbody')!.textContent).toContain('todavía no tiene movimientos');
  });

  it('records a cash-out with a reason and a reference, under one key per intent', async () => {
    const fixture = await open();
    await openForm(fixture);
    await chooseOption(fixture, body(), 'mv-reason', 'Change');
    type('#mv-amount', '5');
    type('#mv-reference', 'F-100');
    await fixture.whenStable();

    save().click();
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
    expect(inDialog('#mv-amount')).toBeNull();
    expect(inDialog('[data-testid="fd-save"]')).toBeNull();
  });

  it('asks for a reason before a cash-out can be recorded and says so', async () => {
    const fixture = await open();
    await openForm(fixture);
    type('#mv-amount', '5');
    await fixture.whenStable();

    expect(save().disabled).toBe(true);
    expect(inDialog('[data-testid="fd-reason"]')!.textContent).toContain('motivo');
  });

  it('asks for a note when the reason is Other', async () => {
    const fixture = await open();
    await openForm(fixture);
    await chooseOption(fixture, body(), 'mv-reason', 'Otro');
    type('#mv-amount', '5');
    await fixture.whenStable();

    expect(save().disabled).toBe(true);
    expect(inDialog('app-form-field .message')!.textContent).toBeDefined();
    expect(body().textContent).toContain('La nota es obligatoria');

    type('#mv-note', 'Taxi');
    await fixture.whenStable();
    expect(save().disabled).toBe(false);
  });

  it('records a cash-in with a note and no reason', async () => {
    const fixture = await open();
    await openForm(fixture);
    await chooseOption(fixture, body(), 'mv-type', 'Entrada');
    type('#mv-amount', '20,5');
    await fixture.whenStable();
    expect(save().disabled).toBe(true);
    expect(inDialog('[data-testid="mv-reason-box"]')).toBeNull();

    type('#mv-note', 'Sencillo del banco');
    await fixture.whenStable();
    save().click();
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
    await openForm(fixture);
    await chooseOption(fixture, body(), 'mv-reason', 'Change');
    type('#mv-amount', '0');
    await fixture.whenStable();

    expect(save().disabled).toBe(true);
  });

  it('is aria-busy and keeps the dialog while the movement is being recorded', async () => {
    const fixture = await open();
    await openForm(fixture);
    await chooseOption(fixture, body(), 'mv-reason', 'Change');
    type('#mv-amount', '5');
    await fixture.whenStable();
    save().click();
    await fixture.whenStable();

    expect(inDialog('form')!.getAttribute('aria-busy')).toBe('true');
    expect(inDialog<HTMLButtonElement>('[data-testid="fd-cancel"]')!.disabled).toBe(true);

    http.expectOne(MOVEMENTS).flush({}, { status: 201, statusText: 'Created' });
    http.expectOne(MOVEMENTS).flush([]);
  });
});
