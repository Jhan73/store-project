import { defineConfig, devices } from '@playwright/test';
import { adminCredentials, baseUrl } from './support/env';

// Fail fast, before any browser starts, when the credentials are not provided.
adminCredentials();

// The failure snapshot of the login page would include the typed password.
process.env['PLAYWRIGHT_NO_COPY_PROMPT'] = '1';

export default defineConfig({
  testDir: './tests',
  workers: 1,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 1 : 0,
  // html, json, blob and junit persist typed values as step titles, so they are never used.
  reporter: process.env['CI'] ? [['github'], ['line']] : [['list']],
  timeout: 60_000,
  expect: { timeout: 10_000 },
  use: {
    baseURL: baseUrl,
    locale: 'es',
    // Traces and videos record typed values, so they stay off: sessions are opened by typing the admin password.
    trace: 'off',
    video: 'off',
    screenshot: 'off',
  },
  projects: [
    { name: 'unit', testMatch: /\.spec\.ts/ },
    { name: 'setup', testMatch: /auth\.setup\.ts/ },
    {
      name: 'chromium',
      testMatch: /\.journey\.ts/,
      dependencies: ['setup'],
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
