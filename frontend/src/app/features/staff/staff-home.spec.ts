import { signal, type WritableSignal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import type { Role } from '../../core/api/api-types';
import { AuthStore } from '../../core/auth/auth-store';
import { StaffHome } from './staff-home';

describe('StaffHome', () => {
  let role: WritableSignal<Role | null>;

  beforeEach(() => {
    role = signal<Role | null>('SERVER');
    TestBed.configureTestingModule({
      providers: [provideRouter([]), { provide: AuthStore, useValue: { role } }],
    });
  });

  async function links() {
    const fixture = TestBed.createComponent(StaffHome);
    await fixture.whenStable();
    return Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('a')).map((link) =>
      link.getAttribute('href'),
    );
  }

  it('offers a server the floor shortcuts only', async () => {
    expect(await links()).toEqual(['/staff/tables', '/staff/board', '/staff/availability']);
  });

  it('adds the register and the quick sale for a cashier', async () => {
    role.set('CASHIER');

    expect(await links()).toEqual([
      '/staff/tables',
      '/staff/board',
      '/staff/register',
      '/staff/quick-sale',
      '/staff/availability',
    ]);
  });

  it('adds the open shifts for an administrator', async () => {
    role.set('ADMIN');

    expect(await links()).toContain('/staff/register/shifts');
  });
});
