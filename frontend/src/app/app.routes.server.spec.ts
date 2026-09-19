import { RenderMode } from '@angular/ssr';
import { serverRoutes } from './app.routes.server';

describe('serverRoutes', () => {
  it('prerenders the landing page', () => {
    expect(serverRoutes.find((route) => route.path === '')?.renderMode).toBe(RenderMode.Prerender);
  });

  it('renders every other route on the client', () => {
    expect(serverRoutes.find((route) => route.path === '**')?.renderMode).toBe(RenderMode.Client);
  });
});
