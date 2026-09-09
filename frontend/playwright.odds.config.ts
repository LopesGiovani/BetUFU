import { defineConfig, devices } from '@playwright/test';

const outputDir = process.env['BETUFU_PLAYWRIGHT_OUTPUT_DIR'] ?? 'tmp/odds-playwright';

export default defineConfig({
  testDir: './e2e',
  testMatch: 'odds-pilot.spec.ts',
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  workers: 1,
  reporter: [['line']],
  outputDir,
  preserveOutput: 'never',
  use: {
    baseURL: 'http://127.0.0.1:4200',
    headless: true,
    serviceWorkers: 'block',
    trace: 'off',
    screenshot: 'off',
    video: 'off',
    launchOptions: { args: ['--host-resolver-rules=MAP localhost 127.0.0.1'] },
  },
  webServer: {
    command: 'npm run start -- --host 127.0.0.1 --port 4200',
    cwd: '.',
    url: 'http://127.0.0.1:4200',
    reuseExistingServer: true,
    timeout: 120_000,
  },
  projects: [
    {
      name: 'phase4-chromium',
      use: {
        ...devices['Desktop Chrome'],
        browserName: 'chromium',
        launchOptions: { executablePath: 'C:/Program Files/Google/Chrome/Application/chrome.exe' },
      },
    },
    { name: 'phase4-firefox', use: { ...devices['Desktop Firefox'], browserName: 'firefox' } },
  ],
});
