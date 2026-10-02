import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { API_ORIGIN } from '../api/api-config';
import { AuthApi } from './auth-api';

const session = { accessToken: 'jwt', tokenType: 'Bearer', userId: 'u1', role: 'CASHIER' as const };

describe('AuthApi', () => {
  let api: AuthApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    api = TestBed.inject(AuthApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('logs in with the credentials and accepts the refresh cookie', () => {
    let result: unknown;
    api.login({ email: 'a@b.pe', password: 'secret' }).subscribe((value) => (result = value));

    const request = http.expectOne('http://api.test/api/v1/auth/login');
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'a@b.pe', password: 'secret' });
    expect(request.request.withCredentials).toBe(true);
    request.flush(session);
    expect(result).toEqual(session);
  });

  it('refreshes with the cookie and the CSRF-defense header', () => {
    api.refresh().subscribe();

    const request = http.expectOne('http://api.test/api/v1/auth/refresh');
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.headers.has('X-Requested-With')).toBe(true);
    request.flush(session);
  });

  it('logs out with the cookie and the CSRF-defense header', () => {
    api.logout().subscribe();

    const request = http.expectOne('http://api.test/api/v1/auth/logout');
    expect(request.request.method).toBe('POST');
    expect(request.request.withCredentials).toBe(true);
    expect(request.request.headers.has('X-Requested-With')).toBe(true);
    request.flush(null, { status: 204, statusText: 'No Content' });
  });
});
