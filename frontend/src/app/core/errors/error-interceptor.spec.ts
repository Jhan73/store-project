import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { API_ORIGIN } from '../api/api-config';
import { ApiError } from './api-error';
import { errorInterceptor } from './error-interceptor';

const API = 'http://api.test/api/v1/tables';

describe('errorInterceptor', () => {
  let client: HttpClient;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    client = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  async function failWith(body: object | string, init: { status: number; headers?: Record<string, string> }) {
    const result = firstValueFrom(client.get(API)).catch((error: unknown) => error);
    http.expectOne(API).flush(body, { statusText: 'x', ...init });
    return (await result) as ApiError;
  }

  it('turns a Problem Details body into a typed error', async () => {
    const error = await failWith(
      {
        type: 'about:blank',
        title: 'Conflict',
        status: 409,
        detail: 'English detail for developers',
        code: 'identity.last-active-admin-required',
        correlationId: 'abc-123',
      },
      { status: 409 },
    );

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(409);
    expect(error.code).toBe('identity.last-active-admin-required');
    expect(error.correlationId).toBe('abc-123');
  });

  it('keeps validation errors per field and extra members of the problem', async () => {
    const error = await failWith(
      {
        status: 400,
        code: 'common.validation-failed',
        errors: [{ field: 'lines[0].quantity', constraint: 'Positive', message: 'must be greater than 0' }],
        lockedUntil: '2026-09-18T10:00:00Z',
      },
      { status: 400 },
    );

    expect(error.fieldErrors).toEqual([
      { field: 'lines[0].quantity', constraint: 'Positive', message: 'must be greater than 0' },
    ]);
    expect(error.properties).toEqual({ lockedUntil: '2026-09-18T10:00:00Z' });
  });

  it('falls back to the X-Request-Id header for the correlation id', async () => {
    const error = await failWith({ status: 500, code: 'common.internal-error' }, {
      status: 500,
      headers: { 'X-Request-Id': 'from-header' },
    });

    expect(error.correlationId).toBe('from-header');
  });

  it('survives a body that is not Problem Details', async () => {
    const error = await failWith('<html>Bad gateway</html>', { status: 502 });

    expect(error).toBeInstanceOf(ApiError);
    expect(error.status).toBe(502);
    expect(error.code).toBeNull();
    expect(error.correlationId).toBeNull();
    expect(error.fieldErrors).toEqual([]);
  });

  it('reports a network failure as status 0', async () => {
    const result = firstValueFrom(client.get(API)).catch((error: unknown) => error);
    http.expectOne(API).error(new ProgressEvent('error'));

    const error = (await result) as ApiError;
    expect(error.status).toBe(0);
    expect(error.code).toBeNull();
  });

  it('leaves successful responses and other origins alone', async () => {
    const ok = firstValueFrom(client.get(API));
    http.expectOne(API).flush({ ok: true });
    expect(await ok).toEqual({ ok: true });

    const other = firstValueFrom(client.get('https://cdn.example.com/x')).catch((e: unknown) => e);
    http.expectOne('https://cdn.example.com/x').flush('nope', { status: 404, statusText: 'Not Found' });
    expect(await other).not.toBeInstanceOf(ApiError);
  });
});
