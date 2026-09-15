import { defineConfig, devices } from '@playwright/test';
import { join } from 'node:path';

const outputDir = process.env['BETUFU_PLAYWRIGHT_OUTPUT_DIR'] ?? 'tmp/prediction-playwright';
const reportDir =
  process.env['BETUFU_PLAYWRIGHT_REPORT_DIR'] ?? 'tmp/prediction-playwright-reports';
const chromiumExecutable = process.env['BETUFU_CHROMIUM_EXECUTABLE'];
const firefoxExecutable = process.env['BETUFU_FIREFOX_EXECUTABLE'];
const launchArgs = ['--host-resolver-rules=MAP localhost 127.0.0.1'];

export default defineConfig({
  testDir: './e2e',
  testMatch: 'prediction-pilot.spec.ts',
  fullyParallel: false,
  forbidOnly: true,
  retries: 0,
  workers: 1,
  reporter: [
    ['line'],
    ['json', { outputFile: join(reportDir, 'results.json') }],
    ['junit', { outputFile: join(reportDir, 'results.xml') }],
  ],
  outputDir,
  preserveOutput: 'failures-only',
  use: {
    baseURL: 'http://127.0.0.1:4200',
    headless: true,
    serviceWorkers: 'block',
    trace: 'off',
    screenshot: 'off',
    video: 'off',
  },
  webServer: {
    command: 'npm run start -- --host 127.0.0.1 --port 4200',
    cwd: '.',
    url: 'http://127.0.0.1:4200',
    reuseExistingServer: false,
    timeout: 120_000,
  },
  projects: [
    {
      name: 'phase5-chromium',
      use: {
        ...devices['Desktop Chrome'],
        browserName: 'chromium',
        channel: chromiumExecutable ? undefined : 'chrome',
        launchOptions: {
          args: launchArgs,
          executablePath: chromiumExecutable,
        },
      },
    },
    {
      name: 'phase5-firefox',
      use: {
        ...devices['Desktop Firefox'],
        browserName: 'firefox',
        launchOptions: {
          args: launchArgs,
          executablePath: firefoxExecutable,
        },
      },
    },
  ],
});
