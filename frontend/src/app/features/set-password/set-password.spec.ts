import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Location } from '@angular/common';
import { Component } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';
import { RouterTestingHarness } from '@angular/router/testing';
import { API_ORIGIN } from '../../core/api/api-config';
import { authInterceptor } from '../../core/auth/auth-interceptor';
import { errorInterceptor } from '../../core/errors/error-interceptor';
import { SetPassword } from './set-password';

const SET_PASSWORD = 'http://api.test/api/v1/auth/set-password';
const TOKEN = 'dGVzdC10b2tlbl92YWx1ZV8xMjM0NTY3ODkwYWJjZGVm';
const GOOD = 'Str0ng!pass';

@Component({ template: '' })
class LoginStub {}

async function render(url = `/set-password?token=${TOKEN}`) {
  TestBed.configureTestingModule({
    providers: [
      provideRouter([
        { path: 'set-password', component: SetPassword },
        { path: 'login', component: LoginStub },
      ]),
      provideHttpClient(withInterceptors([errorInterceptor, authInterceptor])),
      provideHttpClientTesting(),
      { provide: API_ORIGIN, useValue: 'http://api.test' },
    ],
  });
  const router = TestBed.inject(Router);
  const navigate = vi.spyOn(router, 'navigate');
  const harness = await RouterTestingHarness.create();
  await harness.navigateByUrl(url, SetPassword);
  await harness.fixture.whenStable();
  const host = harness.routeNativeElement as HTMLElement;
  return { harness, host, router, navigate, http: TestBed.inject(HttpTestingController) };
}

// A settled promise chain after a response needs one macrotask before change detection is stable.
async function settle(harness: RouterTestingHarness) {
  await new Promise((resolve) => setTimeout(resolve));
  await harness.fixture.whenStable();
}

function type(host: HTMLElement, selector: string, value: string) {
  const input = host.querySelector<HTMLInputElement>(selector)!;
  input.value = value;
  input.dispatchEvent(new Event('input'));
}

async function submit(
  harness: RouterTestingHarness,
  host: HTMLElement,
  password = GOOD,
  confirmation = password,
) {
  type(host, '#new-password', password);
  type(host, '#confirm-password', confirmation);
  host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
  await settle(harness);
}

function problem(status: number, code: string, extra: object = {}) {
  return {
    body: { type: 'about:blank', status, code, correlationId: 'corr-1', ...extra },
    init: { status, statusText: 'x' },
  };
}

describe('SetPassword', () => {
  afterEach(() => TestBed.inject(HttpTestingController).verify());

  describe('token handling', () => {
    it('removes the token from the address bar by replacing the history entry', async () => {
      const { navigate, router } = await render();

      expect(TestBed.inject(Location).path()).toBe('/set-password');
      expect(navigate).toHaveBeenCalledWith(
        [],
        expect.objectContaining({ replaceUrl: true, queryParams: { token: null } }),
      );
      expect(router.url).toBe('/set-password');
    });

    it('still sends the token it read once the URL is clean', async () => {
      const { harness, host, http } = await render();

      await submit(harness, host);

      const request = http.expectOne(SET_PASSWORD);
      expect(request.request.body).toEqual({ token: TOKEN, newPassword: GOOD });
      expect(request.request.url).not.toContain(TOKEN);
      request.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('asks to reopen the emailed link, without admin advice, when the address has no token', async () => {
      const { host, navigate } = await render('/set-password');

      expect(host.querySelector('form')).toBeNull();
      expect(host.querySelector('h1')?.textContent).toContain('No encontramos tu enlace');
      expect(host.textContent).toContain('Abre de nuevo el enlace');
      expect(host.textContent).not.toContain('administrador');
      expect(navigate).not.toHaveBeenCalled();
    });

    it('treats a malformed token as an invalid link and still cleans the URL', async () => {
      const { host, router } = await render('/set-password?token=not%20a%20token%21');

      expect(host.querySelector('form')).toBeNull();
      expect(host.querySelector('h1')?.textContent).toContain('Enlace no válido');
      expect(host.textContent).toContain('administrador');
      expect(router.url).toBe('/set-password');
    });

    it('treats an empty token as an invalid link', async () => {
      const { host } = await render('/set-password?token=');

      expect(host.querySelector('form')).toBeNull();
    });

    it('offers a way to reach the sign-in page from the missing and invalid states', async () => {
      const { host } = await render('/set-password');

      expect(host.querySelector('a[href="/login"]')).not.toBeNull();
    });
  });

  describe('form', () => {
    it('has labelled password fields that do not autofill old passwords', async () => {
      const { host } = await render();

      expect(host.querySelector('h1')?.textContent).toContain('Crea tu contraseña');
      expect(host.querySelector('label[for="new-password"]')).not.toBeNull();
      expect(host.querySelector('label[for="confirm-password"]')).not.toBeNull();
      for (const id of ['#new-password', '#confirm-password']) {
        const input = host.querySelector<HTMLInputElement>(id)!;
        expect(input.type).toBe('password');
        expect(input.classList.contains('p-password')).toBe(true);
        expect(input.autocomplete).toBe('new-password');
      }
      expect(host.querySelector('button[type="submit"]')).not.toBeNull();
    });

    it('describes the policy and ties it to the field', async () => {
      const { host } = await render();

      const hint = host.querySelector('#new-password-hint');
      expect(hint?.textContent).toContain('8');
      expect(hint?.textContent).toContain('50');
      expect(host.querySelector('#new-password')?.getAttribute('aria-describedby')).toContain(
        'new-password-hint',
      );
    });

    it('toggles both fields between hidden and visible with a pressed state', async () => {
      const { harness, host } = await render();
      const toggle = host.querySelector<HTMLButtonElement>('button[aria-pressed]')!;

      expect(toggle.getAttribute('aria-pressed')).toBe('false');
      expect(toggle.getAttribute('aria-label')).toContain('contraseñas');
      toggle.click();
      await settle(harness);

      expect(toggle.getAttribute('aria-pressed')).toBe('true');
      expect(host.querySelector<HTMLInputElement>('#new-password')!.type).toBe('text');
      expect(host.querySelector<HTMLInputElement>('#confirm-password')!.type).toBe('text');
    });

    it('does not call the API with empty fields and focuses the first invalid one', async () => {
      const { harness, host, http } = await render();

      await submit(harness, host, '', '');

      http.expectNone(SET_PASSWORD);
      expect(host.querySelector('#new-password')?.getAttribute('aria-invalid')).toBe('true');
      expect(host.querySelector('#new-password-errors')?.textContent).toContain('Escribe');
      expect(document.activeElement?.id).toBe('new-password');
    });

    it.each([
      ['Abcde1!', 'entre 8 y 50'],
      ['Aa1!' + 'x'.repeat(47), 'entre 8 y 50'],
      ['ABCDEF1!', 'minúscula'],
      ['abcdef1!', 'mayúscula'],
      ['Abcdefg!', 'número'],
      ['Abcdefg1', 'carácter especial'],
      ['Aa1!' + 'ñ'.repeat(35), '72 bytes'],
      ['Abcdef1!\u2028', 'saltos de línea'],
    ])('rejects %s with a message about the broken rule', async (password, message) => {
      const { harness, host, http } = await render();

      await submit(harness, host, password);

      http.expectNone(SET_PASSWORD);
      expect(host.querySelector('#new-password-errors')?.textContent).toContain(message);
    });

    it.each(['Abcdef1!', 'Aa1!' + 'x'.repeat(46), 'Abcdef1 ', 'Abcdef1ñ'])(
      'accepts %s at the boundaries of the policy',
      async (password) => {
        const { harness, host, http } = await render();

        await submit(harness, host, password);

        http.expectOne(SET_PASSWORD).flush(null, { status: 204, statusText: 'No Content' });
      },
    );

    it('rejects a confirmation that differs and focuses it', async () => {
      const { harness, host, http } = await render();

      await submit(harness, host, GOOD, GOOD + 'x');

      http.expectNone(SET_PASSWORD);
      expect(host.querySelector('#confirm-password-errors')?.textContent).toContain(
        'no coinciden',
      );
      expect(host.querySelector('#confirm-password')?.getAttribute('aria-invalid')).toBe('true');
      expect(document.activeElement?.id).toBe('confirm-password');
    });
  });

  describe('submit', () => {
    it('sends one request when submitted twice before the answer', async () => {
      const { harness, host, http } = await render();

      type(host, '#new-password', GOOD);
      type(host, '#confirm-password', GOOD);
      const form = host.querySelector<HTMLFormElement>('form')!;
      form.dispatchEvent(new Event('submit'));
      form.dispatchEvent(new Event('submit'));
      await settle(harness);

      http.expectOne(SET_PASSWORD).flush(null, { status: 204, statusText: 'No Content' });
    });

    it('confirms the change, drops the form and links to sign-in on success', async () => {
      const { harness, host, http } = await render();

      await submit(harness, host);
      http.expectOne(SET_PASSWORD).flush(null, { status: 204, statusText: 'No Content' });
      await settle(harness);

      expect(host.querySelector('h1')?.textContent).toContain('Contraseña creada');
      expect(host.querySelector('form')).toBeNull();
      expect(host.querySelector('input')).toBeNull();
      expect(host.querySelector('a[href="/login"]')).not.toBeNull();
      expect(document.activeElement?.tagName).toBe('H1');
    });

    it('goes to the sign-in page from the confirmation', async () => {
      const { harness, host, http, router } = await render();
      await submit(harness, host);
      http.expectOne(SET_PASSWORD).flush(null, { status: 204, statusText: 'No Content' });
      await settle(harness);

      host.querySelector<HTMLAnchorElement>('a[href="/login"]')!.click();
      await settle(harness);

      expect(router.url).toBe('/login');
    });

    it('never writes the password to web storage', async () => {
      const { harness, host, http } = await render();

      await submit(harness, host);
      http.expectOne(SET_PASSWORD).flush(null, { status: 204, statusText: 'No Content' });

      for (const storage of [localStorage, sessionStorage]) {
        const content = JSON.stringify({ ...storage });
        expect(content).not.toContain(GOOD);
        expect(content).not.toContain(TOKEN);
      }
    });
  });

  describe('backend errors', () => {
    it.each([
      ['auth.invalid-set-password-token', 401],
    ])('replaces the form with the invalid-link state on %s', async (code, status) => {
      const { harness, host, http, router } = await render();
      await submit(harness, host);

      const { body, init } = problem(status, code);
      http.expectOne(SET_PASSWORD).flush(body, init);
      await settle(harness);

      expect(host.querySelector('form')).toBeNull();
      expect(host.querySelector('h1')?.textContent).toContain('Enlace no válido');
      expect(host.textContent).toContain('ya venció');
      expect(host.textContent).toContain('administrador');
      expect(router.url).toBe('/set-password');
      expect(document.activeElement?.tagName).toBe('H1');
    });

    it('maps a validation failure onto the password field and keeps the form', async () => {
      const { harness, host, http } = await render();
      await submit(harness, host);

      const { body, init } = problem(400, 'common.validation-failed', {
        errors: [{ field: 'newPassword', constraint: 'Pattern' }],
      });
      http.expectOne(SET_PASSWORD).flush(body, init);
      await settle(harness);

      expect(host.querySelector('form')).not.toBeNull();
      expect(host.querySelector('#new-password')?.getAttribute('aria-invalid')).toBe('true');
      expect(host.querySelector('#new-password-errors')?.textContent).toContain('requisitos');
    });

    it('shows the validation message when the failing field has no control', async () => {
      const { harness, host, http } = await render();
      await submit(harness, host);

      const { body, init } = problem(400, 'common.validation-failed', {
        errors: [{ field: 'token', constraint: 'NotBlank' }],
      });
      http.expectOne(SET_PASSWORD).flush(body, init);
      await settle(harness);

      expect(host.querySelector('form')).not.toBeNull();
      expect(host.querySelector('[role="alert"]')?.textContent).toContain('Revisa los campos');
    });

    it('keeps the form and the typed values on an outage, with a retry-friendly message', async () => {
      const { harness, host, http } = await render();
      await submit(harness, host);

      const { body, init } = problem(503, 'common.service-unavailable');
      http.expectOne(SET_PASSWORD).flush(body, init);
      await settle(harness);

      expect(host.querySelector('[role="alert"]')?.textContent).toContain('Inténtalo');
      expect(host.querySelector('[role="alert"]')?.textContent).toContain('corr-1');
      expect(host.querySelector<HTMLInputElement>('#new-password')?.value).toBe(GOOD);
      expect(host.querySelector<HTMLButtonElement>('button[type="submit"]')?.disabled).toBe(false);

      host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
      await settle(harness);
      const retry = http.expectOne(SET_PASSWORD);
      expect(retry.request.body).toEqual({ token: TOKEN, newPassword: GOOD });
      retry.flush(null, { status: 204, statusText: 'No Content' });
    });

    it('keeps the form on a network failure', async () => {
      const { harness, host, http } = await render();
      await submit(harness, host);

      http.expectOne(SET_PASSWORD).error(new ProgressEvent('error'));
      await settle(harness);

      expect(host.querySelector('[role="alert"]')?.textContent).toContain('conexión');
      expect(host.querySelector('form')).not.toBeNull();
    });

    it('clears the previous failure when the user submits again', async () => {
      const { harness, host, http } = await render();
      await submit(harness, host);
      const { body, init } = problem(503, 'common.service-unavailable');
      http.expectOne(SET_PASSWORD).flush(body, init);
      await settle(harness);

      host.querySelector<HTMLFormElement>('form')!.dispatchEvent(new Event('submit'));
      await settle(harness);

      expect(host.querySelector('[role="alert"]')?.textContent?.trim()).toBe('');
      http.expectOne(SET_PASSWORD).flush(null, { status: 204, statusText: 'No Content' });
    });
  });
});
