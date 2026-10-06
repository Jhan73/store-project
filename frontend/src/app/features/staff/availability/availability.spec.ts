import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { Observable, Subject } from 'rxjs';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { Menu, MenuModifierGroup } from '../../../core/api/api-types';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { RealtimeClient } from '../../../core/realtime/realtime-client';
import { Availability } from './availability';
import { AvailabilityApi } from './availability-api';

const CATALOG = 'http://api.test/api/v1/catalog';
const MENU = `${CATALOG}/menu`;

const money = { amount: '10.00', currency: 'PEN' };

function option(id: string, name: string, available: boolean) {
  return { id, name, available, allergens: [], priceDelta: money };
}

function product(
  id: string,
  name: string,
  available: boolean,
  modifierGroups: MenuModifierGroup[] = [],
) {
  return {
    id,
    name,
    available,
    description: null,
    imageUrl: null,
    allergens: [],
    price: money,
    modifierGroups,
  };
}

function menu(overrides: { p1?: boolean; p2?: boolean; o1?: boolean } = {}): Menu {
  const group: MenuModifierGroup = {
    id: 'g1',
    name: 'Endulzante',
    required: false,
    minChoices: 0,
    maxChoices: 1,
    options: [option('o1', 'Miel', overrides.o1 ?? true), option('o2', 'Azúcar', false)],
  };
  return {
    categories: [
      {
        id: 'c1',
        name: 'Jugos',
        products: [
          product('p1', 'Naranja', overrides.p1 ?? true, [group]),
          product('p2', 'Fresa', overrides.p2 ?? false, [group]),
        ],
      },
      { id: 'c2', name: 'Sándwiches', products: [product('p3', 'Pollo', true)] },
    ],
  };
}

class FakeRealtime {
  readonly connected = signal(false);
  readonly isConnected = this.connected.asReadonly();
  readonly signals = new Subject<void>();

  refetch(): Observable<void> {
    return this.signals.asObservable();
  }
}

const serverError = {
  type: 'about:blank',
  status: 500,
  code: 'common.internal-error',
  correlationId: 'c',
};
const failure = { status: 500, statusText: 'Server Error' };

describe('Availability', () => {
  let realtime: FakeRealtime;
  let http: HttpTestingController;

  beforeEach(() => {
    realtime = new FakeRealtime();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        MessageService,
        AvailabilityApi,
        { provide: RealtimeClient, useValue: realtime },
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function open(initial: Menu = menu()) {
    const fixture = TestBed.createComponent(Availability);
    await fixture.whenStable();
    http.expectOne(MENU).flush(initial);
    await fixture.whenStable();
    return fixture;
  }

  // Connecting emits the re-fetch signal; answering that fetch is what enables the actions.
  async function connect(fixture: ComponentFixture<Availability>, latest: Menu = menu()) {
    realtime.connected.set(true);
    realtime.signals.next();
    http.expectOne(MENU).flush(latest);
    await fixture.whenStable();
  }

  async function ready(initial: Menu = menu()) {
    const fixture = await open(initial);
    await connect(fixture, initial);
    return fixture;
  }

  const host = (fixture: ComponentFixture<Availability>) => fixture.nativeElement as HTMLElement;
  const box = (fixture: ComponentFixture<Availability>, testId: string) =>
    host(fixture).querySelector<HTMLInputElement>(`p-toggleswitch[data-testid="${testId}"] input`)!;
  const banner = (fixture: ComponentFixture<Availability>) =>
    host(fixture).querySelector('[data-testid="offline-banner"]');
  const stateText = (fixture: ComponentFixture<Availability>, id: string) =>
    host(fixture).querySelector(`[data-testid="state-${id}"]`)?.textContent ?? '';

  it('lists the products by category and the modifier options once, with their state', async () => {
    const fixture = await ready();
    const text = host(fixture).textContent ?? '';

    expect(text).toContain('Jugos');
    expect(text).toContain('Sándwiches');
    expect(text).toContain('Endulzante');
    expect(box(fixture, 'product-p1').checked).toBe(true);
    expect(box(fixture, 'product-p2').checked).toBe(false);
    expect(host(fixture).querySelectorAll('[data-testid^="product-"]').length).toBe(3);
    expect(host(fixture).querySelectorAll('[data-testid="option-o1"]').length).toBe(1);
    expect(box(fixture, 'option-o2').checked).toBe(false);
  });

  it('says in text which items are out, not only through the control', async () => {
    const fixture = await ready();

    expect(stateText(fixture, 'p2')).toContain('Agotado');
    expect(stateText(fixture, 'p1')).toContain('Disponible');
  });

  it('keeps the actions disabled behind a banner until the connection has re-read the menu', async () => {
    const fixture = await open();

    expect(banner(fixture)).not.toBeNull();
    expect(box(fixture, 'product-p1').disabled).toBe(true);

    await connect(fixture);

    expect(banner(fixture)).toBeNull();
    expect(box(fixture, 'product-p1').disabled).toBe(false);
  });

  it('sends the product toggle, blocks it while in flight and shows the saved state', async () => {
    const fixture = await ready();

    box(fixture, 'product-p1').click();
    const request = http.expectOne({ method: 'PUT', url: `${CATALOG}/products/p1/availability` });
    await fixture.whenStable();

    expect(request.request.body).toEqual({ available: false });
    expect(box(fixture, 'product-p1').disabled).toBe(true);

    request.flush({ id: 'p1', available: false });
    await fixture.whenStable();

    expect(box(fixture, 'product-p1').disabled).toBe(false);
    expect(box(fixture, 'product-p1').checked).toBe(false);
    expect(stateText(fixture, 'p1')).toContain('Agotado');
  });

  it('sends the option toggle', async () => {
    const fixture = await ready();

    box(fixture, 'option-o2').click();
    const request = http.expectOne({
      method: 'PUT',
      url: `${CATALOG}/modifier-options/o2/availability`,
    });
    request.flush({ id: 'o2', available: true });
    await fixture.whenStable();

    expect(request.request.body).toEqual({ available: true });
    expect(box(fixture, 'option-o2').checked).toBe(true);
    expect(stateText(fixture, 'o2')).toContain('Disponible');
  });

  it('re-reads the menu when another client changes the catalog', async () => {
    const fixture = await ready();

    realtime.signals.next();
    http.expectOne(MENU).flush(menu({ p1: false, o1: false }));
    await fixture.whenStable();

    expect(box(fixture, 'product-p1').checked).toBe(false);
    expect(box(fixture, 'option-o1').checked).toBe(false);
  });

  it('applies only the latest of overlapping re-reads', async () => {
    const fixture = await ready();

    realtime.signals.next();
    realtime.signals.next();
    const [first, second] = http.match(MENU);
    second.flush(menu({ p1: false }));
    first.flush(menu({ p1: true }));
    await fixture.whenStable();

    expect(box(fixture, 'product-p1').checked).toBe(false);
  });

  it('puts the control back to the server value and re-reads when a toggle fails', async () => {
    const fixture = await ready();

    box(fixture, 'product-p1').click();
    await fixture.whenStable();
    expect(box(fixture, 'product-p1').checked).toBe(false);
    http
      .expectOne({ method: 'PUT', url: `${CATALOG}/products/p1/availability` })
      .flush(serverError, failure);
    await fixture.whenStable();

    expect(box(fixture, 'product-p1').checked).toBe(true);
    expect(box(fixture, 'product-p1').disabled).toBe(false);

    http.expectOne(MENU).flush(menu({ p1: false }));
    await fixture.whenStable();
    expect(box(fixture, 'product-p1').checked).toBe(false);
  });

  it('disables everything while disconnected and until the reconnection has re-read the menu', async () => {
    const fixture = await ready();

    realtime.connected.set(false);
    await fixture.whenStable();
    expect(banner(fixture)).not.toBeNull();
    expect(box(fixture, 'product-p1').disabled).toBe(true);

    realtime.connected.set(true);
    await fixture.whenStable();
    expect(banner(fixture)).not.toBeNull();
    expect(box(fixture, 'product-p1').disabled).toBe(true);

    realtime.signals.next();
    http.expectOne(MENU).flush(menu({ p1: false }));
    await fixture.whenStable();

    expect(banner(fixture)).toBeNull();
    expect(box(fixture, 'product-p1').disabled).toBe(false);
    expect(box(fixture, 'product-p1').checked).toBe(false);
  });

  it('does not enable the actions on a read that started before the connection came back', async () => {
    const fixture = await ready();
    realtime.connected.set(false);
    await fixture.whenStable();

    realtime.signals.next();
    const early = http.expectOne(MENU);
    realtime.connected.set(true);
    early.flush(menu());
    await fixture.whenStable();

    expect(box(fixture, 'product-p1').disabled).toBe(true);
  });

  it('offers a retry when the first read fails', async () => {
    const fixture = TestBed.createComponent(Availability);
    await fixture.whenStable();
    http.expectOne(MENU).flush(serverError, failure);
    await fixture.whenStable();

    host(fixture).querySelector<HTMLButtonElement>('[data-testid="retry"]')!.click();
    http.expectOne(MENU).flush(menu());
    await fixture.whenStable();

    expect(host(fixture).querySelector('[data-testid="retry"]')).toBeNull();
    expect(host(fixture).textContent).toContain('Jugos');
  });

  const retryButton = (fixture: ComponentFixture<Availability>) =>
    host(fixture).querySelector<HTMLButtonElement>('[data-testid="retry"]');

  it('disables a loaded screen and offers a retry when a re-read fails, then recovers', async () => {
    const fixture = await ready();

    realtime.signals.next();
    http.expectOne(MENU).flush(serverError, failure);
    await fixture.whenStable();

    expect(host(fixture).textContent).toContain('Jugos');
    expect(box(fixture, 'product-p1').disabled).toBe(true);
    expect(retryButton(fixture)).not.toBeNull();

    retryButton(fixture)!.click();
    http.expectOne(MENU).flush(menu({ p1: false }));
    await fixture.whenStable();

    expect(retryButton(fixture)).toBeNull();
    expect(box(fixture, 'product-p1').disabled).toBe(false);
    expect(box(fixture, 'product-p1').checked).toBe(false);
  });

  it('offers a retry when the latest read fails after an earlier one was superseded', async () => {
    const fixture = await ready();

    realtime.signals.next();
    realtime.signals.next();
    const [first, second] = http.match(MENU);
    first.flush(menu({ p1: false }));
    second.flush(serverError, failure);
    await fixture.whenStable();

    expect(box(fixture, 'product-p1').disabled).toBe(true);
    expect(retryButton(fixture)).not.toBeNull();

    retryButton(fixture)!.click();
    http.expectOne(MENU).flush(menu({ p1: false }));
    await fixture.whenStable();

    expect(box(fixture, 'product-p1').disabled).toBe(false);
    expect(box(fixture, 'product-p1').checked).toBe(false);
  });

  it('tells offline, updating and failed states apart', async () => {
    const fixture = await open();
    const offline = banner(fixture)?.textContent ?? '';
    expect(offline).toContain('Sin conexión');
    expect(retryButton(fixture)).toBeNull();

    realtime.connected.set(true);
    await fixture.whenStable();
    const updating = banner(fixture)?.textContent ?? '';
    expect(updating).not.toContain('Sin conexión');
    expect(updating).not.toBe(offline);
    expect(retryButton(fixture)).toBeNull();

    realtime.signals.next();
    http.expectOne(MENU).flush(serverError, failure);
    await fixture.whenStable();
    const failed = banner(fixture)?.textContent ?? '';
    expect(failed).not.toContain('Sin conexión');
    expect(failed).not.toBe(updating);
    expect(failed).not.toBe(offline);
    expect(retryButton(fixture)).not.toBeNull();
  });

  it('cancels a toggle and a read when the screen is destroyed, so no late error toast appears', async () => {
    const fixture = await ready();
    const toasts = vi.spyOn(TestBed.inject(MessageService), 'add');

    box(fixture, 'product-p1').click();
    const toggle = http.expectOne({ method: 'PUT', url: `${CATALOG}/products/p1/availability` });
    realtime.signals.next();
    const read = http.expectOne(MENU);
    fixture.destroy();

    expect(toggle.cancelled).toBe(true);
    expect(read.cancelled).toBe(true);
    expect(toasts).not.toHaveBeenCalled();
  });

  it('stops listening to the catalog when the screen is destroyed', async () => {
    const fixture = await ready();

    fixture.destroy();
    realtime.signals.next();

    http.expectNone(MENU);
  });
});
