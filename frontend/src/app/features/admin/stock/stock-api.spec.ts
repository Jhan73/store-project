import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { Product, StaffMember, StockItem } from '../../../core/api/api-types';
import { StockApi } from './stock-api';

const BASE = 'http://api.test/api/v1';
const STOCK = `${BASE}/admin/stock-items`;

const item: StockItem = {
  productId: 'p1',
  tracked: true,
  onHand: 10,
  reserved: 2,
  available: 8,
  lowStockThreshold: 5,
  low: false,
};

function product(id: string): Product {
  return { id, name: `Product ${id}` } as Product;
}

function member(id: string): StaffMember {
  return { id, username: null, email: `${id}@juguera.pe`, role: 'SERVER', active: true, createdAt: '2026-01-01T00:00:00Z' };
}

describe('StockApi', () => {
  let api: StockApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        StockApi,
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    api = TestBed.inject(StockApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists the tracked items', async () => {
    const result = firstValueFrom(api.list());

    const request = http.expectOne(STOCK);
    expect(request.request.method).toBe('GET');
    request.flush([item]);

    expect(await result).toEqual([item]);
  });

  it('lists the low-stock items from their own endpoint', async () => {
    const result = firstValueFrom(api.low());

    const request = http.expectOne(`${STOCK}/low`);
    expect(request.request.method).toBe('GET');
    request.flush([item]);

    expect(await result).toEqual([item]);
  });

  it('reads one item', async () => {
    const result = firstValueFrom(api.item('p1'));

    http.expectOne(`${STOCK}/p1`).flush(item);

    expect(await result).toEqual(item);
  });

  it('turns tracking on or off with a PUT and no guard headers', () => {
    api.track('p1', { tracked: true, lowStockThreshold: 5 }).subscribe();

    const request = http.expectOne(`${STOCK}/p1`);
    expect(request.request.method).toBe('PUT');
    expect(request.request.body).toEqual({ tracked: true, lowStockThreshold: 5 });
    expect(request.request.headers.has('Idempotency-Key')).toBe(false);
    expect(request.request.headers.has('If-Match')).toBe(false);
    request.flush(item);
  });

  it('records an entry with the idempotency key of the intent', () => {
    api.recordEntry('p1', { quantity: 12, note: 'Supplier' }, 'key-1').subscribe();

    const request = http.expectOne(`${STOCK}/p1/entries`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ quantity: 12, note: 'Supplier' });
    expect(request.request.headers.get('Idempotency-Key')).toBe('key-1');
    request.flush(item);
  });

  it('records an adjustment with the idempotency key of the intent', () => {
    api.adjust('p1', { quantityDelta: -3, reason: 'Damaged', note: null }, 'key-2').subscribe();

    const request = http.expectOne(`${STOCK}/p1/adjustments`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ quantityDelta: -3, reason: 'Damaged', note: null });
    expect(request.request.headers.get('Idempotency-Key')).toBe('key-2');
    request.flush(item);
  });

  it('asks for a page of movements', () => {
    api.movements('p1', 2, 20).subscribe();

    const request = http.expectOne(`${STOCK}/p1/movements?page=2&size=20`);
    expect(request.request.method).toBe('GET');
    request.flush({ content: [], page: 2, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('collects every page of catalog products to name the items', async () => {
    const result = firstValueFrom(api.catalogProducts());

    http.expectOne(`${BASE}/admin/products?page=0&size=100`).flush({
      content: [product('p1'), product('p2')],
      page: 0,
      size: 100,
      totalElements: 3,
      totalPages: 2,
    });
    await Promise.resolve();
    http.expectOne(`${BASE}/admin/products?page=1&size=100`).flush({
      content: [product('p3')],
      page: 1,
      size: 100,
      totalElements: 3,
      totalPages: 2,
    });

    expect((await result).map((entry) => entry.id)).toEqual(['p1', 'p2', 'p3']);
  });

  it('collects every page of staff accounts to name the actors', async () => {
    const result = firstValueFrom(api.staffAccounts());

    http.expectOne(`${BASE}/staff?page=0&size=100`).flush({
      content: [member('u1')],
      page: 0,
      size: 100,
      totalElements: 2,
      totalPages: 2,
    });
    await Promise.resolve();
    http.expectOne(`${BASE}/staff?page=1&size=100`).flush({
      content: [member('u2')],
      page: 1,
      size: 100,
      totalElements: 2,
      totalPages: 2,
    });

    expect((await result).map((entry) => entry.id)).toEqual(['u1', 'u2']);
  });

  it('reads the store time zone from the settings', async () => {
    const result = firstValueFrom(api.timeZone());

    http.expectOne(`${BASE}/admin/settings`).flush({ timeZone: 'America/Lima', currency: 'PEN' });

    expect(await result).toBe('America/Lima');
  });
});
