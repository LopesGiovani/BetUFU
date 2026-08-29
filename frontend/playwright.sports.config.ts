import { defineConfig } from '@playwright/test';

const outputDir = process.env['BETUFU_PLAYWRIGHT_OUTPUT_DIR'];
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
  use: {
    baseURL: 'http://localhost:4200',
    browserName: 'chromium',
    channel: 'chrome',
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
    command: 'npm run start -- --host 127.0.0.1 --port 4200',
    url: 'http://localhost:4200',
    reuseExistingServer: false,
    timeout: 120_000,
  },
});
