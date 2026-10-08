import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_ORIGIN } from '../../../core/api/api-config';
import { AvailabilityApi } from './availability-api';

const CATALOG = 'http://api.test/api/v1/catalog';

describe('AvailabilityApi', () => {
  let api: AvailabilityApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        AvailabilityApi,
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    api = TestBed.inject(AvailabilityApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('reads the menu with every category and its availability', () => {
    let result: unknown;
    api.menu().subscribe((menu) => (result = menu));

    http.expectOne({ method: 'GET', url: `${CATALOG}/menu` }).flush({ categories: [] });

    expect(result).toEqual({ categories: [] });
  });

  it('sets a product availability as an absolute value, with no If-Match', () => {
    api.setProduct('p1', false).subscribe();

    const request = http.expectOne({ method: 'PUT', url: `${CATALOG}/products/p1/availability` });
    expect(request.request.body).toEqual({ available: false });
    expect(request.request.headers.has('If-Match')).toBe(false);
    request.flush({ id: 'p1', available: false });
  });

  it('sets a modifier option availability as an absolute value', () => {
    api.setOption('o1', true).subscribe();

    const request = http.expectOne({
      method: 'PUT',
      url: `${CATALOG}/modifier-options/o1/availability`,
    });
    expect(request.request.body).toEqual({ available: true });
    request.flush({ id: 'o1', available: true });
  });
});
