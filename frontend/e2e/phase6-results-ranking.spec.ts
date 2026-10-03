import { expect, test, type Page } from '@playwright/test';

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
  await expect(page.locator('a.arena-nav__account:visible')).toHaveText('Minha conta');
  await page.locator('a.arena-nav__account:visible').click();
  await expect(page.getByRole('heading', { name: 'Seu perfil', exact: true })).toBeVisible();
}

async function accessibleSurface(page: Page): Promise<void> {
  await expect(page.locator('main')).toHaveCount(1);
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  // Start at the same reachable landmark, not at the previous journey's last control.
  await page.locator('a.skip-link').focus();
  await page.keyboard.press('Tab');
  expect(await page.evaluate(() => document.activeElement?.tagName)).not.toBe('BODY');
}

async function readRealApi(page: Page, url: string) {
  // Read the real API in the same session; Chrome can discard network bodies.
  const response = await page.context().request.get(url);
  expect(response.status()).toBe(200);
  return response.json();
}

test('Arena descobre ranking publico com API real, refresh e historico do navegador', async ({
  page,
}) => {
  await page.goto('/');
  const response = page
    .waitForResponse(
      (r) => r.request().method() === 'GET' && r.url().endsWith('/api/v1/public/ranking/lifetime'),
    )
    .then(async (r) => ({ status: r.status(), data: await readRealApi(page, r.url()) }));
  await page.locator('a[href="/ranking"]:visible').click();
  const actual = await response;
  expect(actual.status).toBe(200);
  const data = actual.data;
  expect(Array.isArray(data.entries)).toBe(true);
  expect(data.entries.length, 'Ranking vazio nao certifica classificacao real.').toBeGreaterThan(0);
  expect(
    data.entries.some((entry: { verificationActive: boolean }) => !entry.verificationActive),
  ).toBe(true);
  for (const entry of data.entries) {
    expect(Object.keys(entry).sort()).toEqual([
      'hitRate',
      'losses',
      'nickname',
      'position',
      'verificationActive',
      'wins',
    ]);
  }
  await expect(page.getByRole('heading', { name: 'Ranking vitalicio', exact: true })).toBeVisible();
  await expect(page.getByText('Matricula atual inativa', { exact: true })).toBeVisible();
  if (data.entries.length) await expect(page.locator('tbody tr')).toHaveCount(data.entries.length);
  else
    await expect(
      page.getByText('Ainda nao ha palpites com resultado definitivo para classificar.'),
    ).toBeVisible();
  await accessibleSurface(page);
  await page.reload();
  await expect(page.getByRole('heading', { name: 'Ranking vitalicio', exact: true })).toBeVisible();
  await page.goBack();
  await page.goForward();
  await expect(page).toHaveURL(/\/ranking$/u);
});

test('conta percorre historico real, conquistas e perfil sem URLs internas', async ({ page }) => {
  await login(page);
  const history = page
    .waitForResponse(
      (r) => r.request().method() === 'GET' && r.url().endsWith('/api/v1/me/settlements'),
    )
    .then(async (r) => ({ status: r.status(), data: await readRealApi(page, r.url()) }));
  await page.getByRole('link', { name: 'Historico de liquidacoes', exact: true }).click();
  const response = await history;
  expect(response.status).toBe(200);
  const data = response.data;
  expect(
    data.items.some((item: { processingStatus: string }) => item.processingStatus === 'COMPLETE'),
    'A persona precisa de liquidacao real concluida; estado vazio nao certifica resultado.',
  ).toBe(true);
  await expect(page.getByRole('heading', { name: 'Resultado confirmado' }).first()).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Calculo' }).first()).toBeVisible();
  await expect(page.getByRole('heading', { name: 'Movimentacoes de saldo' }).first()).toBeVisible();
  await accessibleSurface(page);
  await page.reload();
  await expect(
    page.getByRole('heading', { name: 'Historico de liquidacoes', exact: true }),
  ).toBeVisible();
  const awards = page.waitForResponse(
    (r) => r.request().method() === 'GET' && r.url().endsWith('/api/v1/me/achievements'),
  );
  await page.getByRole('link', { name: 'Conquistas', exact: true }).click();
  expect((await awards).status()).toBe(200);
  await expect(page.getByRole('heading', { name: 'Conquistas', exact: true })).toBeVisible();
  await page.getByText('Regras das conquistas', { exact: true }).click();
  await expect(page.getByText(/Nenhuma conquista gera creditos ou premios/u)).toBeVisible();
  await accessibleSurface(page);
  await page.getByRole('link', { name: 'Meu perfil', exact: true }).click();
  await expect(page.getByRole('heading', { name: 'Conquistas', exact: true })).toBeVisible();
});

test('grupo real isola segunda sessao e usa metricas vitalicias sem carteira', async ({
  page,
  browser,
  baseURL,
}) => {
  await login(page);
  await page.getByRole('link', { name: 'Meus grupos', exact: true }).click();
  await expect(page.locator('section[aria-labelledby="groups-title"]')).toHaveAttribute(
    'aria-busy',
    'false',
  );
  const groupName = `Certificacao ${Date.now()}`;
  await page.getByLabel('Nome do grupo', { exact: true }).fill(groupName);
  const created = page
    .waitForResponse(
      (r) => r.url().endsWith('/api/v1/me/groups') && r.request().method() === 'POST',
      { timeout: 15_000 },
    )
    .then((r) => r.status());
  await page.getByRole('button', { name: 'Criar grupo', exact: true }).click();
  const createdResponse = await created;
  expect(createdResponse).toBe(201);
  const groups = await readRealApi(page, '/api/v1/me/groups');
  const { groupId } = groups.items.find((group: { name: string }) => group.name === groupName);
  await page.getByRole('button', { name: groupName, exact: true }).click();
  await expect(page.getByRole('region', { name: 'Metricas do grupo' })).toBeVisible();
  await expect(page.getByText('Sem saldo coletivo ou premios.', { exact: false })).toBeVisible();
  await accessibleSurface(page);
  const isolated = await browser.newContext({ baseURL });
  try {
    const outsider = await isolated.newPage();
    await login(outsider, 'BETUFU_E2E_OTHER');
    const denied = await isolated.request.get(`/api/v1/me/groups/${groupId}`);
    expect(denied.status()).toBe(404);
    const deniedBody = await denied.json();
    expect(JSON.stringify(deniedBody)).not.toContain(groupName);
    await outsider.getByRole('link', { name: 'Meus grupos', exact: true }).click();
    await expect(outsider.getByRole('button', { name: groupName, exact: true })).toHaveCount(0);
    await outsider.getByLabel('Codigo de convite', { exact: true }).fill('X'.repeat(43));
    await outsider.getByRole('button', { name: 'Entrar com convite', exact: true }).click();
    await expect(
      outsider.getByRole('button', { name: 'Tentar outro convite', exact: true }),
    ).toBeVisible();
    await page.getByRole('button', { name: 'Emitir convite de um dia', exact: true }).click();
    const invitation = page.locator('p[role="status"] code');
    await expect(invitation).toBeVisible();
    await outsider
      .getByLabel('Codigo de convite', { exact: true })
      .fill((await invitation.textContent())!.trim());
    await outsider.getByRole('button', { name: 'Entrar com convite', exact: true }).click();
    await expect(outsider.getByRole('button', { name: groupName, exact: true })).toBeVisible();
    await outsider.getByRole('button', { name: groupName, exact: true }).click();
    await expect(outsider.getByRole('region', { name: 'Metricas do grupo' })).toBeVisible();
    const members = await page.context().request.get(`/api/v1/me/groups/${groupId}`);
    expect(members.status()).toBe(200);
    const view = await members.json();
    const ranking = await page.context().request.get('/api/v1/public/ranking/lifetime');
    const lifetime = (await ranking.json()).entries;
    for (const member of view.standings) {
      const global = lifetime.find(
        (entry: { nickname: string }) => entry.nickname === member.nickname,
      );
      expect(member.wins).toBe(global?.wins ?? 0);
      expect(member.hitRate).toBe(global?.hitRate ?? 0);
    }
  } finally {
    await isolated.close();
    await page.getByRole('button', { name: 'Encerrar grupo', exact: true }).click();
    await expect(page.getByRole('dialog')).toBeVisible();
    await page.keyboard.press('Escape');
    await expect(page.getByRole('button', { name: 'Encerrar grupo', exact: true })).toBeFocused();
    await page.getByRole('button', { name: 'Encerrar grupo', exact: true }).click();
    await page.getByRole('button', { name: 'Confirmar encerramento', exact: true }).click();
    await page.getByRole('button', { name: `${groupName} (Encerrado)`, exact: true }).click();
    await expect(
      page.getByText('Grupo encerrado: historico preservado', { exact: true }),
    ).toBeVisible();
  }
});

test('anonimo recebe login contextual, 401 privado e 404 acessivel', async ({ page }) => {
  await page.goto('/conta/historico');
  await expect(page).toHaveURL(/\/entrar\?returnTo=%2Fconta%2Fhistorico$/u);
  expect((await page.context().request.get('/api/v1/me/settlements')).status()).toBe(401);
  await page.goto('/rota-inexistente-phase6');
  await expect(page.getByRole('heading', { name: 'Página não encontrada' })).toBeFocused();
  await page.getByRole('link', { name: 'Voltar para a Arena' }).click();
  await expect(page).toHaveURL(/\/$/u);
});

test('carregamento real continua acessivel com transporte lento', async ({ page }) => {
  await page.goto('/');
  const transport = await page.context().newCDPSession(page);
  try {
    await transport.send('Network.enable');
    await transport.send('Network.emulateNetworkConditions', {
      offline: false,
      latency: 1000,
      downloadThroughput: 100_000,
      uploadThroughput: 100_000,
    });
    await page.locator('a[href="/ranking"]:visible').click();
    await expect(
      page.getByRole('status').filter({ hasText: 'Carregando ranking...' }),
    ).toBeVisible();
    await expect(page.locator('section[aria-labelledby="ranking-title"]')).toHaveAttribute(
      'aria-busy',
      'true',
    );
    await expect(page.locator('section[aria-labelledby="ranking-title"]')).toHaveAttribute(
      'aria-busy',
      'false',
    );
    await expect(page.locator('tbody tr').first()).toBeVisible();
  } finally {
    await transport.detach();
  }
});
