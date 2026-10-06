import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { StaffMember, StaffPage } from '../../../core/api/api-types';
import { AuthStore } from '../../../core/auth/auth-store';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import {
  chooseOption,
  optionLabels,
  selectedLabel,
  selectIsDisabled,
} from '../../../testing/primeng-controls';
import { StaffApi } from './staff-api';
import { StaffList } from './staff-list';

const API = 'http://api.test/api/v1';
const LIST = `${API}/staff?page=0&size=20`;

const ana: StaffMember = {
  id: 'u1',
  email: 'ana@juguera.pe',
  role: 'SERVER',
  active: true,
  createdAt: '2026-01-01T03:30:00Z',
};
const beto: StaffMember = { ...ana, id: 'u2', email: 'beto@juguera.pe', role: 'CASHIER', active: false };
const me: StaffMember = { ...ana, id: 'me', email: 'yo@juguera.pe', role: 'ADMIN' };

function page(content: StaffMember[], overrides: Partial<StaffPage> = {}): StaffPage {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: 1, ...overrides };
}

async function render(first: StaffPage = page([ana, beto, me])) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      MessageService,
      StaffApi,
      { provide: AuthStore, useValue: { session: signal({ userId: 'me', role: 'ADMIN' }) } },
      { provide: API_ORIGIN, useValue: 'http://api.test' },
    ],
  });
  const fixture = TestBed.createComponent(StaffList);
  const http = TestBed.inject(HttpTestingController);
  const toast = vi.spyOn(TestBed.inject(MessageService), 'add');
  await fixture.whenStable();
  http.expectOne(LIST).flush(first);
  http.expectOne(`${API}/admin/settings`).flush({ timeZone: 'America/Lima', currency: 'PEN' });
  await fixture.whenStable();
  return { fixture, host: fixture.nativeElement as HTMLElement, http, toast };
}

function type(host: HTMLElement, selector: string, value: string) {
  const input = host.querySelector<HTMLInputElement>(selector)!;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

function submit(host: HTMLElement) {
  host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
}

function button(host: HTMLElement, testId: string) {
  return host.querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`);
}

function rows(host: HTMLElement) {
  return Array.from(host.querySelectorAll('tbody tr')).map((row) => row.textContent ?? '');
}

function problem(status: number, code: string) {
  return [
    { type: 'about:blank', status, code, correlationId: 'c' },
    { status, statusText: 'Error' },
  ] as const;
}

describe('StaffList', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('lists the accounts with their role, state and creation time in the store time zone', async () => {
    const { host } = await render();

    const [first, second] = rows(host);
    expect(first).toContain('ana@juguera.pe');
    expect(first).toContain('Mozo');
    expect(first).toContain('Activa');
    expect(first).toContain('31/12');
    expect(second).toContain('beto@juguera.pe');
    expect(second).toContain('Inactiva');
  });

  it('invites an account with an email and a role and never asks for a password', async () => {
    const { fixture, host, http } = await render();

    expect(host.querySelector('input[type="password"]')).toBeNull();
    type(host, '#staff-email', ' nueva@juguera.pe ');
    await chooseOption(fixture, host, 'staff-role', 'Cajero');
    submit(host);
    const request = http.expectOne(`${API}/staff`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({ email: 'nueva@juguera.pe', role: 'CASHIER' });
    request.flush({ ...ana, id: 'u9', email: 'nueva@juguera.pe', role: 'CASHIER' }, {
      status: 201,
      statusText: 'Created',
    });
    await fixture.whenStable();
    http.expectOne(LIST).flush(page([ana, beto, me, { ...ana, id: 'u9', email: 'nueva@juguera.pe' }]));
    await fixture.whenStable();

    expect(host.querySelector('[role="status"]')?.textContent).toContain('nueva@juguera.pe');
    expect(rows(host)).toHaveLength(4);
    expect(host.querySelector<HTMLInputElement>('#staff-email')!.value).toBe('');
  });

  it('does not invite an address that is not an email', async () => {
    const { fixture, host, http } = await render();

    type(host, '#staff-email', 'not-an-email');
    submit(host);
    await fixture.whenStable();

    http.expectNone(`${API}/staff`);
    expect(host.querySelector('#staff-email')!.getAttribute('aria-invalid')).toBe('true');
  });

  it('offers only staff roles when inviting', async () => {
    const { fixture, host } = await render();

    expect(await optionLabels(fixture, host, 'staff-role')).toEqual([
      'Mozo',
      'Cajero',
      'Administrador',
    ]);
  });

  it('keeps the typed email and tells the user when it is already registered', async () => {
    const { fixture, host, http, toast } = await render();

    type(host, '#staff-email', 'ana@juguera.pe');
    submit(host);
    http.expectOne(`${API}/staff`).flush(...problem(409, 'identity.email-already-registered'));
    await fixture.whenStable();

    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
    expect(host.querySelector<HTMLInputElement>('#staff-email')!.value).toBe('ana@juguera.pe');
    expect(host.querySelector('[role="status"]')).toBeNull();
  });

  it('changes a role and blocks the control meanwhile', async () => {
    const { fixture, host, http } = await render();

    await chooseOption(fixture, host, 'role-u1', 'Cajero');
    await fixture.whenStable();
    expect(selectIsDisabled(host, 'role-u1')).toBe(true);
    const request = http.expectOne(`${API}/staff/u1/role`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ role: 'CASHIER' });
    request.flush({ ...ana, role: 'CASHIER' });
    await fixture.whenStable();

    expect(rows(host)[0]).toContain('Cajero');
    expect(selectIsDisabled(host, 'role-u1')).toBe(false);
    expect(selectedLabel(host, 'role-u1')).toBe('Cajero');
  });

  it('puts the role control back to what the server holds when the change is refused', async () => {
    const { fixture, host, http, toast } = await render();

    await chooseOption(fixture, host, 'role-u1', 'Administrador');
    http.expectOne(`${API}/staff/u1/role`).flush(...problem(409, 'identity.last-active-admin-required'));
    await fixture.whenStable();

    expect(toast).toHaveBeenCalled();
    expect(selectedLabel(host, 'role-u1')).toBe('Mozo');
    expect(selectIsDisabled(host, 'role-u1')).toBe(false);
  });

  it('deactivates and reactivates an account and blocks the button meanwhile', async () => {
    const { fixture, host, http } = await render();

    button(host, 'toggle-u1')!.click();
    await fixture.whenStable();
    expect(button(host, 'toggle-u1')!.disabled).toBe(true);
    button(host, 'toggle-u1')!.click();
    http.expectOne(`${API}/staff/u1/deactivate`).flush({ ...ana, active: false });
    await fixture.whenStable();
    expect(rows(host)[0]).toContain('Inactiva');
    expect(button(host, 'toggle-u1')!.disabled).toBe(false);

    button(host, 'toggle-u2')!.click();
    http.expectOne(`${API}/staff/u2/reactivate`).flush({ ...beto, active: true });
    await fixture.whenStable();
    expect(rows(host)[1]).toContain('Activa');
  });

  it('keeps the state and tells the user when the last administrator cannot be deactivated', async () => {
    const { fixture, host, http, toast } = await render();

    button(host, 'toggle-u1')!.click();
    http.expectOne(`${API}/staff/u1/deactivate`).flush(...problem(409, 'identity.last-active-admin-required'));
    await fixture.whenStable();

    expect(toast).toHaveBeenCalled();
    expect(rows(host)[0]).toContain('Activa');
    expect(button(host, 'toggle-u1')!.disabled).toBe(false);
  });

  it('does not let the administrator act on their own account', async () => {
    const { host } = await render();

    expect(button(host, 'toggle-me')!.disabled).toBe(true);
    expect(selectIsDisabled(host, 'role-me')).toBe(true);
    expect(button(host, 'resend-me')!.disabled).toBe(true);
  });

  it('resends the set-password link of an active account and confirms without showing it', async () => {
    const { fixture, host, http } = await render();

    button(host, 'resend-u1')!.click();
    await fixture.whenStable();
    expect(button(host, 'resend-u1')!.disabled).toBe(true);
    http
      .expectOne(`${API}/staff/u1/set-password-link`)
      .flush(null, { status: 204, statusText: 'No Content' });
    await fixture.whenStable();

    expect(host.querySelector('[role="status"]')?.textContent).toContain('ana@juguera.pe');
    expect(button(host, 'resend-u1')!.disabled).toBe(false);
    expect(button(host, 'resend-u2')).toBeNull();
  });

  it('tells the user when the link cannot be resent', async () => {
    const { fixture, host, http, toast } = await render();

    button(host, 'resend-u1')!.click();
    http.expectOne(`${API}/staff/u1/set-password-link`).flush(...problem(404, 'common.not-found'));
    await fixture.whenStable();
    http.expectOne(LIST).flush(page([beto, me]));
    await fixture.whenStable();

    expect(toast).toHaveBeenCalled();
    expect(host.querySelector('[role="status"]')).toBeNull();
    expect(rows(host)).toHaveLength(2);
  });

  it('reloads the current page when an account is gone', async () => {
    const { fixture, host, http } = await render();

    button(host, 'toggle-u1')!.click();
    http.expectOne(`${API}/staff/u1/deactivate`).flush(...problem(404, 'common.not-found'));
    await fixture.whenStable();
    http.expectOne(LIST).flush(page([beto, me]));
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(2);
    expect(rows(host)[0]).toContain('beto@juguera.pe');
  });

  it('moves between pages and shows only the latest page asked for', async () => {
    const { fixture, host, http } = await render(page([ana], { totalPages: 3, totalElements: 41 }));

    button(host, 'next-page')!.click();
    const second = http.expectOne(`${API}/staff?page=1&size=20`);
    button(host, 'next-page')!.click();
    const third = http.expectOne(`${API}/staff?page=1&size=20`);
    third.flush(page([beto], { page: 1, totalPages: 3, totalElements: 41 }));
    second.flush(page([me], { page: 1, totalPages: 3, totalElements: 41 }));
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(1);
    expect(rows(host)[0]).toContain('beto@juguera.pe');
    expect(host.textContent).toContain('Página 2 de 3');
  });
});
