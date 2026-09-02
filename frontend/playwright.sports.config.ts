import { defineConfig } from '@playwright/test';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

const outputDir = process.env['BETUFU_PLAYWRIGHT_OUTPUT_DIR']
  ?? join(tmpdir(), 'betufu-playwright-sports');
if (typeof outputDir !== 'string' || outputDir.length === 0) {
  throw new Error('O diretório efêmero do Playwright não foi fornecido.');
}

export default defineConfig({
  testDir: './e2e',
  testMatch: 'sports-pilot.spec.ts',
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  workers: 1,
  reporter: [['line']],
  outputDir,
  preserveOutput: 'never',
  projects: [
    {
      name: 'phase3-chromium',
      use: { browserName: 'chromium', channel: 'chrome' },
    },
    {
      name: 'phase3-firefox',
      use: { browserName: 'firefox' },
    },
  ],
  use: {
    baseURL: 'http://localhost:4200',
    headless: true,
    timezoneId: 'America/Sao_Paulo',
    launchOptions: {
      args: ['--host-resolver-rules=MAP localhost 127.0.0.1'],
    },
    serviceWorkers: 'block',
    trace: 'off',
    screenshot: 'off',
    video: 'off',
  },
  webServer: {
    command: 'npm exec -- ng serve --configuration production --host 127.0.0.1 --port 4200',
    url: 'http://localhost:4200',
    reuseExistingServer: false,
    timeout: 120_000,
  },
});
