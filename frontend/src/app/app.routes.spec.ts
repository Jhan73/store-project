import { routes } from './app.routes';
import { Display } from './features/display/display';
import { DisplayApi } from './features/display/display-api';
import { Landing } from './features/landing/landing';
import { SetPassword } from './features/set-password/set-password';

describe('routes', () => {
  it('lazy-loads the landing page at the root', async () => {
    const root = routes.find((route) => route.path === '');

    expect(await root?.loadComponent?.()).toBe(Landing);
  });

  it('keeps the customer display behind a staff login, with its own API', async () => {
    const route = routes.find((candidate) => candidate.path === 'display');

    expect(route?.canMatch).toHaveLength(1);
    expect(route?.providers).toContain(DisplayApi);
    expect(await route?.loadComponent?.()).toBe(Display);
  });

  it('exposes the set-password page publicly, with a localized title', async () => {
    const route = routes.find((candidate) => candidate.path === 'set-password');

    expect(route?.canMatch).toBeUndefined();
    expect(route?.canActivate).toBeUndefined();
    expect(route?.title).toBe('Crear contraseña');
    expect(await route?.loadComponent?.()).toBe(SetPassword);
  });
});
