import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { createEnvironmentInjector, EnvironmentInjector } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { Menu, QuickSaleItem } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { pen, problem, shift } from '../../../testing/register-fixtures';
import { UndoNotice } from '../../../shared/state/undo-notice';
import { UNDO_WINDOW_MS } from '../../../shared/state/undo-queue';
import { RegisterApi } from '../register/register-api';
import { QuickSaleStore } from './quick-sale-store';

const BASE = 'http://api.test/api/v1';

const items: QuickSaleItem[] = [
  { productId: 'p1', name: 'Naranja', price: pen('8.00') },
  { productId: 'p2', name: 'Fresa', price: pen('10.00') },
];

const option = (id: string, name: string, delta = '0.00') => ({
  id,
  name,
  available: true,
  allergens: [],
  priceDelta: pen(delta),
});

const menu: Menu = {
  categories: [
    {
      id: 'c',
      name: 'Jugos',
      products: [
        {
          id: 'p1',
          name: 'Naranja',
          available: true,
          description: null,
          imageUrl: null,
          allergens: [],
          price: pen('8.00'),
          modifierGroups: [],
        },
        {
          id: 'p2',
          name: 'Fresa',
          available: true,
          description: null,
          imageUrl: null,
          allergens: [],
          price: pen('10.00'),
          modifierGroups: [
            {
              id: 'size',
              name: 'Tamaño',
              required: true,
              minChoices: 1,
              maxChoices: 1,
              options: [option('small', 'Chico'), option('large', 'Grande', '2.00')],
            },
          ],
        },
      ],
    },
  ],
} as unknown as Menu;

describe('QuickSaleStore', () => {
  let store: QuickSaleStore;
  let http: HttpTestingController;
  let messages: MessageService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        MessageService,
        RegisterApi,
        QuickSaleStore,
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    store = TestBed.inject(QuickSaleStore);
    http = TestBed.inject(HttpTestingController);
    messages = TestBed.inject(MessageService);
  });

  afterEach(() => http.verify());

  function load() {
    store.load();
    http.expectOne(`${BASE}/quick-sales/items`).flush(items);
    http.expectOne(`${BASE}/catalog/menu`).flush(menu);
    http.expectOne(`${BASE}/shifts/current`).flush(shift());
  }

  it('reads the pinned items, the menu and the open shift', () => {
    load();

    expect(store.items()).toEqual(items);
    expect(store.hasShift()).toBe(true);
    expect(store.loaded()).toBe(true);
  });

  it('knows there is no shift', () => {
    store.load();
    http.expectOne(`${BASE}/quick-sales/items`).flush(items);
    http.expectOne(`${BASE}/catalog/menu`).flush(menu);
    http
      .expectOne(`${BASE}/shifts/current`)
      .flush(problem(404, 'instore.shift-not-found'), { status: 404, statusText: 'Not Found' });

    expect(store.hasShift()).toBe(false);
  });

  it('reports a failed read and can read again', () => {
    const added = vi.spyOn(messages, 'add');
    store.load();
    http
      .expectOne(`${BASE}/quick-sales/items`)
      .flush(problem(500, 'common.internal-error'), { status: 500, statusText: 'Error' });
    http.expectOne(`${BASE}/catalog/menu`).flush(menu);
    http.expectOne(`${BASE}/shifts/current`).flush(shift());

    expect(store.failed()).toBe(true);
    expect(added).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
  });

  it('treats a pinned item missing from the menu as unavailable', () => {
    load();

    expect(store.isAvailable(items[0])).toBe(true);
    expect(store.isAvailable({ productId: 'p9', name: 'Kiwi', price: pen('7.00') })).toBe(false);
    expect(store.isOnMenu({ productId: 'p9', name: 'Kiwi', price: pen('7.00') })).toBe(false);
  });

  it('needs the configurator only for an item with a required modifier group', () => {
    load();

    expect(store.needsOptions(items[0])).toBe(false);
    expect(store.needsOptions(items[1])).toBe(true);
  });

  it('adds a plain item once and merges a repeat into its quantity', () => {
    load();

    store.addPlain(items[0]);
    store.addPlain(items[0]);

    expect(store.cart()).toHaveLength(1);
    expect(store.cart()[0]).toMatchObject({ productId: 'p1', quantity: 2, unitPrice: pen('8.00') });
  });

  it('adds a configured item with its choices and the preview price', () => {
    load();

    store.addConfigured(items[1], {
      productId: 'p2',
      quantity: 2,
      modifierOptionIds: ['large'],
      note: 'Frío',
      allergy: false,
    });

    expect(store.cart()[0]).toMatchObject({
      productId: 'p2',
      quantity: 2,
      modifierOptionIds: ['large'],
      note: 'Frío',
      unitPrice: pen('12.00'),
    });
  });

  it('changes quantities within one to ninety-nine, removes a line and previews the total', () => {
    load();
    store.addPlain(items[0]);
    store.addPlain(items[1]);
    const first = store.cart()[0].key;

    store.changeQuantity(first, 2);
    expect(store.estimatedTotal()).toEqual(pen('34.00'));

    store.changeQuantity(first, -5);
    expect(store.cart()[0].quantity).toBe(1);

    store.changeQuantity(first, 500);
    expect(store.cart()[0].quantity).toBe(99);

    vi.useFakeTimers();
    try {
      store.remove(store.cart()[0]);
      vi.advanceTimersByTime(UNDO_WINDOW_MS);
    } finally {
      vi.useRealTimers();
    }
    expect(store.cart()).toHaveLength(1);
    expect(store.cart()[0].key).not.toBe(first);
  });

  describe('removing a line', () => {
    afterEach(() => vi.useRealTimers());

    it('hides the line and its price at once and lists an undo notice', () => {
      vi.useFakeTimers();
      load();
      store.addPlain(items[0]);
      store.addPlain(items[1]);

      store.remove(store.cart()[0]);

      expect(store.cart()).toHaveLength(1);
      expect(store.estimatedTotal()).toEqual(pen('10.00'));
      expect(TestBed.inject(UndoNotice).active()).toHaveLength(1);
    });

    it('brings the line back when the notice is undone, even after the window', () => {
      vi.useFakeTimers();
      load();
      store.addPlain(items[0]);
      const notice = TestBed.inject(UndoNotice);

      store.remove(store.cart()[0]);
      notice.undo(notice.active()[0]);
      vi.advanceTimersByTime(UNDO_WINDOW_MS * 2);

      expect(store.cart()).toHaveLength(1);
      expect(notice.active()).toHaveLength(0);
    });

    it('starts a fresh line when the same product is added while its removal waits', () => {
      vi.useFakeTimers();
      load();
      store.addPlain(items[0]);
      store.changeQuantity(store.cart()[0].key, 2);

      store.remove(store.cart()[0]);
      store.addPlain(items[0]);
      vi.advanceTimersByTime(UNDO_WINDOW_MS);

      expect(store.cart()).toHaveLength(1);
      expect(store.cart()[0].quantity).toBe(1);
    });
  });

  it('leaves no undo notice behind when the screen is destroyed inside the window', () => {
    vi.useFakeTimers();
    try {
      const screen = createEnvironmentInjector([QuickSaleStore], TestBed.inject(EnvironmentInjector));
      const own = screen.get(QuickSaleStore);
      own.addPlain(items[0]);
      own.remove(own.cart()[0]);
      expect(TestBed.inject(UndoNotice).active()).toHaveLength(1);

      screen.destroy();

      expect(TestBed.inject(UndoNotice).active()).toHaveLength(0);
    } finally {
      vi.useRealTimers();
    }
  });

  describe('selling', () => {
    function cart() {
      load();
      store.addPlain(items[0]);
    }

    it('posts the lines and payment under one key and keeps the ticket it returns', () => {
      cart();

      store.sell({ customerName: ' Ana ', method: 'CASH', received: '10.00' });
      const request = http.expectOne(`${BASE}/quick-sales`);
      expect(request.request.body).toEqual({
        customerName: 'Ana',
        lines: [{ productId: 'p1', quantity: 1, modifierOptionIds: [], note: null, allergy: false }],
        payment: { method: 'CASH', receivedAmount: pen('10.00'), walletProviderId: null },
      });
      expect(request.request.headers.get('Idempotency-Key')).toBeTruthy();
      request.flush({ id: 'k1', number: 7, quickSale: true }, { status: 201, statusText: 'Created' });

      expect(store.sold()?.number).toBe(7);
      expect(store.cart()).toEqual([]);
    });

    describe('when the screen is destroyed meanwhile', () => {
      let screen: EnvironmentInjector;
      let own: QuickSaleStore;

      beforeEach(() => {
        screen = createEnvironmentInjector([QuickSaleStore], TestBed.inject(EnvironmentInjector));
        own = screen.get(QuickSaleStore);
        own.load();
        http.expectOne(`${BASE}/quick-sales/items`).flush(items);
        http.expectOne(`${BASE}/catalog/menu`).flush(menu);
        http.expectOne(`${BASE}/shifts/current`).flush(shift());
        own.addPlain(items[0]);
      });

      it('lets the sale finish and says it was registered', () => {
        const added = vi.spyOn(messages, 'add');
        own.sell({ customerName: '', method: 'CARD', received: null });
        const request = http.expectOne(`${BASE}/quick-sales`);

        screen.destroy();
        expect(request.cancelled).toBe(false);
        request.flush({ id: 'k1', number: 7, quickSale: true }, { status: 201, statusText: 'Created' });

        expect(added).toHaveBeenCalledWith(expect.objectContaining({ severity: 'success' }));
      });

      it('reports a refused sale without reading the shift again', () => {
        const added = vi.spyOn(messages, 'add');
        own.sell({ customerName: '', method: 'CARD', received: null });
        const request = http.expectOne(`${BASE}/quick-sales`);

        screen.destroy();
        request.flush(problem(409, 'instore.shift-closed'), { status: 409, statusText: 'Conflict' });

        expect(added).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
      });
    });

    it('sends no received amount and no name for a card sale', () => {
      cart();

      store.sell({ customerName: '', method: 'CARD', received: null });

      const request = http.expectOne(`${BASE}/quick-sales`);
      expect(request.request.body.customerName).toBeNull();
      expect(request.request.body.payment).toEqual({ method: 'CARD', receivedAmount: null, walletProviderId: null });
      request.flush({ id: 'k1', number: 8 }, { status: 201, statusText: 'Created' });
    });

    it('keeps the cart and reuses the key when the sale is refused, then takes a new one', () => {
      cart();
      const added = vi.spyOn(messages, 'add');

      store.sell({ customerName: '', method: 'CARD', received: null });
      const first = http.expectOne(`${BASE}/quick-sales`);
      const key = first.request.headers.get('Idempotency-Key');
      first.flush(problem(409, 'instore.no-open-shift'), { status: 409, statusText: 'Conflict' });
      http.expectOne(`${BASE}/shifts/current`).flush(shift());

      expect(added).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
      expect(store.cart()).toHaveLength(1);
      expect(store.sold()).toBeNull();

      store.sell({ customerName: '', method: 'CARD', received: null });
      const second = http.expectOne(`${BASE}/quick-sales`);
      expect(second.request.headers.get('Idempotency-Key')).toBe(key);
      second.flush({ id: 'k1', number: 9 }, { status: 201, statusText: 'Created' });

      store.newSale();
      store.addPlain(items[0]);
      store.sell({ customerName: '', method: 'CARD', received: null });
      const third = http.expectOne(`${BASE}/quick-sales`);
      expect(third.request.headers.get('Idempotency-Key')).not.toBe(key);
      third.flush({ id: 'k2', number: 10 }, { status: 201, statusText: 'Created' });
    });

    it('starts over after a sale', () => {
      cart();
      store.sell({ customerName: '', method: 'CARD', received: null });
      http.expectOne(`${BASE}/quick-sales`).flush({ id: 'k1', number: 7 }, { status: 201, statusText: 'Created' });

      store.newSale();

      expect(store.sold()).toBeNull();
      expect(store.cart()).toEqual([]);
    });
  });
});
