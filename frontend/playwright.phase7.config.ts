import { defineConfig } from '@playwright/test';

const project = process.env['BETUFU_E2E_PROJECT'];
const outputDir = process.env['BETUFU_PLAYWRIGHT_OUTPUT_DIR'];
if (!outputDir || !['phase7-desktop', 'phase7-320px'].includes(project ?? ''))
  throw new Error('Use the approved isolated Phase7 runner.');

export default defineConfig({
  testDir: './e2e', testMatch: 'phase7-integrated.spec.ts',
  globalSetup: './e2e/phase7.setup.ts',
  fullyParallel: false, forbidOnly: true, retries: 0, workers: 1,
  timeout: 180_000, expect: { timeout: 15_000 },
  reporter: [['line']], outputDir, preserveOutput: 'never',
  use: {
    baseURL: 'http://localhost:4300', headless: true,
    browserName: 'chromium', channel: 'chrome',
    launchOptions: { args: ['--host-resolver-rules=MAP localhost 127.0.0.1'] },
    locale: 'pt-BR', timezoneId: 'America/Sao_Paulo', serviceWorkers: 'block',
    trace: 'off', screenshot: 'off', video: 'off',
  },
  projects: [{ name: project, use: {
    viewport: { width: project === 'phase7-320px' ? 320 : 1280, height: 800 },
  } }],
});
