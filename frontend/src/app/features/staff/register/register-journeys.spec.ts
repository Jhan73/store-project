import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { Menu, QuickSaleItem, Ticket } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { chooseOption } from '../../../testing/primeng-controls';
import { pen, problem, shift } from '../../../testing/register-fixtures';
import { QuickSale } from '../quick-sale/quick-sale';
import { Register } from './register';
import { RegisterApi } from './register-api';

const BASE = 'http://api.test/api/v1';

describe('register journeys', () => {
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

  const type = (root: ParentNode, selector: string, value: string) => {
    const input = root.querySelector<HTMLInputElement | HTMLTextAreaElement>(selector)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };
  const click = async (fixture: ComponentFixture<unknown>, root: ParentNode, selector: string) => {
    root.querySelector<HTMLElement>(selector)!.click();
    await fixture.whenStable();
  };

  // Journey 4: three frequent items, the name "Ana" and the cash received, then the change.
  it('sells three frequent items to Ana for cash and shows the change and the number', async () => {
    const product = (id: string, name: string, price: string) => ({
      id,
      name,
      available: true,
      description: null,
      imageUrl: null,
      allergens: [],
      price: pen(price),
      modifierGroups: [],
    });
    const items: QuickSaleItem[] = [
      { productId: 'a', name: 'Naranja', price: pen('8.00') },
      { productId: 'b', name: 'Fresa', price: pen('10.00') },
      { productId: 'c', name: 'Piña', price: pen('9.00') },
    ];
    const menu = {
      categories: [
        {
          id: 'c',
          name: 'Jugos',
          products: [product('a', 'Naranja', '8.00'), product('b', 'Fresa', '10.00'), product('c', 'Piña', '9.00')],
        },
      ],
    } as unknown as Menu;
    const fixture = TestBed.createComponent(QuickSale);
    const root = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
    http.expectOne(`${BASE}/quick-sales/items`).flush(items);
    http.expectOne(`${BASE}/catalog/menu`).flush(menu);
    http.expectOne(`${BASE}/shifts/current`).flush(shift());
    await fixture.whenStable();

    await click(fixture, root, '[data-testid="item-a"]');
    await click(fixture, root, '[data-testid="item-b"]');
    await click(fixture, root, '[data-testid="item-c"]');
    type(root, '[data-testid="customer-name"]', 'Ana');
    type(root, '[data-testid="qs-received"]', '50');
    await fixture.whenStable();
    await click(fixture, root, '[data-testid="qs-submit"]');

    const request = http.expectOne(`${BASE}/quick-sales`);
    expect(request.request.body.customerName).toBe('Ana');
    expect(request.request.body.lines.map((line: { productId: string }) => line.productId)).toEqual(['a', 'b', 'c']);
    expect(request.request.body.payment).toEqual({ method: 'CASH', receivedAmount: pen('50.00') });
    request.flush(
      {
        id: 'k1',
        number: 18,
        quickSale: true,
        status: 'CLOSED',
        total: pen('27.00'),
        paid: pen('27.00'),
        balance: pen('0.00'),
        lines: [],
        payments: [
          {
            id: 'p',
            method: 'CASH',
            paid: pen('27.00'),
            received: pen('50.00'),
            change: pen('23.00'),
            lineIds: [],
            recordedBy: 'u',
            recordedAt: '2026-10-13T10:00:00Z',
            voided: false,
            voidReasonCode: null,
            voidNote: null,
            compReasonCode: null,
            compNote: null,
          },
        ],
      } as unknown as Ticket,
      { status: 201, statusText: 'Created' },
    );
    await fixture.whenStable();

    expect(root.querySelector('[data-testid="sale-number"]')?.textContent).toContain('18');
    expect(root.querySelector('[data-testid="sale-total"]')?.textContent).toContain('27.00');
    expect(root.querySelector('[data-testid="sale-change"]')?.textContent).toContain('23.00');
  });

  // Journey 7: a cash out for the supplier, then a blind close with a justified difference.
  it('records a cash out and closes the shift blind with a justified difference', async () => {
    const fixture = TestBed.createComponent(Register);
    const root = fixture.nativeElement as HTMLElement;
    await fixture.whenStable();
    http.expectOne(`${BASE}/shifts/current`).flush(shift());
    http.expectOne(`${BASE}/store/board-thresholds`).flush({ timeZone: 'America/Lima' });
    await fixture.whenStable();
    http.expectOne(`${BASE}/shifts/s1/cash-movements`).flush([]);
    http.expectOne(`${BASE}/reasons?type=CASH_OUT`).flush([{ type: 'CASH_OUT', code: 'Supplier' }]);
    await fixture.whenStable();

    await click(fixture, root, '[data-testid="mv-open"]');
    await chooseOption(fixture, document.body, 'mv-reason', 'Supplier');
    type(document.body, '[role="dialog"] #mv-amount', '40');
    type(document.body, '[role="dialog"] [data-testid="mv-reference"]', 'Fruteria Lola');
    await fixture.whenStable();
    await click(fixture, document.body, '[role="dialog"] [data-testid="fd-save"]');
    const movement = http.expectOne(`${BASE}/shifts/s1/cash-movements`);
    expect(movement.request.body).toMatchObject({ type: 'OUT', reasonCode: 'Supplier', reference: 'Fruteria Lola' });
    movement.flush({}, { status: 201, statusText: 'Created' });
    http.expectOne(`${BASE}/shifts/s1/cash-movements`).flush([]);
    await fixture.whenStable();

    await click(fixture, root, '[data-testid="close-open"]');
    const body = document.body;
    expect(body.textContent).not.toContain('Efectivo esperado');
    type(body, '[role="dialog"] #close-count', '90');
    await fixture.whenStable();
    await click(fixture, body, '[role="dialog"] [data-testid="close-confirm"]');
    http
      .expectOne(`${BASE}/shifts/s1/close`)
      .flush(problem(409, 'instore.difference-note-required', { difference: pen('-10.00') }), {
        status: 409,
        statusText: 'Conflict',
      });
    await fixture.whenStable();

    expect(body.querySelector('[role="dialog"] [data-testid="close-difference"]')?.textContent).toContain('10.00');
    type(body, '[role="dialog"] [data-testid="close-note"]', 'Faltó sencillo');
    await fixture.whenStable();
    await click(fixture, body, '[role="dialog"] [data-testid="close-confirm"]');
    const close = http.expectOne(`${BASE}/shifts/s1/close`);
    expect(close.request.body).toEqual({ countedCash: pen('90.00'), note: 'Faltó sencillo' });
    close.flush(
      shift({
        status: 'CLOSED',
        closeKind: 'COUNTED',
        closedAt: '2026-10-13T22:00:00Z',
        countedCash: pen('90.00'),
        expectedCash: pen('100.00'),
        difference: pen('-10.00'),
        differenceNote: 'Faltó sencillo',
        totals: {
          cashReceived: pen('90.00'),
          changeGiven: pen('0.00'),
          voidedCash: pen('0.00'),
          cashIn: pen('0.00'),
          cashOut: pen('40.00'),
          byMethod: [{ method: 'CASH', recorded: pen('90.00'), voided: pen('0.00'), net: pen('90.00') }],
        },
      }),
    );
    await fixture.whenStable();

    const dialog = body.querySelector('[role="dialog"]')!;
    expect(dialog.querySelector('[data-testid="expected-cash"]')?.textContent).toContain('100.00');
    expect(dialog.querySelector('[data-testid="difference"]')?.textContent).toContain('10.00');
    expect(dialog.querySelector('[data-testid="difference-note"]')?.textContent).toContain('Faltó sencillo');
    expect(dialog.querySelector('tbody')?.textContent).toContain('Efectivo');
  });
});
