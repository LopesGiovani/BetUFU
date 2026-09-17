import assert from "node:assert/strict";
import test from "node:test";

import { validateUserJourneySources } from "./validate-phase05-1-user-journeys.mjs";

const validSpec = `
import { test } from '@playwright/test';
test('jornadas reais', async ({ page, browser }) => {
  await page.clock.setFixedTime(new Date('2026-09-15T16:00:00-03:00'));
  await page.goto('/');
  await page.getByRole('link', { name: /^Abrir evento / }).click();
  await page.getByRole('link', { name: 'Ver odds da partida' }).click();
  await page.getByRole('link', { name: 'Entrar para palpitar' }).click();
  await page.getByLabel('Endereço de email').fill(process.env['BETUFU_E2E_USERNAME']);
  await page.getByLabel('Senha').fill(process.env['BETUFU_E2E_PASSWORD']);
  await page.getByRole('link', { name: 'Minha conta' }).click();
  await page.getByText('Matrícula registrada').waitFor();
  await page.goBack();
  await page.goForward();
  await page.reload();
  const isolated = await browser.newContext();
  await isolated.close();
  await page.goto('/conta/perfil');
  await page.getByRole('heading', { name: 'Entre para continuar' }).waitFor();
  await page.goto('/rota-inexistente-fase-05-1');
  await page.getByRole('heading', { name: 'Página não encontrada' }).waitFor();
  await page.getByRole('link', { name: 'Voltar para a Arena' }).click();
  await page.getByRole('heading', { name: 'Mercado encerrado' }).waitFor();
});
`;

const validConfig = `
const firefoxExecutable = process.env['BETUFU_FIREFOX_EXECUTABLE'];
export default defineConfig({
  retries: 0,
  workers: 1,
  use: { baseURL: 'http://localhost:4200', trace: 'off', screenshot: 'off', video: 'off' },
  projects: [
    { name: 'desktop-chromium', use: { browserName: 'chromium' } },
    { name: 'desktop-firefox', use: { browserName: 'firefox' } },
    { name: '320px-chromium', use: { browserName: 'chromium', viewport: { width: 320, height: 800 } } },
    { name: '320px-firefox', use: { browserName: 'firefox', viewport: { width: 320, height: 800 } } },
  ],
});
`;

test("accepts a real-browser suite with all required journeys", () => {
  assert.doesNotThrow(() => validateUserJourneySources(validSpec, validConfig));
});

test("rejects business API interception", () => {
  assert.throws(
    () =>
      validateUserJourneySources(
        `${validSpec}\npage.route('/api/v1/session', () => {});`,
        validConfig,
      ),
    /MOCK_FORBIDDEN/u,
  );
});

test("rejects literal credentials", () => {
  assert.throws(
    () =>
      validateUserJourneySources(
        `${validSpec}\nconst password = 'local-secret';`,
        validConfig,
      ),
    /CREDENTIAL_LITERAL_FORBIDDEN/u,
  );
});

test("rejects a literal UUID used by the browser journey", () => {
  assert.throws(
    () =>
      validateUserJourneySources(
        `${validSpec}\nconst market = '5601e9f5-ea85-5d2a-b541-93bea03e83d2';`,
        validConfig,
      ),
    /UUID_LITERAL_FORBIDDEN/u,
  );
});

test("rejects missing integrated journey coverage", () => {
  assert.throws(
    () =>
      validateUserJourneySources(
        validSpec.replace("'Matrícula registrada'", "'Perfil'"),
        validConfig,
      ),
    /REQUIRED_JOURNEY_MARKER/u,
  );
});
