import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { Shift } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { pen, problem, shift } from '../../../testing/register-fixtures';
import { OpenShifts } from './open-shifts';
import { RegisterApi } from './register-api';

const BASE = 'http://api.test/api/v1';
const LIST = `${BASE}/shifts?status=OPEN&page=0&size=100`;
const STAFF = `${BASE}/staff?page=0&size=100`;
const ZONE = `${BASE}/store/board-thresholds`;
const page = (content: Shift[]) => ({ content, page: 0, size: 100, totalElements: content.length, totalPages: 1 });

describe('OpenShifts', () => {
  let http: HttpTestingController;
  let messages: MessageService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        MessageService,
        RegisterApi,
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    messages = TestBed.inject(MessageService);
  });

  afterEach(() => http.verify());

  const host = (fixture: ComponentFixture<OpenShifts>) => fixture.nativeElement as HTMLElement;
  const find = <T extends HTMLElement>(fixture: ComponentFixture<OpenShifts>, testId: string) =>
    host(fixture).querySelector<T>(`[data-testid="${testId}"]`);
  const inDialog = <T extends HTMLElement>(testId: string) =>
    document.body.querySelector<T>(`[role="dialog"] [data-testid="${testId}"]`);

  async function open(shifts: Shift[] = [shift()]) {
    const fixture = TestBed.createComponent(OpenShifts);
    await fixture.whenStable();
    http.expectOne(LIST).flush(page(shifts));
    http.expectOne(STAFF).flush({
      content: [{ id: 'u1', email: 'ana@jugueria.pe' }],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    });
    http.expectOne(ZONE).flush({ timeZone: 'America/Lima' });
    await fixture.whenStable();
    return fixture;
  }

  it('lists the open shifts with the cashier named from the staff accounts', async () => {
    const fixture = await open([shift(), shift({ id: 's2', cashierId: '0f5a1c9e-1111-2222-3333-444455556666' })]);

    const rows = host(fixture).querySelectorAll('tbody tr');
    expect(rows).toHaveLength(2);
    expect(rows[0].textContent).toContain('ana@jugueria.pe');
    expect(rows[0].textContent).toContain('50.00');
    expect(rows[0].textContent).toContain('9:00');
    expect(rows[1].textContent).toContain('0f5a1c9e');
    expect(rows[1].textContent).not.toContain('0f5a1c9e-1111');
  });

  it('says so when no shift is open', async () => {
    const fixture = await open([]);

    expect(find(fixture, 'no-open-shifts')).not.toBeNull();
  });

  it('force-closes a shift with a reason and then shows its expected cash', async () => {
    const fixture = await open();
    find<HTMLButtonElement>(fixture, 'force-s1')!.click();
    await fixture.whenStable();
    expect(inDialog<HTMLButtonElement>('force-confirm')!.disabled).toBe(true);

    const reason = inDialog<HTMLTextAreaElement>('force-reason')!;
    reason.value = 'Se fue sin cerrar';
    reason.dispatchEvent(new Event('input'));
    await fixture.whenStable();
    inDialog<HTMLButtonElement>('force-confirm')!.click();
    await fixture.whenStable();

    const request = http.expectOne(`${BASE}/shifts/s1/force-close`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ reason: 'Se fue sin cerrar' });
    request.flush(shift({ status: 'CLOSED', closeKind: 'FORCED', forceReason: 'Se fue sin cerrar', expectedCash: pen('120.00') }));
    http.expectOne(LIST).flush(page([]));
    await fixture.whenStable();

    expect(inDialog('expected-cash')?.textContent).toContain('120.00');
    expect(inDialog('forced')?.textContent).toContain('Se fue sin cerrar');
    expect(find(fixture, 'no-open-shifts')).not.toBeNull();
  });

  it('keeps the dialog and reads the list again when the force close is refused', async () => {
    const fixture = await open();
    const added = vi.spyOn(messages, 'add');
    find<HTMLButtonElement>(fixture, 'force-s1')!.click();
    await fixture.whenStable();
    const reason = inDialog<HTMLTextAreaElement>('force-reason')!;
    reason.value = 'x';
    reason.dispatchEvent(new Event('input'));
    await fixture.whenStable();

    inDialog<HTMLButtonElement>('force-confirm')!.click();
    await fixture.whenStable();
    http
      .expectOne(`${BASE}/shifts/s1/force-close`)
      .flush(problem(409, 'instore.shift-closed'), { status: 409, statusText: 'Conflict' });
    http.expectOne(LIST).flush(page([]));
    await fixture.whenStable();

    expect(added).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
    expect(find(fixture, 'no-open-shifts')).not.toBeNull();
  });

  it('lets a failed list be retried', async () => {
    const fixture = TestBed.createComponent(OpenShifts);
    await fixture.whenStable();
    http.expectOne(LIST).flush(problem(500, 'common.internal-error'), { status: 500, statusText: 'Error' });
    http.expectOne(STAFF).flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    http.expectOne(ZONE).flush({ timeZone: 'America/Lima' });
    await fixture.whenStable();

    expect(find(fixture, 'load-failed')).not.toBeNull();
    find<HTMLButtonElement>(fixture, 'retry')!.click();
    http.expectOne(LIST).flush(page([]));
    http.expectOne(STAFF).flush({ content: [], page: 0, size: 100, totalElements: 0, totalPages: 0 });
    http.expectOne(ZONE).flush({ timeZone: 'America/Lima' });
    await fixture.whenStable();

    expect(find(fixture, 'no-open-shifts')).not.toBeNull();
  });
});
