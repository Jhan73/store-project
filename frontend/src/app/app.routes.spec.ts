import { routes } from './app.routes';
import { Landing } from './features/landing/landing';

describe('routes', () => {
  it('lazy-loads the landing page at the root', async () => {
    const root = routes.find((route) => route.path === '');

    expect(await root?.loadComponent?.()).toBe(Landing);
  });
});
