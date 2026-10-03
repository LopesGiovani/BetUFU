import { expect, test, type Page } from '@playwright/test';
import { execFile } from 'node:child_process';
import { resolve } from 'node:path';
import { promisify } from 'node:util';

function required(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`Preflight: forneca ${name} para a persona local real.`);
  return value;
}

async function login(page: Page, prefix = 'BETUFU_E2E'): Promise<void> {
  await page.goto('/');
  await page.locator('a.arena-nav__login:visible').click();
  await page.getByLabel('Endereço de email').fill(required(`${prefix}_USERNAME`));
  await page
    .getByRole('textbox', { name: 'Senha', exact: true })
    .fill(required(`${prefix}_PASSWORD`));
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page.locator('a.arena-nav__account:visible')).toBeVisible();
  await page.locator('a.arena-nav__account:visible').click();
}

async function readRealApi(page: Page, url: string) {
  const response = await page.context().request.get(url);
  expect(response.status()).toBe(200);
  return response.json();
}

test('correcao real preserva fatos antigos, pendencia e conquista revogada apos refresh', async ({
  page,
}) => {
  await login(page);
  const history = page
    .waitForResponse(
      (r) => r.request().method() === 'GET' && r.url().endsWith('/api/v1/me/settlements'),
    )
    .then(async (r) => ({ status: r.status(), data: await readRealApi(page, r.url()) }));
  const corrections = page
    .waitForResponse(
      (r) => r.request().method() === 'GET' && r.url().endsWith('/api/v1/me/credit-corrections'),
    )
    .then(async (r) => ({ status: r.status(), data: await readRealApi(page, r.url()) }));
  await page.getByRole('link', { name: 'Historico de liquidacoes', exact: true }).click();
  const settled = await history;
  const corrected = await corrections;
  expect(settled.status).toBe(200);
  expect(corrected.status).toBe(200);
  const historyData = settled.data;
  const correctionData = corrected.data;
  expect(
    historyData.items.some((item: { current: boolean }) => !item.current),
    'Exige fato historico supersedido real.',
  ).toBe(true);
  expect(
    correctionData.items.some((item: { pending: number }) => item.pending > 0),
    'Exige correcao real pendente.',
  ).toBe(true);
  await expect(page.getByText('Fato historico preservado', { exact: true }).first()).toBeVisible();
  await expect(page.getByText('Correcao pendente', { exact: true }).first()).toBeVisible();
  await expect(page.getByText('Valor recuperado', { exact: true }).first()).toBeVisible();
  await expect(page.getByText('Valor pendente', { exact: true }).first()).toBeVisible();
  expect(JSON.stringify(correctionData)).not.toMatch(
    /issuer|subject|hmac|matricula|authorizedBy|description|calculation/iu,
  );
  const awards = page
    .waitForResponse(
      (r) => r.request().method() === 'GET' && r.url().endsWith('/api/v1/me/achievements'),
    )
    .then(async (r) => ({ status: r.status(), data: await readRealApi(page, r.url()) }));
  await page.getByRole('link', { name: 'Conquistas', exact: true }).click();
  const response = await awards;
  expect(response.status).toBe(200);
  const data = response.data;
  expect(
    data.items.some((item: { state: string }) => item.state === 'REVOKED'),
    'Exige conquista real revogada.',
  ).toBe(true);
  await expect(page.getByText('Revogada', { exact: true }).first()).toBeVisible();
  await page.reload();
  await expect(page.getByText('Revogada', { exact: true }).first()).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test('erro de transporte real permite recarregar sem resposta artificial', async ({ page }) => {
  await page.goto('/');
  await page.locator('a[href="/ranking"]:visible').click();
  await expect(page.locator('section[aria-labelledby="ranking-title"]')).toHaveAttribute(
    'aria-busy',
    'false',
  );
  await page.getByRole('link', { name: 'BETUFU — início', exact: true }).click();
  await page.context().setOffline(true);
  try {
    await page.locator('a[href="/ranking"]:visible').click();
    await expect(
      page.getByRole('alert').filter({ hasText: 'Nao foi possivel carregar o ranking.' }),
    ).toBeVisible();
  } finally {
    await page.context().setOffline(false);
  }
  const recovered = page.waitForResponse(
    (r) => r.request().method() === 'GET' && r.url().endsWith('/api/v1/public/ranking/lifetime'),
  );
  await page.getByRole('button', { name: 'Recarregar ranking', exact: true }).click();
  expect((await recovered).status()).toBe(200);
  await expect(page.getByRole('alert')).toHaveCount(0);
});

test('sessao expirada no servidor retorna ao login contextual sem esconder dados privados', async ({
  page,
}) => {
  await login(page);
  await page.getByRole('link', { name: 'Historico de liquidacoes', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Calculo' }).first()).toBeVisible();
  await promisify(execFile)(
    process.execPath,
    [
      resolve(process.cwd(), '../scripts/phase6-e2e.mjs'),
      'expire',
      required('BETUFU_E2E_ISOLATED_RUN'),
    ],
    { windowsHide: true },
  );
  await expect
    .poll(async () => (await page.context().request.get('/api/v1/me/settlements')).status())
    .toBe(401);
  await page.reload();
  await expect(page).toHaveURL(/\/entrar\?returnTo=%2Fconta%2Fhistorico$/u);
  await expect(page.getByRole('heading', { name: 'Calculo' })).toHaveCount(0);
});
