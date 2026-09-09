import { expect, test, type Page } from '@playwright/test';

const MARKET_ID = '11111111-1111-4111-8111-111111111111';

function publicPayload() {
  return {
    marketId: MARKET_ID,
    eventId: '22222222-2222-4222-8222-222222222222',
    marketType: 'HEAD_TO_HEAD',
    state: 'OPEN',
    current: {
      publicationId: 'pub-current',
      publishedOdds: [1.82, 2.05],
      publishedAt: '2026-09-08T12:00:00Z',
    },
    history: [
      { publicationId: 'pub-previous', publishedOdds: 1.9, publishedAt: '2026-09-07T12:00:00Z' },
    ],
    explanation: {
      algorithmVersion: 'elo-davidson-v1',
      configurationVersion: 'cfg-2026-09',
      fairProbability: 0.54,
      margin: 0.05,
      roundingMode: 'HALF_UP',
      confidence: 0.8,
      confidenceBand: 'HIGH',
      sampleSize: 18,
    },
  };
}

async function mockPublic(page: Page): Promise<void> {
  await page.route(`**/api/v1/public/odds/markets/${MARKET_ID}`, async (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(publicPayload()),
    }),
  );
}

async function mockOperations(page: Page): Promise<void> {
  await page.route('**/api/v1/session', async (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ authenticated: true, displayName: 'Operador' }),
    }),
  );
  await page.route('**/api/v1/profile/me', async (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        identityId: '33333333-3333-4333-8333-333333333333',
        nickname: 'operador',
        matriculaStatus: 'REGISTRADA',
        favoriteOrganizationId: null,
      }),
    }),
  );
  await page.route('**/api/v1/operations/capabilities', async (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        capabilities: [
          'VIEW_ODDS_MARKET',
          'MANAGE_ODDS_MARKET',
          'PROPOSE_ODDS_OVERRIDE',
          'APPROVE_ODDS_OVERRIDE',
        ],
      }),
    }),
  );
  await page.route(`**/api/v1/operations/odds/markets/${MARKET_ID}`, async (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        marketId: MARKET_ID,
        eventId: '22222222-2222-4222-8222-222222222222',
        marketType: 'HEAD_TO_HEAD',
        state: 'SUSPENDED',
        currentVersionId: 'version-2',
        versionNo: 2,
        lockVersion: 4,
        updatedAt: '2026-09-08T12:00:00Z',
      }),
    }),
  );
}

test.describe('Wave 16 odds', () => {
  for (const viewport of [
    { name: 'desktop', width: 1280, height: 800 },
    { name: '320px', width: 320, height: 800 },
  ]) {
    test(`publica odd e explicacao sem PII em ${viewport.name}`, async ({ page }) => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      await mockPublic(page);
      await page.goto(`/odds/mercados/${MARKET_ID}`);
      await expect(page.getByRole('heading', { level: 1, name: 'Mercado pre-jogo' })).toBeVisible();
      await expect(page.getByText('1.82')).toBeVisible();
      await page.getByRole('button', { name: 'Ver explicacao completa' }).click();
      await expect(page.getByText('elo-davidson-v1')).toBeVisible();
      const body = await page.locator('body').innerText();
      expect(body).not.toMatch(
        /Operador|identity-|matricula|HMAC|token|liquidez|saldo|aposta|Apostar/iu,
      );
      const proof = await page.evaluate(() => {
        const pageElement = document.querySelector<HTMLElement>('.market-page');
        const history = document.querySelector<HTMLElement>('.history');
        return {
          innerWidth: window.innerWidth,
          pageWidth: pageElement?.getBoundingClientRect().width ?? 0,
          historyWidth: history?.getBoundingClientRect().width ?? 0,
        };
      });
      expect(proof.innerWidth).toBe(viewport.width);
      expect(proof.pageWidth).toBeLessThanOrEqual(proof.innerWidth);
      expect(proof.historyWidth).toBeLessThanOrEqual(proof.innerWidth);
    });
  }

  test('console preserva maker-checker e estados sem autorizar pelo DOM', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await mockOperations(page);
    await page.goto(`/operacao/odds/mercados/${MARKET_ID}`);
    await expect(page.getByRole('heading', { level: 1, name: 'Mercado de odds' })).toBeVisible();
    await page.getByRole('button', { name: 'Corrigir fonte e recalcular' }).click();
    await expect(page.getByText('Tentativa registrada')).toBeVisible();
    await expect(page.getByText(/nao concede permissao/i)).toBeVisible();
    await expect(page.getByRole('button', { name: 'Aprovar override' })).toHaveCount(0);
    await page.route(`**/api/v1/operations/odds/markets/${MARKET_ID}`, async (route) =>
      route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          marketId: MARKET_ID,
          eventId: '22222222-2222-4222-8222-222222222222',
          marketType: 'HEAD_TO_HEAD',
          state: 'CLOSED',
          currentVersionId: 'version-3',
          versionNo: 3,
          lockVersion: 5,
          updatedAt: '2026-09-08T12:01:00Z',
        }),
      }),
    );
    await page.getByRole('button', { name: 'Atualizar leitura' }).click();
    await expect(page.getByText('CLOSED/LOCKED')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Suspender mercado' })).toHaveCount(0);
  });
});
