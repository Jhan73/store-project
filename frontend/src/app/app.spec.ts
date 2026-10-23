import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { App } from './app';
import { API_ORIGIN } from './core/api/api-config';
import { AuthStore } from './core/auth/auth-store';

const LOGIN = 'http://api.test/api/v1/auth/login';
const REFRESH = 'http://api.test/api/v1/auth/refresh';

describe('App', () => {
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [App],
      providers: [
        provideRouter([{ path: 'login', children: [] }, { path: 'staff', children: [] }]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_ORIGIN, useValue: 'http://api.test' },
      ],
    }).compileComponents();
  });

  it('renders only the router outlet', async () => {
    const fixture = TestBed.createComponent(App);
    await fixture.whenStable();

    const host = fixture.nativeElement as HTMLElement;
    expect(host.querySelector('router-outlet')).not.toBeNull();
    expect(host.children.length).toBe(1);
  });

  it('sends the tab to login when another tab signed in as a different user', async () => {
    const http = TestBed.inject(HttpTestingController);
    const auth = TestBed.inject(AuthStore);
    const router = TestBed.inject(Router);
    const fixture = TestBed.createComponent(App);
    const login = auth.login({ email: 'a@b.pe', password: 'x' });
    http.expectOne(LOGIN).flush({ accessToken: 'a', tokenType: 'Bearer', userId: 'u1', role: 'CASHIER' });
    await login;
    await router.navigateByUrl('/staff');
    await fixture.whenStable();

    const refresh = auth.refresh();
    http.expectOne(REFRESH).flush({ accessToken: 'b', tokenType: 'Bearer', userId: 'u2', role: 'ADMIN' });
    await refresh;
    await fixture.whenStable();

    expect(router.url).toBe('/login');
    http.verify();
  });
});
