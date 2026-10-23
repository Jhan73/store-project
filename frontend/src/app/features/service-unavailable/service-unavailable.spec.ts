import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, convertToParamMap, provideRouter } from '@angular/router';
import { of } from 'rxjs';
import { API_ORIGIN } from '../../core/api/api-config';
import { ServiceUnavailable } from './service-unavailable';

const REFRESH = 'http://api.test/api/v1/auth/refresh';

describe('ServiceUnavailable', () => {
  let http: HttpTestingController;
  let navigated: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: API_ORIGIN, useValue: 'http://api.test' },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: { queryParamMap: convertToParamMap({ returnUrl: '/staff/tickets/k1' }) },
            queryParamMap: of(convertToParamMap({ returnUrl: '/staff/tickets/k1' })),
          },
        },
      ],
    });
    http = TestBed.inject(HttpTestingController);
    navigated = vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true);
  });

  afterEach(() => http.verify());

  async function render() {
    const fixture = TestBed.createComponent(ServiceUnavailable);
    await fixture.whenStable();
    const host = fixture.nativeElement as HTMLElement;
    return {
      fixture,
      host,
      retry: () => host.querySelector<HTMLButtonElement>('[data-testid="retry"]')!,
    };
  }

  it('explains the outage and offers a retry', async () => {
    const { host, retry } = await render();

    expect(host.textContent).toContain('no está disponible');
    expect(retry()).not.toBeNull();
    http.expectNone(REFRESH);
  });

  it('goes back to where the employee was once the service answers', async () => {
    const { fixture, retry } = await render();

    retry().click();
    http
      .expectOne(REFRESH)
      .flush({ accessToken: 'jwt', tokenType: 'Bearer', userId: 'u', role: 'CASHIER' });
    await fixture.whenStable();

    expect(navigated).toHaveBeenCalledWith('/staff/tickets/k1');
  });

  it('stays and says so while the service is still down', async () => {
    const { fixture, host, retry } = await render();

    retry().click();
    http.expectOne(REFRESH).flush(null, { status: 503, statusText: 'Service Unavailable' });
    await fixture.whenStable();

    expect(navigated).not.toHaveBeenCalled();
    expect(host.querySelector('[data-testid="still-down"]')).not.toBeNull();
    expect(retry().disabled).toBe(false);
  });

  it('goes to login when the session turns out to be over', async () => {
    const { fixture, retry } = await render();

    retry().click();
    http.expectOne(REFRESH).flush(null, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();

    expect(navigated).toHaveBeenCalledWith(expect.stringContaining('/login'));
  });
});
