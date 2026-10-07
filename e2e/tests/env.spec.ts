import { expect, test } from '@playwright/test';
import { assertSafeTarget } from '../support/target';

const local = { E2E_BASE_URL: 'http://localhost:4200', E2E_API_URL: 'http://127.0.0.1:8080' };

test.describe('target guard', () => {
  test('accepts local hosts', () => {
    expect(() => assertSafeTarget(local, false)).not.toThrow();
    expect(() =>
      assertSafeTarget({ E2E_BASE_URL: 'http://[::1]:4200', E2E_API_URL: 'http://app.localhost:8080' }, false),
    ).not.toThrow();
  });

  test('refuses a remote host unless explicitly allowed', () => {
    const remote = { ...local, E2E_API_URL: 'https://api.test.jugueria.jhanantezana.com' };
    expect(() => assertSafeTarget(remote, false)).toThrow(/E2E_ALLOW_REMOTE/);
    expect(() => assertSafeTarget(remote, true)).not.toThrow();
  });

  test('refuses production hosts even when remote is allowed', () => {
    for (const host of ['jugueria.jhanantezana.com', 'api.jugueria.jhanantezana.com', 'my-prod-alb.example.com']) {
      expect(() => assertSafeTarget({ ...local, E2E_BASE_URL: `https://${host}` }, true)).toThrow(/production/);
    }
  });

  test('refuses a host that only starts like localhost', () => {
    expect(() => assertSafeTarget({ ...local, E2E_API_URL: 'http://localhost.evil.com' }, false)).toThrow(
      /non-local/,
    );
  });

  test('refuses an invalid URL', () => {
    expect(() => assertSafeTarget({ ...local, E2E_API_URL: 'not a url' }, false)).toThrow(/valid URL/);
  });
});
