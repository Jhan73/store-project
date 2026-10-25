import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { MessageService } from 'primeng/api';
import { API_ORIGIN } from '../../../core/api/api-config';
import type { StaffMember, StaffPage } from '../../../core/api/api-types';
import { AuthStore } from '../../../core/auth/auth-store';
import { errorInterceptor } from '../../../core/errors/error-interceptor';
import { ConfirmAction } from '../../../shared/state/confirm-action';
import { UndoNotice } from '../../../shared/state/undo-notice';
import {
  chooseOption,
  filterOptions,
  optionLabels,
  selectedLabel,
  selectIsDisabled,
} from '../../../testing/primeng-controls';
import { StaffApi } from './staff-api';
import { StaffList } from './staff-list';

const API = 'http://api.test/api/v1';
const LIST = `${API}/staff?page=0&size=100`;

const ana: StaffMember = {
  id: 'u1',
  username: 'Ana',
  email: 'ana@juguera.pe',
  role: 'SERVER',
  active: true,
  createdAt: '2026-01-01T03:30:00Z',
};
const beto: StaffMember = {
  ...ana,
  id: 'u2',
  username: null,
  email: 'beto@juguera.pe',
  role: 'CASHIER',
  active: false,
};
const me: StaffMember = { ...ana, id: 'me', username: 'Yo', email: 'yo@juguera.pe', role: 'ADMIN' };

function page(content: StaffMember[], overrides: Partial<StaffPage> = {}): StaffPage {
  return { content, page: 0, size: 100, totalElements: content.length, totalPages: 1, ...overrides };
}

const ask = vi.fn(() => Promise.resolve(true));
const adminAuth = {
  session: signal({ userId: 'me', role: 'ADMIN' }),
  onSignOut: () => () => undefined,
  ensureFresh: () => Promise.resolve(),
};

async function render(first: StaffPage = page([ana, beto, me])) {
  TestBed.configureTestingModule({
    providers: [
      provideHttpClient(withInterceptors([errorInterceptor])),
      provideHttpClientTesting(),
      MessageService,
      StaffApi,
      { provide: ConfirmAction, useValue: { ask } },
      { provide: AuthStore, useValue: adminAuth },
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

const body = () => document.body;

function type(selector: string, value: string) {
  const input = body().querySelector<HTMLInputElement>(selector)!;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

function submit() {
  body().querySelector<HTMLFormElement>('[role="dialog"] form')!.dispatchEvent(new Event('submit'));
}

async function openInvite(fixture: { whenStable(): Promise<unknown> }, host: HTMLElement) {
  host.querySelector<HTMLButtonElement>('[data-testid="invite-staff"]')!.click();
  await fixture.whenStable();
}

function button(host: HTMLElement, testId: string) {
  return host.querySelector<HTMLButtonElement>(`[data-testid="${testId}"]`);
}

function rows(host: HTMLElement) {
  return Array.from(host.querySelectorAll('tbody tr')).map((row) => row.textContent ?? '');
}

// The role change waits out the undo window; flushing sends it now.
function sendPending(fixture: { componentInstance: StaffList }) {
  (fixture.componentInstance as unknown as { changes: { flush(): void } }).changes.flush();
}

function problem(status: number, code: string) {
  return [
    { type: 'about:blank', status, code, correlationId: 'c' },
    { status, statusText: 'Error' },
  ] as const;
}

describe('StaffList', () => {
  beforeEach(() => ask.mockReset().mockResolvedValue(true));
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  it('lists the accounts with their role, state and creation time in the store time zone', async () => {
    const { host } = await render();

    const [first, second] = rows(host);
    expect(first).toContain('Ana');
    expect(first).toContain('ana@juguera.pe');
    expect(first).toContain('Mozo');
    expect(first).toContain('Activa');
    expect(first).toContain('31/12');
    expect(second).toContain('beto@juguera.pe');
    expect(second).toContain('Inactiva');
  });

  it('marks an account that has no username yet instead of leaving a blank', async () => {
    const { host } = await render();

    expect(rows(host)[1]).toContain('Sin nombre');
    expect(rows(host)[0]).not.toContain('Sin nombre');
  });

  it('reads every page of accounts', async () => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([errorInterceptor])),
        provideHttpClientTesting(),
        MessageService,
        StaffApi,
        { provide: ConfirmAction, useValue: { ask } },
        { provide: AuthStore, useValue: adminAuth },
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    });
    const fixture = TestBed.createComponent(StaffList);
    const http = TestBed.inject(HttpTestingController);
    await fixture.whenStable();
    http.expectOne(LIST).flush(page([ana], { totalPages: 2, totalElements: 2 }));
    http.expectOne(`${API}/admin/settings`).flush({ timeZone: 'America/Lima', currency: 'PEN' });
    await fixture.whenStable();
    http.expectOne(`${API}/staff?page=1&size=100`).flush(page([beto], { page: 1, totalPages: 2, totalElements: 2 }));
    await fixture.whenStable();

    expect(rows(fixture.nativeElement as HTMLElement)).toHaveLength(2);
  });

  it('invites an account from a dialog with a username, an email and a role and never asks for a password', async () => {
    const { fixture, host, http } = await render();

    await openInvite(fixture, host);
    expect(body().querySelector('input[type="password"]')).toBeNull();
    type('#staff-username', ' Nueva ');
    type('#staff-email', ' nueva@juguera.pe ');
    await chooseOption(fixture, body(), 'staff-role', 'Cajero');
    submit();
    const request = http.expectOne(`${API}/staff`);
    expect(request.request.method).toBe('POST');
    expect(request.request.body).toEqual({
      username: 'Nueva',
      email: 'nueva@juguera.pe',
      role: 'CASHIER',
    });
    request.flush({ ...ana, id: 'u9', username: 'Nueva', email: 'nueva@juguera.pe', role: 'CASHIER' }, {
      status: 201,
      statusText: 'Created',
    });
    await fixture.whenStable();
    http.expectOne(LIST).flush(page([ana, beto, me, { ...ana, id: 'u9', email: 'nueva@juguera.pe' }]));
    await fixture.whenStable();

    expect(host.querySelector('[role="status"]')?.textContent).toContain('nueva@juguera.pe');
    expect(rows(host)).toHaveLength(4);
    expect(body().querySelector('#staff-email')).toBeNull();
  });

  it('asks for the username before the email and the role', async () => {
    const { fixture, host } = await render();

    await openInvite(fixture, host);
    const ids = Array.from(body().querySelectorAll('[role="dialog"] input')).map((input) => input.id);
    expect(ids.indexOf('staff-username')).toBeLessThan(ids.indexOf('staff-email'));
  });

  it('does not invite without a username', async () => {
    const { fixture, host, http } = await render();

    await openInvite(fixture, host);
    type('#staff-username', '   ');
    type('#staff-email', 'nueva@juguera.pe');
    submit();
    await fixture.whenStable();

    http.expectNone(`${API}/staff`);
    expect(body().querySelector('#staff-username')!.getAttribute('aria-invalid')).toBe('true');
  });

  it('does not invite an address that is not an email', async () => {
    const { fixture, host, http } = await render();

    await openInvite(fixture, host);
    type('#staff-username', 'Nueva');
    type('#staff-email', 'not-an-email');
    submit();
    await fixture.whenStable();

    http.expectNone(`${API}/staff`);
    expect(body().querySelector('#staff-email')!.getAttribute('aria-invalid')).toBe('true');
  });

  it('offers only staff roles when inviting', async () => {
    const { fixture, host } = await render();

    await openInvite(fixture, host);
    expect(await optionLabels(fixture, body(), 'staff-role')).toEqual([
      'Mozo',
      'Cajero',
      'Administrador',
    ]);
  });

  it('searches the roles and invites with the one that is picked', async () => {
    const { fixture, host, http } = await render();

    await openInvite(fixture, host);
    expect(await filterOptions(fixture, body(), 'staff-role', 'caj')).toEqual(['Cajero']);
    await chooseOption(fixture, body(), 'staff-role', 'Cajero');
    type('#staff-username', 'Nueva');
    type('#staff-email', 'nueva@juguera.pe');
    submit();

    expect(http.expectOne(`${API}/staff`).request.body).toEqual({
      username: 'Nueva',
      email: 'nueva@juguera.pe',
      role: 'CASHIER',
    });
  });

  it('searches the role of an existing account', async () => {
    const { fixture, host } = await render();

    expect(await filterOptions(fixture, host, 'role-u1', 'admin')).toEqual(['Administrador']);
  });

  it('keeps the typed email and tells the user when it is already registered', async () => {
    const { fixture, host, http, toast } = await render();

    await openInvite(fixture, host);
    type('#staff-username', 'Otra');
    type('#staff-email', 'ana@juguera.pe');
    submit();
    http.expectOne(`${API}/staff`).flush(...problem(409, 'identity.email-already-registered'));
    await fixture.whenStable();

    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
    expect(body().querySelector<HTMLInputElement>('#staff-email')!.value).toBe('ana@juguera.pe');
    expect(host.querySelector('[role="status"]')).toBeNull();
  });

  it('keeps the typed username and tells the user in Spanish when it is already taken', async () => {
    const { fixture, host, http, toast } = await render();

    await openInvite(fixture, host);
    type('#staff-username', 'Ana');
    type('#staff-email', 'otra@juguera.pe');
    submit();
    http.expectOne(`${API}/staff`).flush(...problem(409, 'identity.username-already-taken'));
    await fixture.whenStable();

    expect(toast).toHaveBeenCalledWith(
      expect.objectContaining({
        severity: 'error',
        summary: expect.stringContaining('nombre de usuario'),
      }),
    );
    expect(body().querySelector<HTMLInputElement>('#staff-username')!.value).toBe('Ana');
    expect(host.querySelector('[role="status"]')).toBeNull();
  });

  it('names an account from a dialog and shows the new name', async () => {
    const { fixture, host, http } = await render();

    button(host, 'rename-u2')!.click();
    await fixture.whenStable();
    expect(body().querySelector<HTMLInputElement>('#staff-rename')!.value).toBe('');
    type('#staff-rename', ' Beto ');
    submit();
    const request = http.expectOne(`${API}/staff/u2/username`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ username: 'Beto' });
    request.flush({ ...beto, username: 'Beto' });
    await fixture.whenStable();

    expect(rows(host)[1]).toContain('Beto');
    expect(rows(host)[1]).not.toContain('Sin nombre');
    expect(body().querySelector('#staff-rename')).toBeNull();
  });

  it('starts the rename dialog with the current username', async () => {
    const { fixture, host } = await render();

    button(host, 'rename-u1')!.click();
    await fixture.whenStable();

    expect(body().querySelector<HTMLInputElement>('#staff-rename')!.value).toBe('Ana');
  });

  it('does not rename to a blank username', async () => {
    const { fixture, host, http } = await render();

    button(host, 'rename-u2')!.click();
    await fixture.whenStable();
    type('#staff-rename', '   ');
    submit();
    await fixture.whenStable();

    http.expectNone(`${API}/staff/u2/username`);
    expect(body().querySelector('#staff-rename')!.getAttribute('aria-invalid')).toBe('true');
  });

  it('keeps the rename dialog open and tells the user when the username is taken', async () => {
    const { fixture, host, http, toast } = await render();

    button(host, 'rename-u2')!.click();
    await fixture.whenStable();
    type('#staff-rename', 'Ana');
    submit();
    http.expectOne(`${API}/staff/u2/username`).flush(...problem(409, 'identity.username-already-taken'));
    await fixture.whenStable();

    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
    expect(body().querySelector<HTMLInputElement>('#staff-rename')!.value).toBe('Ana');
    expect(rows(host)[1]).toContain('Sin nombre');
  });

  it('shows a changed role at once with an undo and sends it after the undo window', async () => {
    const { fixture, host, http } = await render();

    await chooseOption(fixture, host, 'role-u1', 'Cajero');
    await fixture.whenStable();
    expect(rows(host)[0]).toContain('Cajero');
    expect(selectedLabel(host, 'role-u1')).toBe('Cajero');
    expect(selectIsDisabled(host, 'role-u1')).toBe(true);
    sendPending(fixture);
    const request = http.expectOne(`${API}/staff/u1/role`);
    expect(request.request.method).toBe('PATCH');
    expect(request.request.body).toEqual({ role: 'CASHIER' });
    request.flush({ ...ana, role: 'CASHIER' });
    await fixture.whenStable();

    expect(rows(host)[0]).toContain('Cajero');
    expect(selectIsDisabled(host, 'role-u1')).toBe(false);
    expect(selectedLabel(host, 'role-u1')).toBe('Cajero');
  });

  it('undoing a role change sends nothing and shows the old role', async () => {
    const { fixture, host } = await render();
    const notice = TestBed.inject(UndoNotice);

    await chooseOption(fixture, host, 'role-u1', 'Administrador');
    notice.undo(notice.active()[0]);
    await fixture.whenStable();

    expect(selectedLabel(host, 'role-u1')).toBe('Mozo');
    expect(selectIsDisabled(host, 'role-u1')).toBe(false);
  });

  it('puts the role control back to what the server holds when the change is refused', async () => {
    const { fixture, host, http, toast } = await render();

    await chooseOption(fixture, host, 'role-u1', 'Administrador');
    sendPending(fixture);
    http.expectOne(`${API}/staff/u1/role`).flush(...problem(409, 'identity.last-active-admin-required'));
    await fixture.whenStable();

    expect(toast).toHaveBeenCalled();
    expect(selectedLabel(host, 'role-u1')).toBe('Mozo');
    expect(selectIsDisabled(host, 'role-u1')).toBe(false);
  });

  it('describes each account action with an icon and a tooltip that follows its state', async () => {
    const { fixture, host } = await render();

    for (const id of ['toggle-u1', 'toggle-u2', 'resend-u1']) {
      expect(button(host, id)!.querySelector('tabler-icon[aria-hidden="true"] svg')).not.toBeNull();
    }
    button(host, 'toggle-u1')!.dispatchEvent(new Event('mouseenter'));
    await fixture.whenStable();
    expect(document.body.querySelector('.p-tooltip')?.textContent).toContain(
      'Impedir que la cuenta inicie sesión',
    );
    button(host, 'toggle-u1')!.dispatchEvent(new Event('mouseleave'));
    button(host, 'toggle-u2')!.dispatchEvent(new Event('mouseenter'));
    await fixture.whenStable();
    expect(document.body.querySelector('.p-tooltip')?.textContent).toContain(
      'Permitir que la cuenta vuelva a iniciar sesión',
    );
  });

  it('asks for confirmation that says the sessions will be closed, then deactivates the account', async () => {
    const { fixture, host, http } = await render();

    button(host, 'toggle-u1')!.click();
    await fixture.whenStable();
    expect(ask).toHaveBeenCalledWith(
      expect.objectContaining({ message: expect.stringContaining('sesiones abiertas se cerrarán') }),
    );
    expect(button(host, 'toggle-u1')!.disabled).toBe(true);
    http.expectOne(`${API}/staff/u1/deactivate`).flush({ ...ana, active: false });
    await fixture.whenStable();

    expect(rows(host)[0]).toContain('Inactiva');
    expect(button(host, 'toggle-u1')!.disabled).toBe(false);
  });

  it('does nothing when the deactivation is not confirmed', async () => {
    ask.mockResolvedValue(false);
    const { fixture, host } = await render();

    button(host, 'toggle-u1')!.click();
    await fixture.whenStable();

    expect(rows(host)[0]).toContain('Activa');
    expect(rows(host)[0]).not.toContain('Inactiva');
  });

  it('reactivates an account straight away without asking', async () => {
    const { fixture, host, http } = await render();

    button(host, 'toggle-u2')!.click();
    http.expectOne(`${API}/staff/u2/reactivate`).flush({ ...beto, active: true });
    await fixture.whenStable();

    expect(ask).not.toHaveBeenCalled();
    expect(rows(host)[1]).not.toContain('Inactiva');
  });

  it('keeps the state and tells the user when the last administrator cannot be deactivated', async () => {
    const { fixture, host, http, toast } = await render();

    button(host, 'toggle-u1')!.click();
    await fixture.whenStable();
    http.expectOne(`${API}/staff/u1/deactivate`).flush(...problem(409, 'identity.last-active-admin-required'));
    await fixture.whenStable();

    expect(toast).toHaveBeenCalled();
    expect(rows(host)[0]).not.toContain('Inactiva');
    expect(button(host, 'toggle-u1')!.disabled).toBe(false);
  });

  it('does not let the administrator act on their own account', async () => {
    const { host } = await render();

    expect(button(host, 'toggle-me')!.disabled).toBe(true);
    expect(selectIsDisabled(host, 'role-me')).toBe(true);
    expect(button(host, 'resend-me')!.disabled).toBe(true);
  });

  it('lets the administrator name their own account', async () => {
    const { host } = await render();

    expect(button(host, 'rename-me')!.disabled).toBe(false);
  });

  it('keeps an account inactive when its username was taken while it was off', async () => {
    const { fixture, host, http, toast } = await render();

    button(host, 'toggle-u2')!.click();
    http.expectOne(`${API}/staff/u2/reactivate`).flush(...problem(409, 'identity.username-already-taken'));
    await fixture.whenStable();

    expect(toast).toHaveBeenCalledWith(expect.objectContaining({ severity: 'error' }));
    expect(rows(host)[1]).toContain('Inactiva');
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

  it('reloads the list when an account is gone', async () => {
    const { fixture, host, http } = await render();

    button(host, 'toggle-u1')!.click();
    await fixture.whenStable();
    http.expectOne(`${API}/staff/u1/deactivate`).flush(...problem(404, 'common.not-found'));
    await fixture.whenStable();
    http.expectOne(LIST).flush(page([beto, me]));
    await fixture.whenStable();

    expect(rows(host)).toHaveLength(2);
    expect(rows(host)[0]).toContain('beto@juguera.pe');
  });
});
