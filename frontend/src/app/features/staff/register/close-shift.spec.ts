import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { pen, problem, shift } from '../../../testing/register-fixtures';
import { CloseShift } from './close-shift';
import { RegisterApi } from './register-api';
import { ShiftStore } from './shift-store';

const BASE = 'http://api.test/api/v1';
const CLOSE = `${BASE}/shifts/s1/close`;

@Component({
  imports: [CloseShift],
  template: `<app-close-shift [visible]="visible()" (dismissed)="visible.set(false)" />`,
})
class Host {
  readonly visible = signal(true);
}

const closed = (difference = '0.00') =>
  shift({
    status: 'CLOSED',
    closeKind: 'COUNTED',
    closedAt: '2026-10-13T22:00:00Z',
    countedCash: pen('90.00'),
    expectedCash: pen('100.00'),
    difference: pen(difference),
    totals: {
      cashReceived: pen('50.00'),
      changeGiven: pen('0.00'),
      voidedCash: pen('0.00'),
      cashIn: pen('0.00'),
      cashOut: pen('0.00'),
      byMethod: [{ method: 'CASH', recorded: pen('50.00'), voided: pen('0.00'), net: pen('50.00') }],
    },
  });

describe('CloseShift', () => {
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

  const inDialog = <T extends HTMLElement>(selector: string) =>
    document.body.querySelector<T>(`[role="dialog"] ${selector}`);
  const byId = <T extends HTMLElement>(testId: string) => inDialog<T>(`[data-testid="${testId}"]`);
  const field = (testId: string) =>
    testId === 'close-count' ? inDialog<HTMLInputElement>('#close-count') : byId<HTMLInputElement | HTMLTextAreaElement>(testId);
  const type = (testId: string, value: string) => {
    const input = field(testId)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };

  async function open() {
    const store = TestBed.inject(ShiftStore);
    store.load();
    http.expectOne(`${BASE}/shifts/current`).flush(shift());
    http.expectOne(`${BASE}/store/board-thresholds`).flush({ timeZone: 'America/Lima' });
    const fixture: ComponentFixture<Host> = TestBed.createComponent(Host);
    await fixture.whenStable();
    return fixture;
  }

  async function submit(fixture: ComponentFixture<Host>) {
    byId<HTMLButtonElement>('close-confirm')!.click();
    await fixture.whenStable();
  }

  it('asks for the count first and shows no expected cash', async () => {
    const fixture = await open();

    expect(inDialog('#close-count')).not.toBeNull();
    expect(byId('expected-cash')).toBeNull();
    expect(byId('close-note')).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('Efectivo esperado');
  });

  it('keeps the confirm button off until the count is an amount', async () => {
    const fixture = await open();
    expect(byId<HTMLButtonElement>('close-confirm')!.disabled).toBe(true);

    type('close-count', '100');
    await fixture.whenStable();

    expect(byId<HTMLButtonElement>('close-confirm')!.disabled).toBe(false);
  });

  it('sends the count, then shows expected, difference and the totals per method', async () => {
    const fixture = await open();
    type('close-count', '90');
    await fixture.whenStable();

    await submit(fixture);
    const request = http.expectOne(CLOSE);
    expect(request.request.body).toEqual({ countedCash: pen('90.00'), note: null });
    request.flush(closed());
    await fixture.whenStable();

    expect(byId('expected-cash')?.textContent).toContain('100.00');
    expect(byId('counted-cash')?.textContent).toContain('90.00');
    expect(inDialog('tbody')?.textContent).toContain('Efectivo');
    expect(inDialog('#close-count')).toBeNull();
  });

  it('shows the difference, locks the count and asks for a note when it is required', async () => {
    const fixture = await open();
    type('close-count', '90');
    await fixture.whenStable();
    await submit(fixture);
    http
      .expectOne(CLOSE)
      .flush(problem(409, 'instore.difference-note-required', { difference: pen('-10.00') }), {
        status: 409,
        statusText: 'Conflict',
      });
    await fixture.whenStable();

    expect(byId('close-difference')?.textContent).toContain('10.00');
    expect(inDialog<HTMLInputElement>('#close-count')!.readOnly).toBe(true);
    expect(byId('close-note')).not.toBeNull();
    expect(byId<HTMLButtonElement>('close-confirm')!.disabled).toBe(true);

    type('close-note', 'Faltó sencillo');
    await fixture.whenStable();
    await submit(fixture);

    const request = http.expectOne(CLOSE);
    expect(request.request.body).toEqual({ countedCash: pen('90.00'), note: 'Faltó sencillo' });
    request.flush(closed('-10.00'));
    await fixture.whenStable();

    expect(byId('difference')?.textContent).toContain('10.00');
    expect(byId('difference-note')).toBeNull();
  });

  it('explains that the count can no longer change', async () => {
    const fixture = await open();
    type('close-count', '91');
    await fixture.whenStable();
    await submit(fixture);
    http
      .expectOne(CLOSE)
      .flush(problem(422, 'instore.counted-cash-locked'), { status: 422, statusText: 'Unprocessable' });
    await fixture.whenStable();

    expect(byId('count-locked')).not.toBeNull();
  });

  it('asks the screen to close the dialog from the done button', async () => {
    const fixture = await open();
    type('close-count', '90');
    await fixture.whenStable();
    await submit(fixture);
    http.expectOne(CLOSE).flush(closed());
    await fixture.whenStable();

    byId<HTMLButtonElement>('close-done')!.click();
    await fixture.whenStable();

    expect(fixture.componentInstance.visible()).toBe(false);
  });

  it('refills the locked count when the dialog is reopened after a cancel following the difference', async () => {
    const fixture = await open();
    type('close-count', '90');
    await fixture.whenStable();
    await submit(fixture);
    http
      .expectOne(CLOSE)
      .flush(problem(409, 'instore.difference-note-required', { difference: pen('-10.00') }), {
        status: 409,
        statusText: 'Conflict',
      });
    await fixture.whenStable();

    byId<HTMLButtonElement>('close-cancel')!.click();
    await fixture.whenStable();
    fixture.componentInstance.visible.set(true);
    await fixture.whenStable();

    expect(inDialog<HTMLInputElement>('#close-count')!.value).toBe('90.00');
    expect(inDialog<HTMLInputElement>('#close-count')!.readOnly).toBe(true);
    expect(byId('close-note')).not.toBeNull();
    type('close-note', 'Faltó sencillo');
    await fixture.whenStable();
    expect(byId<HTMLButtonElement>('close-confirm')!.disabled).toBe(false);
    await submit(fixture);
    expect(http.expectOne(CLOSE).request.body).toEqual({ countedCash: pen('90.00'), note: 'Faltó sencillo' });
  });

  it('closes without sending anything when cancelled', async () => {
    const fixture = await open();

    byId<HTMLButtonElement>('close-cancel')!.click();
    await fixture.whenStable();

    expect(fixture.componentInstance.visible()).toBe(false);
  });

  it('says why the confirm button is off until the count is an amount', async () => {
    await open();

    expect(byId('close-why')?.textContent).toContain('contado');
    expect(byId<HTMLButtonElement>('close-confirm')!.getAttribute('aria-describedby')).toBe(byId('close-why')!.id);
  });

  it('labels the count and note fields and ties the error room to them', async () => {
    const fixture = await open();

    expect(inDialog('label[for="close-count"]')).not.toBeNull();
    type('close-count', 'abc');
    inDialog<HTMLInputElement>('#close-count')!.dispatchEvent(new Event('blur'));
    await fixture.whenStable();
    expect(inDialog('#close-count')!.getAttribute('aria-invalid')).toBe('true');
    expect(inDialog('app-form-field .message.error')?.textContent).toContain('Escribe un monto');
  });

  it('is aria-busy and cannot be dismissed while the close request runs', async () => {
    const fixture = await open();
    type('close-count', '90');
    await fixture.whenStable();
    await submit(fixture);

    expect(inDialog('form')!.getAttribute('aria-busy')).toBe('true');
    expect(byId<HTMLButtonElement>('close-cancel')!.disabled).toBe(true);
    expect(inDialog('.p-dialog-close-button')).toBeNull();
    inDialog('form')!.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    await fixture.whenStable();
    expect(fixture.componentInstance.visible()).toBe(true);

    http.expectOne(CLOSE).flush(closed());
  });
});
