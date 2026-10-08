import { routes } from './app.routes';
import { Landing } from './features/landing/landing';
import { SetPassword } from './features/set-password/set-password';

describe('routes', () => {
  it('lazy-loads the landing page at the root', async () => {
    const root = routes.find((route) => route.path === '');

    expect(await root?.loadComponent?.()).toBe(Landing);
  });

  it('exposes the set-password page publicly, with a localized title', async () => {
    const route = routes.find((candidate) => candidate.path === 'set-password');

    expect(route?.canMatch).toBeUndefined();
    expect(route?.canActivate).toBeUndefined();
    expect(route?.title).toBe('Crear contraseña');
    expect(await route?.loadComponent?.()).toBe(SetPassword);
  });
});
