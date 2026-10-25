import { Component, signal, type Type } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Route, Router, RouterOutlet, Routes } from '@angular/router';
import type { Role } from '../../core/api/api-types';
import { AuthStore } from '../../core/auth/auth-store';
import { routes } from '../../app.routes';
import { STAFF_ROUTES } from './staff.routes';

@Component({ selector: 'app-stub-page', template: '' })
class StubPage {}

@Component({ selector: 'app-stub-layout', imports: [RouterOutlet], template: '<router-outlet />' })
class StubLayout {}

// Keeps every guard, provider and path of the real tables; only the screens are replaced.
function stubbed(table: Routes): Routes {
  return table.map((route): Route => {
    const copy: Route = { ...route };
    if (route.loadComponent) {
      const stub: Type<unknown> = route.children ? StubLayout : StubPage;
      copy.loadComponent = () => Promise.resolve(stub);
    }
    if (route.children) {
      copy.children = stubbed(route.children);
    }
    if (route.loadChildren) {
      copy.loadChildren =
        route.path === 'staff'
          ? () => Promise.resolve(stubbed(STAFF_ROUTES))
          : () => Promise.resolve([{ path: '**', component: StubPage }]);
    }
    return copy;
  });
}

describe('route access by role', () => {
  let router: Router;

  async function signIn(role: Role) {
    TestBed.configureTestingModule({
      providers: [
        provideRouter(stubbed(routes)),
        {
          provide: AuthStore,
          useValue: {
            restore: () => Promise.resolve(),
            role: signal<Role | null>(role),
            refreshError: signal(null),
          },
        },
      ],
    });
    router = TestBed.inject(Router);
  }

  async function visit(url: string): Promise<string> {
    await router.navigateByUrl(url);
    return router.url;
  }

  describe('a kitchen account', () => {
    beforeEach(() => signIn('KITCHEN'));

    it.each([
      '/staff/tables',
      '/staff/tickets/some-ticket',
      '/staff/availability',
      '/staff/quick-sale',
      '/staff/register',
      '/staff/register/shifts',
      '/display',
      '/admin',
    ])('is forbidden from %s', async (url) => {
      expect(await visit(url)).toBe('/forbidden');
    });

    it('reaches the board', async () => {
      expect(await visit('/staff/board')).toBe('/staff/board');
    });

    it('is sent from the empty staff page to its home, the board', async () => {
      expect(await visit('/staff')).toBe('/staff/board');
    });
  });

  describe('the floor and the admin', () => {
    it.each<Role>(['SERVER', 'CASHIER', 'ADMIN'])('%s keeps the staff home', async (role) => {
      await signIn(role);

      expect(await visit('/staff')).toBe('/staff');
    });
  });
});
