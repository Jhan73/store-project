import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { StaffMember } from '../../../core/api/api-types';
import { StaffApi } from './staff-api';

const BASE = 'http://api.test/api/v1';

const member: StaffMember = {
  id: 'u1',
  email: 'ana@juguera.pe',
  role: 'SERVER',
  active: true,
  createdAt: '2026-01-01T03:30:00Z',
};

describe('StaffApi', () => {
  let api: StaffApi;
  let http: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        StaffApi,
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    api = TestBed.inject(StaffApi);
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => http.verify());

  it('lists a page of staff accounts', () => {
    api.list(2, 20).subscribe();

    const request = http.expectOne(`${BASE}/staff?page=2&size=20`);
    expect(request.request.method).toBe('GET');
    request.flush({ content: [member], page: 2, size: 20, totalElements: 41, totalPages: 3 });
  });

  it('invites an account by email and role, without any password', () => {
    api.create({ email: 'ana@juguera.pe', role: 'SERVER' }).subscribe();

    const request = http.expectOne(`${BASE}/staff`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'ana@juguera.pe', role: 'SERVER' });
    request.flush(member, { status: 201, statusText: 'Created' });
  });

  it('changes a role, toggles an account and resends the link with no version header', () => {
    api.changeRole('u1', 'CASHIER').subscribe();
    const role = http.expectOne(`${BASE}/staff/u1/role`);
    expect(role.request.method).toBe('PATCH');
    expect(role.request.body).toEqual({ role: 'CASHIER' });
    role.flush(member);

    api.deactivate('u1').subscribe();
    const off = http.expectOne(`${BASE}/staff/u1/deactivate`);
    expect(off.request.method).toBe('POST');
    expect(off.request.headers.has('If-Match')).toBe(false);
    off.flush(member);

    api.reactivate('u1').subscribe();
    http.expectOne(`${BASE}/staff/u1/reactivate`).flush(member);

    api.resendSetPasswordLink('u1').subscribe();
    const link = http.expectOne(`${BASE}/staff/u1/set-password-link`);
    expect(link.request.method).toBe('POST');
    link.flush(null, { status: 204, statusText: 'No Content' });
  });

  it('reads the store time zone from the settings', async () => {
    const zone = firstValueFrom(api.timeZone());

    http.expectOne(`${BASE}/admin/settings`).flush({ timeZone: 'America/Lima', currency: 'PEN' });

    expect(await zone).toBe('America/Lima');
  });
});
