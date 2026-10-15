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
import { RegisterApi } from '../register/register-api';
import { QuickSale } from './quick-sale';

const BASE = 'http://api.test/api/v1';

const items: QuickSaleItem[] = [
  { productId: 'p1', name: 'Naranja', price: pen('8.00') },
  { productId: 'p2', name: 'Fresa', price: pen('10.00') },
  { productId: 'p3', name: 'Piña', price: pen('9.00') },
];

const product = (id: string, name: string, price: string, available = true, groups: unknown[] = []) => ({
  id,
  name,
  available,
  description: null,
  imageUrl: null,
  allergens: [],
  price: pen(price),
  modifierGroups: groups,
});

const menu = {
  categories: [
    {
      id: 'c',
      name: 'Jugos',
      products: [
        product('p1', 'Naranja', '8.00'),
        product('p2', 'Fresa', '10.00', true, [
          {
            id: 'size',
            name: 'Tamaño',
            required: true,
            minChoices: 1,
            maxChoices: 1,
            options: [
              { id: 'small', name: 'Chico', available: true, allergens: [], priceDelta: pen('0.00') },
              { id: 'large', name: 'Grande', available: true, allergens: [], priceDelta: pen('2.00') },
            ],
          },
        ]),
        product('p3', 'Piña', '9.00', false),
      ],
    },
  ],
} as unknown as Menu;

function sale(overrides: Partial<Ticket> = {}): Ticket {
  return {
    id: 'k1',
    number: 42,
    quickSale: true,
    status: 'CLOSED',
    total: pen('8.00'),
    paid: pen('8.00'),
    balance: pen('0.00'),
    payments: [
      {
        id: 'p',
        method: 'CASH',
        paid: pen('8.00'),
        received: pen('20.00'),
        change: pen('12.00'),
        lineIds: [],
        recordedBy: 'u',
        recordedAt: '2026-10-13T10:00:00Z',
        voided: false,
        voidReasonCode: null,
        voidNote: null,
      },
    ],
    lines: [],
    ...overrides,
  } as unknown as Ticket;
}

describe('QuickSale', () => {
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

  const host = (fixture: ComponentFixture<QuickSale>) => fixture.nativeElement as HTMLElement;
  const find = <T extends HTMLElement>(fixture: ComponentFixture<QuickSale>, testId: string) =>
    host(fixture).querySelector<T>(`[data-testid="${testId}"]`);
  const text = (fixture: ComponentFixture<QuickSale>, testId: string) => find(fixture, testId)?.textContent ?? '';
  const press = async (fixture: ComponentFixture<QuickSale>, testId: string) => {
    find<HTMLButtonElement>(fixture, testId)!.click();
    await fixture.whenStable();
  };
  const type = (fixture: ComponentFixture<QuickSale>, testId: string, value: string) => {
    const input = find<HTMLInputElement>(fixture, testId)!;
    input.value = value;
    input.dispatchEvent(new Event('input'));
  };

  async function open(withShift = true) {
    const fixture = TestBed.createComponent(QuickSale);
    await fixture.whenStable();
    http.expectOne(`${BASE}/quick-sales/items`).flush(items);
    http.expectOne(`${BASE}/catalog/menu`).flush(menu);
    const read = http.expectOne(`${BASE}/shifts/current`);
    if (withShift) {
      read.flush(shift());
    } else {
      read.flush(problem(404, 'instore.shift-not-found'), { status: 404, statusText: 'Not Found' });
    }
    await fixture.whenStable();
    return fixture;
  }

  it('lists the pinned items and disables a sold-out one', async () => {
    const fixture = await open();

    expect(text(fixture, 'item-p1')).toContain('Naranja');
    expect(find<HTMLButtonElement>(fixture, 'item-p1')!.disabled).toBe(false);
    expect(find<HTMLButtonElement>(fixture, 'item-p3')!.disabled).toBe(true);
    expect(text(fixture, 'item-p3')).toContain('Agotado');
  });

  it('adds an item to the cart with an estimated total and changes its quantity', async () => {
    const fixture = await open();

    await press(fixture, 'item-p1');
    await press(fixture, 'item-p1');

    expect(text(fixture, 'cart-line-0')).toContain('Naranja');
    expect(text(fixture, 'cart-line-0')).toContain('2');
    expect(text(fixture, 'cart-total')).toContain('16.00');

    await press(fixture, 'qty-down-0');
    expect(text(fixture, 'cart-total')).toContain('8.00');

    await press(fixture, 'remove-0');
    expect(find(fixture, 'cart-line-0')).toBeNull();
  });

  it('opens the configurator for an item with a required choice and adds what was chosen', async () => {
    const fixture = await open();

    await press(fixture, 'item-p2');
    document.body.querySelector<HTMLInputElement>('[role="dialog"] [data-testid="option-large"] input')!.click();
    await fixture.whenStable();
    document.body.querySelector<HTMLButtonElement>('[role="dialog"] [data-testid="submit"]')!.click();
    await fixture.whenStable();

    expect(text(fixture, 'cart-line-0')).toContain('Fresa');
    expect(text(fixture, 'cart-line-0')).toContain('Grande');
    expect(text(fixture, 'cart-total')).toContain('12.00');
    expect(document.body.querySelector('[role="dialog"] [data-testid="submit"]')).toBeNull();
  });

  it('blocks the sale and points to the register when no shift is open', async () => {
    const fixture = await open(false);
    await press(fixture, 'item-p1');

    expect(find(fixture, 'no-shift')).not.toBeNull();
    expect(host(fixture).querySelector('a[href="/staff/register"]')).not.toBeNull();
    expect(find<HTMLButtonElement>(fixture, 'qs-submit')!.disabled).toBe(true);
  });

  it('sells for cash with the received amount and shows the number and the change from the API', async () => {
    const fixture = await open();
    await press(fixture, 'item-p1');
    type(fixture, 'customer-name', 'Ana');
    type(fixture, 'qs-received', '20');
    await fixture.whenStable();

    await press(fixture, 'qs-submit');
    const request = http.expectOne(`${BASE}/quick-sales`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      customerName: 'Ana',
      lines: [{ productId: 'p1', quantity: 1, modifierOptionIds: [], note: null, allergy: false }],
      payment: { method: 'CASH', receivedAmount: pen('20.00') },
    });
    expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
    request.flush(sale(), { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(text(fixture, 'sale-number')).toContain('42');
    expect(text(fixture, 'sale-total')).toContain('8.00');
    expect(text(fixture, 'sale-change')).toContain('12.00');
    expect(find(fixture, 'qs-submit')).toBeNull();

    await press(fixture, 'new-sale');
    expect(find(fixture, 'sale-number')).toBeNull();
    expect(find(fixture, 'cart-line-0')).toBeNull();
  });

  it('needs a valid amount received, and leaves judging it against the total to the API', async () => {
    const fixture = await open();
    await press(fixture, 'item-p1');
    type(fixture, 'qs-received', 'abc');
    await fixture.whenStable();
    expect(find<HTMLButtonElement>(fixture, 'qs-submit')!.disabled).toBe(true);

    type(fixture, 'qs-received', '5');
    await fixture.whenStable();
    expect(find<HTMLButtonElement>(fixture, 'qs-submit')!.disabled).toBe(false);

    await press(fixture, 'qs-submit');
    const request = http.expectOne(`${BASE}/quick-sales`);
    expect(request.request.body.payment).toEqual({ method: 'CASH', receivedAmount: pen('5.00') });
    request.flush(problem(422, 'instore.invalid-payment'), { status: 422, statusText: 'Unprocessable' });
    await fixture.whenStable();

    expect(find(fixture, 'cart-line-0')).not.toBeNull();
  });

  it('disables a pinned item that is not on the menu', async () => {
    const fixture = TestBed.createComponent(QuickSale);
    await fixture.whenStable();
    http.expectOne(`${BASE}/quick-sales/items`).flush([...items, { productId: 'p9', name: 'Kiwi', price: pen('7.00') }]);
    http.expectOne(`${BASE}/catalog/menu`).flush(menu);
    http.expectOne(`${BASE}/shifts/current`).flush(shift());
    await fixture.whenStable();

    expect(find<HTMLButtonElement>(fixture, 'item-p9')!.disabled).toBe(true);
    expect(text(fixture, 'item-p9')).toContain('No disponible');
    expect(find<HTMLButtonElement>(fixture, 'item-p1')!.disabled).toBe(false);
  });

  it('links the sold ticket so the sale can be voided from its view', async () => {
    const fixture = await open();
    await press(fixture, 'item-p1');
    await chooseOption(fixture, host(fixture), 'qs-method', 'Tarjeta');
    await press(fixture, 'qs-submit');
    http.expectOne(`${BASE}/quick-sales`).flush(sale({ payments: [] }), { status: 201, statusText: 'Created' });
    await fixture.whenStable();

    expect(find<HTMLAnchorElement>(fixture, 'sale-ticket')!.getAttribute('href')).toBe('/staff/tickets/k1');
  });

  it('sells by card without an amount received', async () => {
    const fixture = await open();
    await press(fixture, 'item-p1');
    await chooseOption(fixture, host(fixture), 'qs-method', 'Tarjeta');

    expect(find(fixture, 'qs-received')).toBeNull();
    await press(fixture, 'qs-submit');

    const request = http.expectOne(`${BASE}/quick-sales`);
    expect(request.request.body.payment).toEqual({ method: 'CARD', receivedAmount: null });
    expect(request.request.body.customerName).toBeNull();
    request.flush(sale({ payments: [] }), { status: 201, statusText: 'Created' });
  });

  it('keeps the cart when the sale is refused', async () => {
    const fixture = await open();
    await press(fixture, 'item-p1');
    await chooseOption(fixture, host(fixture), 'qs-method', 'Tarjeta');

    await press(fixture, 'qs-submit');
    http
      .expectOne(`${BASE}/quick-sales`)
      .flush(problem(409, 'inventory.insufficient-stock'), { status: 409, statusText: 'Conflict' });
    await fixture.whenStable();

    expect(find(fixture, 'cart-line-0')).not.toBeNull();
    expect(find(fixture, 'sale-number')).toBeNull();
  });

  it('offers a retry when the items cannot be read', async () => {
    const fixture = TestBed.createComponent(QuickSale);
    await fixture.whenStable();
    http
      .expectOne(`${BASE}/quick-sales/items`)
      .flush(problem(500, 'common.internal-error'), { status: 500, statusText: 'Error' });
    http.expectOne(`${BASE}/catalog/menu`).flush(menu);
    http.expectOne(`${BASE}/shifts/current`).flush(shift());
    await fixture.whenStable();

    expect(find(fixture, 'load-failed')).not.toBeNull();
    await press(fixture, 'retry');
    http.expectOne(`${BASE}/quick-sales/items`).flush(items);
    http.expectOne(`${BASE}/catalog/menu`).flush(menu);
    http.expectOne(`${BASE}/shifts/current`).flush(shift());
    await fixture.whenStable();

    expect(find(fixture, 'item-p1')).not.toBeNull();
  });
});
