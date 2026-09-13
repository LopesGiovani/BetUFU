import { defineConfig, devices } from '@playwright/test';

export default defineConfig({
  testDir: './e2e',
  testMatch: 'prediction-pilot.spec.ts',
  fullyParallel: false,
  use: { baseURL: 'http://127.0.0.1:4200', trace: 'retain-on-failure' },
  projects: [
    { name: 'phase5-chromium', use: { ...devices['Desktop Chrome'] } },
    { name: 'phase5-firefox', use: { ...devices['Desktop Firefox'] } },
  ],
});
