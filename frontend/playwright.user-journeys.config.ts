import { defineConfig, devices } from '@playwright/test';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const baseURL = process.env['BETUFU_E2E_BASE_URL'] ?? 'http://localhost:4200';
const parsedBaseUrl = new URL(baseURL);
if (!['localhost', '127.0.0.1'].includes(parsedBaseUrl.hostname)) {
  throw new Error('As jornadas reais da Fase 05.1 só podem executar contra loopback.');
}

const outputDir =
  process.env['BETUFU_PLAYWRIGHT_OUTPUT_DIR'] ?? join(tmpdir(), 'betufu-phase05-1-playwright');
const chromiumArgs = ['--host-resolver-rules=MAP localhost 127.0.0.1'];
const firefoxExecutable = process.env['BETUFU_FIREFOX_EXECUTABLE'];

export default defineConfig({
  testDir: './e2e',
  testMatch: 'user-journeys-phase-05-1.spec.ts',
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  workers: 1,
  reporter: [['line']],
  outputDir,
  preserveOutput: 'never',
  timeout: 60_000,
  expect: { timeout: 12_000 },
  use: {
    baseURL,
    headless: true,
    locale: 'pt-BR',
    timezoneId: 'America/Sao_Paulo',
    serviceWorkers: 'block',
    trace: 'off',
    screenshot: 'off',
    video: 'off',
  },
  projects: [
    {
      name: 'phase05-1-desktop-chromium',
      use: {
        ...devices['Desktop Chrome'],
        browserName: 'chromium',
        channel: 'chrome',
        launchOptions: { args: chromiumArgs },
      },
    },
    {
      name: 'phase05-1-desktop-firefox',
      use: {
        ...devices['Desktop Firefox'],
        browserName: 'firefox',
        launchOptions: { executablePath: firefoxExecutable },
      },
    },
    {
      name: 'phase05-1-320px-chromium',
      use: {
        browserName: 'chromium',
        channel: 'chrome',
        viewport: { width: 320, height: 800 },
        launchOptions: { args: chromiumArgs },
      },
    },
    {
      name: 'phase05-1-320px-firefox',
      use: {
        browserName: 'firefox',
        viewport: { width: 320, height: 800 },
        launchOptions: { executablePath: firefoxExecutable },
      },
    },
  ],
});
