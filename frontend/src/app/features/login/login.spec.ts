import { provideHttpClient, withInterceptors } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
  TestRequest,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, provideRouter } from '@angular/router';
import { API_ORIGIN } from '../../core/api/api-config';
import { authInterceptor } from '../../core/auth/auth-interceptor';
import { AuthStore } from '../../core/auth/auth-store';
import { errorInterceptor } from '../../core/errors/error-interceptor';
import { Login } from './login';

const LOGIN = 'http://api.test/api/v1/auth/login';
const REFRESH = 'http://api.test/api/v1/auth/refresh';

type RestoreAnswer = (request: TestRequest) => void;
const noSession: RestoreAnswer = (request) =>
  request.flush(null, { status: 401, statusText: 'Unauthorized' });

function setup(returnUrl: string | null = null) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([]),
      provideHttpClient(withInterceptors([errorInterceptor, authInterceptor])),
      provideHttpClientTesting(),
      { provide: API_ORIGIN, useValue: 'http://api.test' },
      {
        provide: ActivatedRoute,
        useValue: { snapshot: { queryParamMap: { get: () => returnUrl } } },
      },
    ],
  });
  return {
    http: TestBed.inject(HttpTestingController),
    navigateByUrl: vi.spyOn(TestBed.inject(Router), 'navigateByUrl').mockResolvedValue(true),
  };
}

describe('Login', () => {
  let http: HttpTestingController;
  let navigateByUrl: ReturnType<typeof vi.spyOn>;

  async function render(returnUrl: string | null = null, answer: RestoreAnswer = noSession) {
    ({ http, navigateByUrl } = setup(returnUrl));
    const fixture = TestBed.createComponent(Login);
    answer(http.expectOne(REFRESH));
    await fixture.whenStable();
    return { fixture, host: fixture.nativeElement as HTMLElement };
  }

  async function submit(
    host: HTMLElement,
    fixture: { whenStable(): Promise<unknown> },
    email = 'a@b.pe',
    password = 'secret',
  ) {
    const set = (selector: string, value: string) => {
      const input = host.querySelector<HTMLInputElement>(selector)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    };
    set('#email', email);
    set('#password', password);
    host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
    await fixture.whenStable();
  }

  function respondOk(role: 'ADMIN' | 'CASHIER' | 'SERVER') {
    http.expectOne(LOGIN).flush({ accessToken: 'jwt', tokenType: 'Bearer', userId: 'u', role });
  }

  afterEach(() => http.verify());

  it('has a labelled email and password and a submit button', async () => {
    const { host } = await render();

    expect(host.querySelector('h1')?.textContent).toContain('Iniciar sesión');
    expect(host.querySelector('label[for="email"]')).not.toBeNull();
    expect(host.querySelector('label[for="password"]')).not.toBeNull();
    expect(host.querySelector('button[type="submit"]')).not.toBeNull();
  });

  it('shows only the spinner, not the button icon, while signing in', async () => {
    const { fixture, host } = await render();
    const button = host.querySelector<HTMLButtonElement>('button[type="submit"]')!;
    expect(button.querySelectorAll('tabler-icon svg').length).toBe(1);

    await submit(host, fixture);

    expect(button.querySelectorAll('tabler-icon').length).toBe(0);
    expect(button.querySelectorAll('svg[data-p-icon="spinner"]').length).toBe(1);
    http.expectOne(LOGIN).flush(null, { status: 401, statusText: 'Unauthorized' });
    await fixture.whenStable();
  });

  it('keeps the label styled as a PrimeNG button label', async () => {
    const { host } = await render();

    expect(host.querySelector('button[type="submit"] span.p-button-label')?.textContent).toContain(
      'Entrar',
    );
  });

  it('does not call the API with empty fields', async () => {
    const { fixture, host } = await render();

    await submit(host, fixture, '', '');

    http.expectNone(LOGIN);
    expect(host.querySelector('#email')?.getAttribute('aria-invalid')).toBe('true');
  });

  it('sends staff to their area after signing in', async () => {
    const { fixture, host } = await render();

    await submit(host, fixture);
    respondOk('ADMIN');
    await vi.waitFor(() => expect(navigateByUrl).toHaveBeenCalledWith('/admin'));
    expect(TestBed.inject(AuthStore).role()).toBe('ADMIN');
  });

  it('goes back to where the user was heading', async () => {
    const { fixture, host } = await render('/staff/tickets');

    await submit(host, fixture);
    respondOk('CASHIER');
    await vi.waitFor(() => expect(navigateByUrl).toHaveBeenCalledWith('/staff/tickets'));
  });

  it('ignores a return URL that leaves the app', async () => {
    const { fixture, host } = await render('//evil.example.com');

    await submit(host, fixture);
    respondOk('SERVER');
    await vi.waitFor(() => expect(navigateByUrl).toHaveBeenCalledWith('/staff'));
  });

  it('shows the localized message when the credentials are wrong', async () => {
    const { fixture, host } = await render();

    await submit(host, fixture, 'a@b.pe', 'bad');
    http.expectOne(LOGIN).flush(
      { status: 401, code: 'auth.invalid-credentials', title: 'Unauthorized', detail: 'English detail' },
      { status: 401, statusText: 'Unauthorized' },
    );
    await vi.waitFor(() => expect(host.querySelector('[role="alert"]')?.textContent?.trim()).toBeTruthy());

    const alert = host.querySelector('[role="alert"]');
    expect(alert?.textContent).toContain('El correo o la contraseña no son correctos.');
    expect(alert?.textContent).not.toContain('English detail');
    expect(navigateByUrl).not.toHaveBeenCalled();
    expect(host.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(false);
  });

  it('shows the support code on server errors', async () => {
    const { fixture, host } = await render();

    await submit(host, fixture);
    http.expectOne(LOGIN).flush(
      { status: 500, code: 'common.internal-error', correlationId: 'abc-123' },
      { status: 500, statusText: 'Server Error' },
    );
    await vi.waitFor(() => expect(host.querySelector('[role="alert"]')?.textContent?.trim()).toBeTruthy());

    expect(host.querySelector('[role="alert"]')?.textContent).toContain('abc-123');
  });

  it('marks the fields the server rejected', async () => {
    const { fixture, host } = await render();

    await submit(host, fixture);
    http.expectOne(LOGIN).flush(
      { status: 400, code: 'common.validation-failed', errors: [{ field: 'email', constraint: 'Email' }] },
      { status: 400, statusText: 'Bad Request' },
    );
    await vi.waitFor(() => expect(host.querySelector('[role="alert"]')?.textContent?.trim()).toBeTruthy());

    expect(host.querySelector('#email')?.getAttribute('aria-invalid')).toBe('true');
    expect(host.querySelector('[role="alert"]')?.textContent).toContain('Revisa los campos marcados.');
  });

  it('sends a visitor with a valid session cookie home without asking for credentials', async () => {
    await render(null, (request) =>
      request.flush({ accessToken: 'jwt', tokenType: 'Bearer', userId: 'u', role: 'CASHIER' }),
    );

    await vi.waitFor(() => expect(navigateByUrl).toHaveBeenCalledWith('/staff'));
  });

  it('shows no error when there is simply no session to restore', async () => {
    const { host } = await render();

    expect(host.querySelector('[role="alert"]')?.textContent?.trim()).toBe('');
    expect(navigateByUrl).not.toHaveBeenCalled();
  });

  it('tells the user when the service is unavailable, and still lets them sign in', async () => {
    const { fixture, host } = await render(null, (request) =>
      request.flush(
        { status: 503, code: 'common.service-unavailable' },
        { status: 503, statusText: 'Service Unavailable' },
      ),
    );

    await vi.waitFor(() =>
      expect(host.querySelector('[role="alert"]')?.textContent).toContain(
        'El servicio no está disponible por ahora.',
      ),
    );
    await submit(host, fixture);
    respondOk('SERVER');
    await vi.waitFor(() => expect(navigateByUrl).toHaveBeenCalledWith('/staff'));
  });

  it('leaves a signed-in user at their home instead of showing the form', async () => {
    ({ http, navigateByUrl } = setup());
    const signIn = TestBed.inject(AuthStore).login({ email: 'a@b.pe', password: 'x' });
    respondOk('ADMIN');
    await signIn;

    TestBed.createComponent(Login);

    await vi.waitFor(() => expect(navigateByUrl).toHaveBeenCalledWith('/admin'));
  });
});
