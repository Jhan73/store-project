import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { providePrimeNG } from 'primeng/config';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { StaffMember, StockItem, StockMovement, StockMovementPage } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { primeTranslation } from '../../../core/theme/prime-translation';
import { StockApi } from './stock-api';
import { StockMovementsDialog } from './stock-movements-dialog';

const API = 'http://api.test/api/v1';
const MOVEMENTS = `${API}/admin/stock-items/p1/movements`;
const ANA = '0199f3a2-0000-7000-8000-00000000000a';
const STRANGER = '0199f3a2-ffff-7000-8000-00000000ffff';

const item: StockItem = {
  productId: 'p1',
  tracked: true,
  onHand: 10,
  reserved: 0,
  available: 10,
  lowStockThreshold: 5,
  low: false,
};

function movement(overrides: Partial<StockMovement>): StockMovement {
  return {
    id: 'm1',
    productId: 'p1',
    type: 'ENTRY',
    onHandDelta: 12,
    reservedDelta: 0,
    referenceId: null,
    reasonCode: null,
    note: null,
    actorId: ANA,
    actorRole: 'ADMIN',
    occurredAt: '2026-10-05T15:30:00Z',
    ...overrides,
  };
}

function page(content: StockMovement[], overrides: Partial<StockMovementPage> = {}): StockMovementPage {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, ...overrides };
}

const ana = {
  id: ANA,
  email: 'ana@juguera.pe',
  role: 'ADMIN',
  active: true,
  createdAt: '2026-01-01T00:00:00Z',
} as StaffMember;

@Component({
  imports: [StockMovementsDialog],
  template: `
    <app-stock-movements-dialog
      [visible]="visible()"
      [item]="item()"
      productName="Papaya"
      (dismissed)="visible.set(false)"
    />
  `,
})
class Host {
  readonly visible = signal(true);
  readonly item = signal<StockItem>(item);
}

describe('StockMovementsDialog', () => {
  let http: HttpTestingController;

  const dialog = () => document.body.querySelector('[role="dialog"]') as HTMLElement;
  const inDialog = <T extends HTMLElement>(testId: string) =>
    document.body.querySelector<T>(`[role="dialog"] [data-testid="${testId}"]`)!;
  const rows = () =>
    Array.from(dialog().querySelectorAll('tbody tr')).map((row) => row.textContent ?? '');

  const refused = [
    { type: 'about:blank', status: 500, code: 'common.internal-error', correlationId: 'c' },
    { status: 500, statusText: 'Error' },
  ] as const;

  async function render() {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        MessageService,
        providePrimeNG({ translation: primeTranslation }),
        StockApi,
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    const fixture = TestBed.createComponent(Host);
    await fixture.whenStable();
    return fixture;
  }

  // Answers the three reads a first opening makes.
  async function rendered(first: StockMovementPage = page([movement({})])) {
    const fixture = await render();
    http.expectOne(`${API}/admin/settings`).flush({ timeZone: 'America/Lima', currency: 'PEN' });
    http.expectOne(`${API}/staff?page=0&size=100`).flush({
      content: [ana],
      page: 0,
      size: 100,
      totalElements: 1,
      totalPages: 1,
    });
    http.expectOne(`${MOVEMENTS}?page=0&size=20`).flush(first);
    await fixture.whenStable();
    return fixture;
  }

  afterEach(() => http.verify());

  it('lists the movements in the order the API gives them, with the time in the store zone', async () => {
    await rendered(
      page([
        movement({ id: 'm2', occurredAt: '2026-10-05T16:00:00Z' }),
        movement({ id: 'm1', occurredAt: '2026-10-05T15:30:00Z' }),
      ]),
    );

    const [first, second] = rows();
    expect(dialog().textContent).toContain('Papaya');
    expect(first).toContain('11:00');
    expect(second).toContain('10:30');
  });

  it('shows the type, the signed changes, the reason, the note and the person', async () => {
    await rendered(
      page([
        movement({
          type: 'ADJUSTMENT',
          onHandDelta: -3,
          reservedDelta: 0,
          reasonCode: 'Damaged',
          note: 'Se cayó la caja',
        }),
      ]),
    );

    const row = rows()[0];
    expect(row).toContain('Ajuste');
    expect(row).toContain('-3');
    expect(row).toContain('Damaged');
    expect(row).toContain('Se cayó la caja');
    expect(row).toContain('ana@juguera.pe');
  });

  it('words the reason Other in Spanish and signs reservations', async () => {
    await rendered(
      page([movement({ type: 'RESERVE', onHandDelta: 0, reservedDelta: 2, reasonCode: 'Other' })]),
    );

    const row = rows()[0];
    expect(row).toContain('Reserva');
    expect(row).toContain('+2');
    expect(row).toContain('Otro');
  });

  it('shortens an actor it does not know and names the role of a system movement', async () => {
    await rendered(
      page([
        movement({ id: 'm1', actorId: STRANGER }),
        movement({ id: 'm2', actorId: null, actorRole: 'SYSTEM', type: 'EXPIRE' }),
      ]),
    );

    const [stranger, system] = rows();
    expect(stranger).toContain('0199f3a2…');
    expect(system).toContain('Sistema');
  });

  it('shows the reference of a reservation, sale or commit in a selectable, truncated form', async () => {
    const reference = '0199f3a2-7c1e-7b1a-8f00-3c1d2e4a5b6c';
    await rendered(
      page([movement({ type: 'SALE', referenceId: reference }), movement({ id: 'm2', referenceId: null })]),
    );

    const code = dialog().querySelector<HTMLElement>('tbody tr code.ref')!;
    expect(code.textContent).toBe(reference);
    expect(code.getAttribute('title')).toBe(reference);
    expect(dialog().querySelectorAll('tbody code.ref')).toHaveLength(1);
    expect(rows()[1]).toContain('—');
  });

  it('says so when the product has no movements yet', async () => {
    await rendered(page([]));

    expect(inDialog('movements-empty')).not.toBeNull();
    expect(dialog().querySelector('table')).toBeNull();
  });

  it('asks for another page from the paginator', async () => {
    const fixture = await rendered(
      page([movement({})], { totalElements: 45, totalPages: 3 }),
    );

    dialog().querySelector<HTMLButtonElement>('button.p-paginator-next')!.click();
    await fixture.whenStable();
    http
      .expectOne(`${MOVEMENTS}?page=1&size=20`)
      .flush(page([movement({ id: 'm9', type: 'SALE', onHandDelta: -1 })], { page: 1, totalElements: 45, totalPages: 3 }));
    await fixture.whenStable();

    expect(rows()[0]).toContain('Venta');
  });

  it('shows an error with a retry when the movements cannot be read', async () => {
    const fixture = await render();
    http.expectOne(`${API}/admin/settings`).flush({ timeZone: 'America/Lima', currency: 'PEN' });
    http.expectOne(`${API}/staff?page=0&size=100`).flush({
      content: [],
      page: 0,
      size: 100,
      totalElements: 0,
      totalPages: 0,
    });
    http.expectOne(`${MOVEMENTS}?page=0&size=20`).flush(...refused);
    await fixture.whenStable();

    expect(dialog().querySelector('[role="alert"]')).not.toBeNull();
    inDialog<HTMLButtonElement>('movements-retry').click();
    await fixture.whenStable();
    http.expectOne(`${MOVEMENTS}?page=0&size=20`).flush(page([movement({})]));
    await fixture.whenStable();

    expect(rows()).toHaveLength(1);
  });

  it('does not show times until the store zone is known', async () => {
    const fixture = await render();
    http.expectOne(`${API}/admin/settings`).flush(...refused);
    http.expectOne(`${API}/staff?page=0&size=100`).flush({
      content: [],
      page: 0,
      size: 100,
      totalElements: 0,
      totalPages: 0,
    });
    http.expectOne(`${MOVEMENTS}?page=0&size=20`).flush(page([movement({})]));
    await fixture.whenStable();

    expect(dialog().querySelector('table')).toBeNull();
    expect(dialog().querySelector('[role="alert"]')).not.toBeNull();
    inDialog<HTMLButtonElement>('movements-retry').click();
    await fixture.whenStable();
    http.expectOne(`${API}/admin/settings`).flush({ timeZone: 'America/Lima', currency: 'PEN' });
    http.expectOne(`${MOVEMENTS}?page=0&size=20`).flush(page([movement({})]));
    await fixture.whenStable();

    expect(rows()[0]).toContain('10:30');
  });

  it('reads the zone and the people only once when it opens again', async () => {
    const fixture = await rendered();
    fixture.componentInstance.visible.set(false);
    await fixture.whenStable();
    fixture.componentInstance.visible.set(true);
    await fixture.whenStable();

    http.expectOne(`${MOVEMENTS}?page=0&size=20`).flush(page([movement({})]));
    await fixture.whenStable();

    expect(rows()).toHaveLength(1);
  });

  it('asks to be dismissed on close', async () => {
    const fixture = await rendered();

    inDialog<HTMLButtonElement>('movements-close').click();
    await fixture.whenStable();

    expect(fixture.componentInstance.visible()).toBe(false);
  });
});
