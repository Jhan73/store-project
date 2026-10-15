import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_ORIGIN } from '../../core/api/api-config';
import { DisplayApi } from './display-api';

describe('DisplayApi', () => {
  it('reads the numbers and first names for the customer screen', () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        DisplayApi,
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    const http = TestBed.inject(HttpTestingController);
    let result: unknown;

    TestBed.inject(DisplayApi).read().subscribe((value) => (result = value));

    const request = http.expectOne('http://api.test/api/v1/board/display');
    expect(request.request.method).toBe('GET');
    request.flush({ preparing: [{ shortNumber: 'L-001', firstName: 'Ana' }], ready: [] });
    expect(result).toEqual({ preparing: [{ shortNumber: 'L-001', firstName: 'Ana' }], ready: [] });
    http.verify();
  });
});
