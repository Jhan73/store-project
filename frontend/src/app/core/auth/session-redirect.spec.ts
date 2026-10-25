import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { redirectToLogin } from './session-redirect';

@Component({ template: '' })
class Page {}

describe('redirectToLogin', () => {
  let router: Router;

  beforeEach(async () => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([
          { path: 'staff', component: Page },
          { path: 'login', component: Page },
          { path: 'display', component: Page, data: { keepOnSessionEnd: true } },
        ]),
      ],
    });
    router = TestBed.inject(Router);
  });

  it('sends the user to login and remembers where they were', async () => {
    await router.navigateByUrl('/staff');
    const navigate = vi.spyOn(router, 'navigate').mockResolvedValue(true);

    redirectToLogin(router);

    expect(navigate).toHaveBeenCalledWith(['/login'], { queryParams: { returnUrl: '/staff' } });
  });

  it('does not navigate again when already on the login page', async () => {
    await router.navigateByUrl('/login');
    const navigate = vi.spyOn(router, 'navigate');

    redirectToLogin(router);

    expect(navigate).not.toHaveBeenCalled();
  });

  it('leaves a screen that asked to stay when the session ends', async () => {
    await router.navigateByUrl('/display');
    const navigate = vi.spyOn(router, 'navigate');

    redirectToLogin(router);

    expect(navigate).not.toHaveBeenCalled();
    expect(router.url).toBe('/display');
  });
});
