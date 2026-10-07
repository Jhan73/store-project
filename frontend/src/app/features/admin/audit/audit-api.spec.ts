import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { StaffMember } from '../../../core/api/api-types';
import { AuditApi } from './audit-api';

const BASE = 'http://api.test/api/v1';

function member(id: string): StaffMember {
  return { id, email: `${id}@juguera.pe`, role: 'SERVER', active: true, createdAt: '2026-01-01T00:00:00Z' };
}

describe('AuditApi', () => {
  let api: AuditApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        AuditApi,
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    api = TestBed.inject(AuditApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('asks for a page without filters when none is set', () => {
    api.search({ page: 0, size: 20 }).subscribe();

    const request = http.expectOne(`${BASE}/audit-entries?page=0&size=20`);
    expect(request.request.method).toBe('GET');
    request.flush({ content: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
  });

  it('sends every filter that is set and leaves out the empty ones', () => {
    api
      .search({
        actorId: 'a1',
        entityType: 'PRODUCT',
        entityId: 'e1',
        action: 'PRODUCT_UPDATED',
        from: '2026-10-05T05:00:00.000Z',
        to: '2026-10-06T04:59:59.999999Z',
        page: 2,
        size: 50,
      })
      .subscribe();
    api.search({ entityType: '', actorId: undefined, page: 0, size: 20 }).subscribe();

    const request = http.expectOne((req) => req.params.has('actorId'));
    expect(request.request.params.get('actorId')).toBe('a1');
    expect(request.request.params.get('entityType')).toBe('PRODUCT');
    expect(request.request.params.get('entityId')).toBe('e1');
    expect(request.request.params.get('action')).toBe('PRODUCT_UPDATED');
    expect(request.request.params.get('from')).toBe('2026-10-05T05:00:00.000Z');
    expect(request.request.params.get('to')).toBe('2026-10-06T04:59:59.999999Z');
    expect(request.request.params.get('page')).toBe('2');
    expect(request.request.params.get('size')).toBe('50');
    request.flush({ content: [], page: 2, size: 50, totalElements: 0, totalPages: 0 });
    http.expectOne(`${BASE}/audit-entries?page=0&size=20`).flush({
      content: [],
      page: 0,
      size: 20,
      totalElements: 0,
      totalPages: 0,
    });
  });

  it('collects every page of staff accounts to name the actors', async () => {
    const result = firstValueFrom(api.staffAccounts());

    http.expectOne(`${BASE}/staff?page=0&size=100`).flush({
      content: [member('u1'), member('u2')],
      page: 0,
      size: 100,
      totalElements: 3,
      totalPages: 2,
    });
    await Promise.resolve();
    http.expectOne(`${BASE}/staff?page=1&size=100`).flush({
      content: [member('u3')],
      page: 1,
      size: 100,
      totalElements: 3,
      totalPages: 2,
    });

    expect((await result).map((item) => item.id)).toEqual(['u1', 'u2', 'u3']);
  });

  it('reads the store time zone from the settings', async () => {
    const result = firstValueFrom(api.timeZone());

    http.expectOne(`${BASE}/admin/settings`).flush({ timeZone: 'America/Lima', currency: 'PEN' });

    expect(await result).toBe('America/Lima');
  });
});
