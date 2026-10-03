import { defineConfig } from '@playwright/test';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const baseURL = process.env['BETUFU_E2E_BASE_URL'] ?? 'http://localhost:4200';
if (!['localhost', '127.0.0.1'].includes(new URL(baseURL).hostname)) {
  throw new Error('A certificacao real da Fase 6 exige ambiente local loopback.');
}

export default defineConfig({
  testDir: './e2e',
  testMatch: ['phase6-results-ranking.spec.ts', 'phase6-corrections.spec.ts'],
  globalSetup: './e2e/phase6.setup.ts',
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  workers: 1,
  timeout: 90_000,
  expect: { timeout: 15_000 },
  reporter: [['line']],
  outputDir: join(tmpdir(), 'betufu-phase6-playwright'),
  preserveOutput: 'never',
  use: {
    baseURL,
    headless: true,
    browserName: 'chromium',
    channel: 'chrome',
    launchOptions: { args: ['--host-resolver-rules=MAP localhost 127.0.0.1'] },
    locale: 'pt-BR',
    timezoneId: 'America/Sao_Paulo',
    serviceWorkers: 'block',
    trace: 'off',
    screenshot: 'off',
    video: 'off',
  },
  projects: [
    { name: 'phase6-desktop', use: { viewport: { width: 1280, height: 800 } } },
    { name: 'phase6-320px', use: { viewport: { width: 320, height: 800 } } },
  ],
});
