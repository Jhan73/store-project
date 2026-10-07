import { assertSafeTarget } from './target';

function required(name: string): string {
  const value = process.env[name];
  if (!value) {
    throw new Error(
      `Missing environment variable ${name}. Export E2E_ADMIN_EMAIL and E2E_ADMIN_PASSWORD in the shell session; see e2e/CLAUDE.md.`,
    );
  }
  return value;
}

function withoutTrailingSlash(url: string): string {
  return url.replace(/\/+$/, '');
}

export const baseUrl = withoutTrailingSlash(process.env['E2E_BASE_URL'] || 'http://localhost:4200');
export const apiUrl = withoutTrailingSlash(process.env['E2E_API_URL'] || 'http://localhost:8080');

assertSafeTarget({ E2E_BASE_URL: baseUrl, E2E_API_URL: apiUrl }, process.env['E2E_ALLOW_REMOTE'] === '1');

export function adminCredentials(): { email: string; password: string } {
  return { email: required('E2E_ADMIN_EMAIL'), password: required('E2E_ADMIN_PASSWORD') };
}
