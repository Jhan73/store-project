import { defineConfig, devices } from '@playwright/test';
import { adminCredentials, baseUrl } from './support/env';

// Fail fast, before any browser starts, when the credentials are not provided.
adminCredentials();

// The failure snapshot of the login page would include the typed password.
process.env['PLAYWRIGHT_NO_COPY_PROMPT'] = '1';

export default defineConfig({
  testDir: './tests',
  fullyParallel: false,
  workers: 1,
  forbidOnly: !!process.env['CI'],
  retries: process.env['CI'] ? 1 : 0,
  reporter: [['list'], ['html', { open: 'never' }]],
  timeout: 120_000,
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
    { name: 'setup', testMatch: /auth\.setup\.ts/ },
    {
      name: 'chromium',
      testMatch: /\.journey\.ts/,
      dependencies: ['setup'],
      use: { ...devices['Desktop Chrome'] },
    },
  ],
});
