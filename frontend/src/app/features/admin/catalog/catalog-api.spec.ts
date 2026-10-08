import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_ORIGIN } from '../../../core/api/api-config';
import { CatalogApi } from './catalog-api';

const BASE = 'http://api.test/api/v1';
const ETAG = '"3"';

describe('CatalogApi', () => {
  let api: CatalogApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        CatalogApi,
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    api = TestBed.inject(CatalogApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  function expectRequest(method: string, url: string) {
    const request = http.expectOne(`${BASE}${url}`);
    expect(request.request.method).toBe(method);
    return request;
  }

  it('reads the store currency from the settings', () => {
    let currency = '';
    api.currency().subscribe((value) => (currency = value));

    expectRequest('GET', '/admin/settings').flush({ currency: 'PEN' });

    expect(currency).toBe('PEN');
  });

  it('lists the allergens', () => {
    api.allergens().subscribe();
    expectRequest('GET', '/admin/allergens').flush(['MILK']);
  });

  describe('stations', () => {
    it('lists, creates and renames with the item version', () => {
      api.stations().subscribe();
      expectRequest('GET', '/admin/stations').flush([]);

      api.createStation({ name: 'Bar' }).subscribe();
      expect(expectRequest('POST', '/admin/stations').request.body).toEqual({ name: 'Bar' });

      api.renameStation('s1', ETAG, { name: 'Cold bar' }).subscribe();
      const rename = expectRequest('PUT', '/admin/stations/s1');
      expect(rename.request.headers.get('If-Match')).toBe(ETAG);
      expect(rename.request.body).toEqual({ name: 'Cold bar' });
    });
  });

  describe('categories', () => {
    it('lists and creates', () => {
      api.categories().subscribe();
      expectRequest('GET', '/admin/categories').flush([]);

      api.createCategory({ name: 'Juices', displayOrder: 1 }).subscribe();
      expect(expectRequest('POST', '/admin/categories').request.body).toEqual({
        name: 'Juices',
        displayOrder: 1,
      });
    });

    it('changes, deactivates and reactivates with the item version', () => {
      api
        .changeCategory('c1', ETAG, { name: 'Juices', displayOrder: 2, stationId: 's1' })
        .subscribe();
      expect(expectRequest('PUT', '/admin/categories/c1').request.headers.get('If-Match')).toBe(
        ETAG,
      );

      api.deactivateCategory('c1', ETAG).subscribe();
      const deactivate = expectRequest('POST', '/admin/categories/c1/deactivate');
      expect(deactivate.request.headers.get('If-Match')).toBe(ETAG);

      api.reactivateCategory('c1', ETAG).subscribe();
      const reactivate = expectRequest('POST', '/admin/categories/c1/reactivate');
      expect(reactivate.request.headers.get('If-Match')).toBe(ETAG);
    });
  });

  describe('modifier groups', () => {
    const body = {
      name: 'Size',
      required: true,
      minChoices: 1,
      maxChoices: 1,
      options: [{ name: 'Large', priceDelta: { amount: '2.00', currency: 'PEN' } }],
    };

    it('lists, reads and creates', () => {
      api.modifierGroups().subscribe();
      expectRequest('GET', '/admin/modifier-groups').flush([]);

      api.modifierGroup('g1').subscribe();
      expectRequest('GET', '/admin/modifier-groups/g1').flush({});

      api.createModifierGroup(body).subscribe();
      expect(expectRequest('POST', '/admin/modifier-groups').request.body).toEqual(body);
    });

    it('replaces and deletes with the group version', () => {
      api.changeModifierGroup('g1', ETAG, body).subscribe();
      const change = expectRequest('PUT', '/admin/modifier-groups/g1');
      expect(change.request.headers.get('If-Match')).toBe(ETAG);
      expect(change.request.body).toEqual(body);

      api.deleteModifierGroup('g1', ETAG).subscribe();
      const remove = expectRequest('DELETE', '/admin/modifier-groups/g1');
      expect(remove.request.headers.get('If-Match')).toBe(ETAG);
    });
  });

  describe('products', () => {
    const body = {
      name: 'Orange juice',
      categoryId: 'c1',
      price: { amount: '9.50', currency: 'PEN' },
      displayOrder: 1,
      quickSalePinned: false,
    };
    const empty = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 };

    it('lists a page, optionally filtered by category', () => {
      api.products({ page: 2, size: 20 }).subscribe();
      const first = http.expectOne(`${BASE}/admin/products?page=2&size=20`);
      expect(first.request.method).toBe('GET');
      first.flush(empty);

      api.products({ categoryId: 'c1', page: 0, size: 20 }).subscribe();
      http.expectOne(`${BASE}/admin/products?categoryId=c1&page=0&size=20`).flush(empty);
    });

    it('reads, creates and replaces with the product version', () => {
      api.product('p1').subscribe();
      expectRequest('GET', '/admin/products/p1').flush({});

      api.createProduct(body).subscribe();
      expect(expectRequest('POST', '/admin/products').request.body).toEqual(body);

      api.changeProduct('p1', ETAG, body).subscribe();
      expect(expectRequest('PUT', '/admin/products/p1').request.headers.get('If-Match')).toBe(ETAG);
    });

    it('deactivates and reactivates with the product version', () => {
      api.deactivateProduct('p1', ETAG).subscribe();
      const deactivate = expectRequest('POST', '/admin/products/p1/deactivate');
      expect(deactivate.request.headers.get('If-Match')).toBe(ETAG);

      api.reactivateProduct('p1', ETAG).subscribe();
      const reactivate = expectRequest('POST', '/admin/products/p1/reactivate');
      expect(reactivate.request.headers.get('If-Match')).toBe(ETAG);
    });

    it('uploads the image as multipart under the part name "file"', () => {
      const file = new File(['x'], 'juice.png', { type: 'image/png' });
      api.replaceProductImage('p1', ETAG, file).subscribe();

      const upload = expectRequest('PUT', '/admin/products/p1/image');
      expect(upload.request.headers.get('If-Match')).toBe(ETAG);
      expect(upload.request.body).toBeInstanceOf(FormData);
      expect((upload.request.body as FormData).get('file')).toBe(file);
    });

    it('removes the image with the product version', () => {
      api.removeProductImage('p1', ETAG).subscribe();
      const remove = expectRequest('DELETE', '/admin/products/p1/image');
      expect(remove.request.headers.get('If-Match')).toBe(ETAG);
    });
  });

  describe('availability', () => {
    it('sets a product and an option absolutely, without a version', () => {
      api.setProductAvailability('p1', false).subscribe();
      const product = expectRequest('PUT', '/catalog/products/p1/availability');
      expect(product.request.body).toEqual({ available: false });
      expect(product.request.headers.has('If-Match')).toBe(false);

      api.setOptionAvailability('o1', true).subscribe();
      const option = expectRequest('PUT', '/catalog/modifier-options/o1/availability');
      expect(option.request.body).toEqual({ available: true });
    });
  });
});
