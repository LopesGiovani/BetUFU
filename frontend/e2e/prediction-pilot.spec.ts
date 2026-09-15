import { expect, test, type Page, type Request } from '@playwright/test';

const MARKET_ID = '11111111-1111-4111-8111-111111111111';
const MARKET_VERSION_1 = '22222222-2222-4222-8222-222222222222';
const PUBLICATION_1 = '33333333-3333-4333-8333-333333333333';
const MARKET_VERSION_2 = '55555555-5555-4555-8555-555555555555';
const PUBLICATION_2 = '66666666-6666-4666-8666-666666666666';

interface BrowserEvidence {
  requests: Array<{ url: string; method: string; headers: Record<string, string>; body: string | null }>;
  apiResponses: string[];
  consoleMessages: string[];
}

function publicPayload(version = 1) {
  const updated = version === 2;
  return {
    marketId: MARKET_ID,
    marketVersionId: updated ? MARKET_VERSION_2 : MARKET_VERSION_1,
    eventId: '77777777-7777-4777-8777-777777777777',
    marketType: 'HEAD_TO_HEAD',
    state: 'OPEN',
    closeDeadline: '2026-09-15T12:00:00Z',
    current: {
      publicationId: updated ? PUBLICATION_2 : PUBLICATION_1,
      publishedOdds: updated ? [1.9, 1.98] : [1.82, 2.05],
      publishedAt: updated ? '2026-09-14T12:01:00Z' : '2026-09-14T12:00:00Z',
    },
    history: [
      {
        publicationId: '88888888-8888-4888-8888-888888888888',
        publishedOdds: 1.9,
        publishedAt: '2026-09-13T12:00:00Z',
      },
    ],
    explanation: {
      algorithmVersion: updated ? 'elo-davidson-v2' : 'elo-davidson-v1',
      configurationVersion: updated ? 'cfg-2026-09-b' : 'cfg-2026-09-a',
      fairProbability: 0.54,
      margin: 0.05,
      roundingMode: 'HALF_UP',
      confidence: 0.8,
      confidenceBand: 'HIGH',
      sampleSize: 18,
    },
  };
}

function ticket(version = 1) {
  const updated = version === 2;
  return {
    predictionId: '99999999-9999-4999-8999-999999999999',
    marketId: MARKET_ID,
    marketVersionId: updated ? MARKET_VERSION_2 : MARKET_VERSION_1,
    publicationId: updated ? PUBLICATION_2 : PUBLICATION_1,
    selection: 'HOME',
    stake: 10,
    acceptedOdds: updated ? 1.9 : 1.82,
    potentialReturn: updated ? 19 : 18.2,
    netProfit: updated ? 9 : 8.2,
    ruleVersion: updated ? 'elo-davidson-v2' : 'elo-davidson-v1',
    closeDeadline: '2026-09-15T12:00:00Z',
    state: 'PENDING',
    acceptedAt: '2026-09-14T12:02:00Z',
  };
}

function observeBrowser(page: Page): BrowserEvidence {
  const evidence: BrowserEvidence = { requests: [], apiResponses: [], consoleMessages: [] };
  page.on('request', (request: Request) => {
    evidence.requests.push({
      url: request.url(),
      method: request.method(),
      headers: request.headers(),
      body: request.postData(),
    });
  });
  page.on('response', (response) => {
    if (response.url().includes('/api/')) {
      void response.text().then((body) => evidence.apiResponses.push(body)).catch(() => undefined);
    }
  });
  page.on('console', (message) => evidence.consoleMessages.push(message.text()));
  return evidence;
}

async function mockPublicMarket(page: Page, version: () => number = () => 1): Promise<void> {
  await page.route(`**/api/v1/public/odds/markets/${MARKET_ID}`, async (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(publicPayload(version())),
    }),
  );
}

async function mockSession(page: Page, authenticated: boolean): Promise<void> {
  await page.route('**/api/v1/session', async (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        authenticated,
        displayName: authenticated ? 'Participante' : null,
        idleTimeout: 'PT8H',
        absoluteTimeout: null,
        lastValidActivityAt: null,
      }),
    }),
  );
}

async function mockEnrollment(page: Page, status: 'ATIVO' | 'NAO_REGISTRADA'): Promise<void> {
  await page.route('**/api/v1/enrollment/me', async (route) =>
    route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        status,
        createdAt: status === 'ATIVO' ? '2026-09-13T12:00:00Z' : null,
      }),
    }),
  );
}

async function mockCredits(page: Page, status = 200, balance = 1000): Promise<void> {
  await page.route('**/api/v1/me/credits', async (route) =>
    route.fulfill({
      status,
      contentType: status === 200 ? 'application/json' : 'application/problem+json',
      body: JSON.stringify(
        status === 200
          ? { balance, scale: 2, minimumStake: 10, rulesVersion: 'credits-v1' }
          : { code: 'BETUFU-403-FORBIDDEN', title: 'Palpite indisponivel' },
      ),
    }),
  );
}

async function assertPrivateAndLocal(page: Page, evidence: BrowserEvidence): Promise<void> {
  await page.waitForLoadState('networkidle');
  const body = await page.locator('body').innerText();
  const storage = await page.evaluate(() => ({
    local: { ...localStorage },
    session: { ...sessionStorage },
  }));
  const requestBodies = evidence.requests.map((request) => request.body ?? '').join('\n');
  const forbidden = /12345678|student@example|identity-private|hmac-secret|bearer-secret/iu;

  expect(body).not.toMatch(forbidden);
  expect(JSON.stringify(storage)).not.toMatch(forbidden);
  expect(requestBodies).not.toMatch(forbidden);
  expect(evidence.apiResponses.join('\n')).not.toMatch(forbidden);
  expect(evidence.consoleMessages.join('\n')).not.toMatch(forbidden);
  expect(evidence.requests.some((request) => 'authorization' in request.headers)).toBe(false);
  expect(
    evidence.requests.every((request) => /^(http:\/\/127\.0\.0\.1:4200|data:|blob:)/u.test(request.url)),
  ).toBe(true);
}

test.describe('Phase 5 prediction pilot', () => {
  for (const viewport of [
    { name: 'desktop', width: 1280, height: 800 },
    { name: '320px', width: 320, height: 800 },
  ]) {
    test(`orienta visitante sem consultar dados protegidos em ${viewport.name}`, async ({ page }) => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      const evidence = observeBrowser(page);
      await mockPublicMarket(page);
      await mockSession(page, false);

      await page.goto(`/odds/mercados/${MARKET_ID}`);

      await expect(page.getByRole('heading', { level: 3, name: 'Entre para palpitar' })).toBeVisible();
      await expect(page.getByRole('link', { name: 'Entrar para palpitar' })).toBeVisible();
      expect(evidence.requests.some((request) => request.url.includes('/me/credits'))).toBe(false);
      expect(evidence.requests.some((request) => request.url.includes('/me/predictions'))).toBe(false);
      const layout = await page.evaluate(() => ({
        innerWidth: window.innerWidth,
        documentWidth: document.documentElement.scrollWidth,
        ticketWidth:
          document
            .querySelector<HTMLElement>('app-public-market-bet-ticket')
            ?.getBoundingClientRect().width ?? 0,
        offenders: Array.from(document.querySelectorAll<HTMLElement>('body *'))
          .map((element) => ({
            tag: element.tagName,
            className: element.className,
            left: element.getBoundingClientRect().left,
            right: element.getBoundingClientRect().right,
            width: element.getBoundingClientRect().width,
          }))
          .filter((element) => element.right > window.innerWidth + 0.5 || element.left < -0.5)
          .slice(0, 10),
      }));
      expect(layout.innerWidth).toBe(viewport.width);
      expect(layout.documentWidth, JSON.stringify(layout.offenders)).toBeLessThanOrEqual(
        layout.innerWidth,
      );
      expect(layout.ticketWidth).toBeLessThanOrEqual(layout.innerWidth);
      await assertPrivateAndLocal(page, evidence);
    });

    test(`envia HOME e recupera o comprovante proprio em ${viewport.name}`, async ({ page }) => {
      await page.setViewportSize({ width: viewport.width, height: viewport.height });
      const evidence = observeBrowser(page);
      let ownTicket: ReturnType<typeof ticket> | null = null;
      await page.context().addCookies([
        { name: 'XSRF-TOKEN', value: 'xsrf-fixture', url: 'http://127.0.0.1:4200' },
      ]);
      await mockPublicMarket(page);
      await mockSession(page, true);
      await mockEnrollment(page, 'ATIVO');
      await mockCredits(page);
      await page.route(`**/api/v1/me/predictions/markets/${MARKET_ID}`, async (route) =>
        ownTicket
          ? route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify(ownTicket) })
          : route.fulfill({ status: 404, contentType: 'application/problem+json', body: '{}' }),
      );
      await page.route('**/api/v1/me/predictions', async (route) => {
        ownTicket = ticket();
        await route.fulfill({
          status: 201,
          contentType: 'application/json',
          body: JSON.stringify(ownTicket),
        });
      });

      await page.goto(`/odds/mercados/${MARKET_ID}`);
      await page.getByLabel('Mandante').check();
      await page.getByLabel('Creditos do palpite').fill('10');
      await page.getByRole('button', { name: 'Enviar palpite' }).click();

      await expect(page.getByRole('heading', { level: 3, name: 'Palpite registrado' })).toBeVisible();
      const post = evidence.requests.find(
        (request) => request.method === 'POST' && request.url.endsWith('/api/v1/me/predictions'),
      );
      expect(post).toBeDefined();
      expect(JSON.parse(post?.body ?? '{}')).toEqual({
        marketId: MARKET_ID,
        marketVersionId: MARKET_VERSION_1,
        publicationId: PUBLICATION_1,
        ruleVersion: 'elo-davidson-v1',
        selection: 'HOME',
        stake: 10,
      });
      expect(post?.headers['idempotency-key']).toBeTruthy();
      expect(post?.headers['x-xsrf-token']).toBe('xsrf-fixture');

      await page.reload();
      await expect(page.getByRole('heading', { level: 3, name: 'Palpite registrado' })).toBeVisible();
      await expect(page.getByLabel('Palpite neste mercado').getByText('Mandante')).toBeVisible();
      const layout = await page.evaluate(() => ({
        innerWidth: window.innerWidth,
        documentWidth: document.documentElement.scrollWidth,
      }));
      expect(layout.documentWidth).toBeLessThanOrEqual(layout.innerWidth);
      await assertPrivateAndLocal(page, evidence);
    });
  }

  test('conta sem vinculo recebe orientacao e nenhum saldo', async ({ page }) => {
    const evidence = observeBrowser(page);
    await mockPublicMarket(page);
    await mockSession(page, true);
    await mockEnrollment(page, 'NAO_REGISTRADA');

    await page.goto(`/odds/mercados/${MARKET_ID}`);

    await expect(page.getByRole('heading', { level: 3, name: 'Vinculo necessario' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Registrar matricula' })).toBeVisible();
    expect(evidence.requests.some((request) => request.url.includes('/me/credits'))).toBe(false);
    await assertPrivateAndLocal(page, evidence);
  });

  test('409 atualiza por GET e exige reconfirmacao antes de um novo POST', async ({ page }) => {
    const evidence = observeBrowser(page);
    let publicVersion = 1;
    let postCount = 0;
    await mockPublicMarket(page, () => publicVersion);
    await mockSession(page, true);
    await mockEnrollment(page, 'ATIVO');
    await mockCredits(page);
    await page.route(`**/api/v1/me/predictions/markets/${MARKET_ID}`, async (route) =>
      route.fulfill({ status: 404, contentType: 'application/problem+json', body: '{}' }),
    );
    await page.route('**/api/v1/me/predictions', async (route) => {
      postCount += 1;
      if (postCount === 1) {
        publicVersion = 2;
        await route.fulfill({
          status: 409,
          contentType: 'application/problem+json',
          body: JSON.stringify({ code: 'BETUFU-409-STALE_VERSION' }),
        });
        return;
      }
      await route.fulfill({ status: 201, contentType: 'application/json', body: JSON.stringify(ticket(2)) });
    });

    await page.goto(`/odds/mercados/${MARKET_ID}`);
    await page.getByLabel('Mandante').check();
    await page.getByLabel('Creditos do palpite').fill('10');
    await page.getByRole('button', { name: 'Enviar palpite' }).click();

    await expect(page.getByRole('heading', { level: 3, name: 'A odd mudou antes do envio' })).toBeVisible();
    await expect(page.getByText('elo-davidson-v2', { exact: true })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Enviar palpite' })).toBeDisabled();
    expect(postCount).toBe(1);

    await page.getByLabel('Confirmo a nova odd publicada').check();
    await page.getByRole('button', { name: 'Enviar palpite' }).click();
    await expect(page.getByRole('heading', { level: 3, name: 'Palpite registrado' })).toBeVisible();
    expect(postCount).toBe(2);

    const posts = evidence.requests.filter(
      (request) => request.method === 'POST' && request.url.endsWith('/api/v1/me/predictions'),
    );
    expect(JSON.parse(posts[1].body ?? '{}')).toMatchObject({
      marketVersionId: MARKET_VERSION_2,
      publicationId: PUBLICATION_2,
      ruleVersion: 'elo-davidson-v2',
      selection: 'HOME',
    });
    expect(posts[1].headers['idempotency-key']).not.toBe(posts[0].headers['idempotency-key']);
    await assertPrivateAndLocal(page, evidence);
  });

  test('403 vira bloqueio generico sem papel, escopo ou capability', async ({ page }) => {
    const evidence = observeBrowser(page);
    await mockPublicMarket(page);
    await mockSession(page, true);
    await mockEnrollment(page, 'ATIVO');
    await mockCredits(page, 403);
    await page.route(`**/api/v1/me/predictions/markets/${MARKET_ID}`, async (route) =>
      route.fulfill({ status: 404, contentType: 'application/problem+json', body: '{}' }),
    );

    await page.goto(`/odds/mercados/${MARKET_ID}`);

    await expect(page.getByRole('heading', { level: 3, name: 'Palpite indisponivel' })).toBeVisible();
    const body = await page.locator('body').innerText();
    expect(body).not.toMatch(/administrador|role|scope|capability/iu);
    await assertPrivateAndLocal(page, evidence);
  });
});
