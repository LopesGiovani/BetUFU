import { expect, test, type Browser, type Page } from '@playwright/test';

const BASE_URL = process.env['BETUFU_E2E_BASE_URL'] ?? 'http://localhost:4200';
const SEED_INSTANT = process.env['BETUFU_E2E_SEED_INSTANT'] ?? '2026-09-15T16:00:00-03:00';
const AFTER_DEADLINE_INSTANT =
  process.env['BETUFU_E2E_AFTER_DEADLINE_INSTANT'] ?? '2026-09-15T18:00:00-03:00';
const MARKET_PATH_PATTERN = /^\/odds\/mercados\/[0-9a-f-]+$/iu;

function requiredEnvironment(name: 'BETUFU_E2E_USERNAME' | 'BETUFU_E2E_PASSWORD'): string {
  const value = process.env[name]?.trim();
  if (!value) throw new Error(`${name} deve ser fornecida para o login real no Keycloak.`);
  return value;
}

async function useSeedClock(page: Page): Promise<void> {
  await page.clock.setFixedTime(new Date(SEED_INSTANT));
}

async function discoverMarketFromArena(page: Page): Promise<string> {
  await useSeedClock(page);
  await page.goto('/');
  await expect(page.getByRole('heading', { level: 1, name: /Hoje em quadra/u })).toBeVisible();

  const eventLinks = page.locator('a[aria-label^="Abrir evento "]');
  await expect(eventLinks.first()).toBeVisible();
  const candidates = await eventLinks.evaluateAll((links) =>
    links.map((link) => ({
      name: link.getAttribute('aria-label') ?? '',
      href: link.getAttribute('href') ?? '',
    })),
  );
  expect(candidates.length).toBeGreaterThan(0);

  for (const candidate of candidates) {
    await page.getByRole('link', { name: candidate.name, exact: true }).click();
    const oddsLink = page.getByRole('link', { name: 'Ver odds da partida' });
    const eventLoaded = page.locator('main.public-detail').getByRole('heading', { level: 1 });
    await expect(eventLoaded).not.toHaveText('Carregando evento');
    if (await oddsLink.isVisible()) {
      const marketPath = await oddsLink.getAttribute('href');
      expect(marketPath).toMatch(MARKET_PATH_PATTERN);
      await oddsLink.click();
      await expect(page).toHaveURL(new RegExp(`${marketPath}$`, 'u'));
      await expect(page.getByRole('heading', { level: 1, name: 'Mercado pre-jogo' })).toBeVisible();
      return marketPath!;
    }
    await page.goBack();
    await expect(page.getByRole('heading', { level: 1, name: /Hoje em quadra/u })).toBeVisible();
  }

  throw new Error('Nenhum mercado foi descoberto pelos links visíveis da Arena.');
}

async function expectAnonymousArena(page: Page): Promise<void> {
  await expect(page.locator('a.arena-nav__login:visible')).toHaveText('Entrar');
  await expect(page.locator('a.arena-nav__account:visible')).toHaveCount(0);
}

async function expectVisibleBalance(page: Page): Promise<void> {
  await expect(page.locator('.arena-session-balance:visible')).toContainText(
    /\d{1,3}(?:\.\d{3})*,\d{2}\s+créditos/u,
  );
}

async function assertIsolatedAnonymousSession(browser: Browser): Promise<void> {
  const isolatedContext = await browser.newContext({ baseURL: BASE_URL, locale: 'pt-BR' });
  try {
    const isolatedPage = await isolatedContext.newPage();
    await useSeedClock(isolatedPage);
    await isolatedPage.goto('/');
    await expectAnonymousArena(isolatedPage);
  } finally {
    await isolatedContext.close();
  }
}

test.describe('Fase 05.1 — jornadas reais integradas', () => {
  test('Arena descobre evento e mercado sem UUID, preservando navegação, refresh e deadline', async ({
    page,
  }) => {
    await discoverMarketFromArena(page);
    await expect(
      page.getByRole('heading', { level: 3, name: 'Entre para palpitar' }),
    ).toBeVisible();

    await page.goBack();
    await expect(page.getByRole('link', { name: 'Ver odds da partida' })).toBeVisible();
    await page.goForward();
    await expect(page.getByRole('heading', { level: 1, name: 'Mercado pre-jogo' })).toBeVisible();
    await page.reload();
    await expect(
      page.getByRole('heading', { level: 3, name: 'Entre para palpitar' }),
    ).toBeVisible();

    await page.clock.setFixedTime(new Date(AFTER_DEADLINE_INSTANT));
    await page.reload();
    await expect(page.getByRole('heading', { level: 3, name: 'Mercado encerrado' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Entrar para palpitar' })).toHaveCount(0);
  });

  test('login real retorna ao mercado e a Arena reconhece conta, saldo e matrícula ativa', async ({
    page,
    browser,
  }) => {
    const username = requiredEnvironment('BETUFU_E2E_USERNAME');
    const password = requiredEnvironment('BETUFU_E2E_PASSWORD');
    const marketPath = await discoverMarketFromArena(page);

    await page.getByRole('link', { name: 'Entrar para palpitar' }).click();
    await expect(page).toHaveURL(/localhost:8081\/realms\/betufu-local/u);
    await page.getByLabel('Endereço de email').fill(username);
    await page.getByRole('textbox', { name: 'Senha', exact: true }).fill(password);
    await page.getByRole('button', { name: 'Entrar', exact: true }).click();

    await expect(page).toHaveURL(new RegExp(`http://localhost:4200${marketPath}$`, 'u'));
    await expect(page.locator('a.arena-nav__account:visible')).toHaveText('Minha conta');
    await expect(page.locator('a.arena-nav__login:visible')).toHaveCount(0);

    await page.getByRole('link', { name: 'BETUFU — início' }).click();
    await expect(page).toHaveURL('http://localhost:4200/');
    const balanceResponse = page.waitForResponse(
      (response) => response.url().endsWith('/api/v1/me/credits') && response.status() === 200,
      { timeout: 30_000 },
    );
    await page.reload();
    await balanceResponse;
    await expect(page.locator('a.arena-nav__account:visible')).toHaveText('Minha conta');
    await expectVisibleBalance(page);

    await page.locator('a.arena-nav__account:visible').click();
    await expect(page).toHaveURL(/\/conta\/perfil$/u);
    await expect(page.getByRole('heading', { level: 1, name: 'Seu perfil' })).toBeVisible();
    await expect(page.getByText('Matrícula registrada', { exact: true })).toBeVisible();

    const browserStorageKeys = await page.evaluate(() => ({
      localStorage: Object.keys(localStorage),
      sessionStorage: Object.keys(sessionStorage),
    }));
    expect(browserStorageKeys.localStorage.join('|')).not.toMatch(/authorization|bearer|token/iu);
    expect(browserStorageKeys.sessionStorage.join('|')).not.toMatch(/authorization|bearer|token/iu);
    await expect(page.locator('input[type="password"]')).toHaveCount(0);

    await assertIsolatedAnonymousSession(browser);
  });

  test('rota protegida anônima preserva returnTo e rota inválida oferece 404 acessível', async ({
    page,
  }) => {
    await useSeedClock(page);
    await page.goto('/conta/perfil');
    await expect(page).toHaveURL(/\/entrar\?returnTo=%2Fconta%2Fperfil$/u);
    const loginHeading = page.getByRole('heading', { level: 1, name: 'Entre para continuar' });
    await expect(loginHeading).toBeVisible();
    await expect(loginHeading).toBeFocused();
    await expect(page.getByRole('link', { name: 'Entrar com a conta UFU' })).toHaveAttribute(
      'href',
      '/login/start?returnTo=%2Fconta%2Fperfil',
    );

    await page.goto('/rota-inexistente-fase-05-1');
    const notFoundHeading = page.getByRole('heading', { level: 1, name: 'Página não encontrada' });
    await expect(notFoundHeading).toBeVisible();
    await expect(notFoundHeading).toBeFocused();
    await page.getByRole('link', { name: 'Voltar para a Arena' }).click();
    await expect(page).toHaveURL('http://localhost:4200/');
    await expectAnonymousArena(page);
  });
});
