import { chromium, expect, type BrowserContext, type Page } from '@playwright/test';
import { execFile } from 'node:child_process';
import { resolve } from 'node:path';
import { promisify } from 'node:util';

function required(key: string) {
  const value = process.env[key];
  if (!value) throw new Error(`Missing isolated setting ${key}`);
  return value;
}

async function login(context: BrowserContext, prefix: string): Promise<Page> {
  const page = await context.newPage();
  const operational = prefix === 'BETUFU_E2E_ADMIN' || prefix === 'BETUFU_E2E_CHECKER';
  await page.goto(operational ? '/operacao/esportes' : '/');
  if (operational)
    await page.getByRole('link', { name: 'Entrar com a conta UFU', exact: true }).click();
  else await page.locator('a.arena-nav__login:visible').click();
  await page.getByLabel('Endereço de email').fill(required(`${prefix}_USERNAME`));
  await page
    .getByRole('textbox', { name: 'Senha', exact: true })
    .fill(required(`${prefix}_PASSWORD`));
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  if (operational)
    await expect(
      page.getByRole('link', { name: 'Resultados para autorizar', exact: true }),
    ).toBeVisible();
  else await expect(page.locator('a.arena-nav__account:visible')).toBeVisible();
  return page;
}

async function api(
  context: BrowserContext,
  path: string,
  method = 'GET',
  data?: unknown,
  expected = 200,
  idempotencyKey = `phase6-${crypto.randomUUID()}`,
) {
  const cookies = await context.cookies();
  const csrf = cookies.find((cookie) => cookie.name === 'XSRF-TOKEN');
  const response = await context.request.fetch(path, {
    method,
    data,
    headers: {
      Origin: required('BETUFU_E2E_BASE_URL'),
      ...(csrf ? { 'X-XSRF-TOKEN': decodeURIComponent(csrf.value) } : {}),
      'Idempotency-Key': idempotencyKey,
    },
  });
  expect(response.status(), `${method} ${path}`).toBe(expected);
  return response.json();
}

async function result(admin: BrowserContext, eventId: string, correction = false) {
  const event = await api(admin, `/api/v1/operations/sports/events/${eventId}`);
  const scores = [
    { slotNo: 1, score: correction ? 0 : 3 },
    { slotNo: 2, score: correction ? 3 : 0 },
  ];
  return api(
    admin,
    `/api/v1/operations/sports/events/${eventId}/results/${correction ? 'correct' : 'confirm'}?expectedLockVersion=${event.lockVersion}`,
    'POST',
    {
      scores,
      sourceReference: 'Isolated certification result',
      ...(correction ? { reason: 'Isolated certification corrected score' } : {}),
    },
  );
}

async function settle(admin: BrowserContext, eventId: string) {
  const candidates = await api(admin, '/api/v1/operations/settlements/candidates');
  const candidate = candidates.items.find((item: { eventId: string }) => item.eventId === eventId);
  expect(candidate).toBeTruthy();
  const settled = await api(
    admin,
    `/api/v1/operations/settlements/events/${eventId}/authorize`,
    'POST',
    {
      expectedOutcomeFactId: candidate.outcomeFactId,
      expectedConfirmedVersionId: candidate.confirmedVersionId,
    },
    201,
  );
  expect(settled.caseProgress.status).toBe('COMPLETE');
}

export default async function setup() {
  if (
    !/^betufu-phase6-e2e-[a-f0-9]{16}$/u.test(required('BETUFU_E2E_ISOLATED_RUN')) ||
    required('BETUFU_E2E_BASE_URL') !== 'http://localhost:4300'
  )
    throw new Error('Real fixture requires owned isolated stack');
  const browser = await chromium.launch({
    channel: 'chrome',
    args: ['--host-resolver-rules=MAP localhost 127.0.0.1'],
  });
  try {
    const options = { baseURL: required('BETUFU_E2E_BASE_URL'), locale: 'pt-BR' };
    const student = await browser.newContext(options);
    const athlete = await browser.newContext(options);
    const admin = await browser.newContext(options);
    const checker = await browser.newContext(options);
    const page = await login(student, 'BETUFU_E2E');
    for (const width of [1280, 320]) {
      await page.setViewportSize({ width, height: 800 });
      await page.goto('/');
      await page.locator('a[href="/ranking"]:visible').click();
      await expect(
        page.getByText('Ainda nao ha palpites com resultado definitivo para classificar.'),
      ).toBeVisible();
      expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
        true,
      );
    }
    console.log('[phase6-e2e] Real empty ranking verified before producing results.');
    await login(athlete, 'BETUFU_E2E_OTHER');
    const adminPage = await login(admin, 'BETUFU_E2E_ADMIN');
    await login(checker, 'BETUFU_E2E_CHECKER');
    const events = (await api(student, '/api/v1/public/sports/events')).items;
    const open = [];
    for (const summary of events) {
      const detail = await api(student, `/api/v1/public/sports/events/${summary.eventId}`);
      if (!detail.marketId) continue;
      const market = await api(student, `/api/v1/public/odds/markets/${detail.marketId}`);
      if (market.state === 'OPEN' && Date.parse(market.closeDeadline) > Date.now())
        open.push({ detail, market });
    }
    expect(open.length).toBeGreaterThanOrEqual(3);
    const first = open[0];
    const second = open[1];
    const spend = open[2];
    const team = await api(student, `/api/v1/public/sports/teams/${first.detail.slots[0].teamId}`);
    await api(student, '/api/v1/profile/me/favorite-organization', 'PUT', {
      organizationId: team.organizationId,
    });
    const predict = (context: BrowserContext, market: typeof first.market, stake: number) =>
      api(
        context,
        '/api/v1/me/predictions',
        'POST',
        {
          marketId: market.marketId,
          marketVersionId: market.marketVersionId,
          publicationId: market.current.publicationId,
          ruleVersion: market.explanation.algorithmVersion,
          selection: 'HOME',
          stake,
        },
        201,
      );
    await predict(student, first.market, 50);
    await predict(student, second.market, 50);
    const athleteTicket = await predict(athlete, second.market, 50);
    await result(admin, first.detail.eventId);
    const pending = (await api(admin, '/api/v1/operations/settlements/candidates')).items.find(
      (item: { eventId: string }) => item.eventId === first.detail.eventId,
    );
    await api(
      admin,
      `/api/v1/operations/settlements/events/${first.detail.eventId}/authorize`,
      'POST',
      { expectedOutcomeFactId: pending.outcomeFactId, expectedConfirmedVersionId: null },
      409,
    );
    await api(student, '/api/v1/operations/settlements/candidates', 'GET', undefined, 403);
    // A real visible operation confirms exact version and focus-safe cancellation.
    await adminPage.getByRole('link', { name: 'Resultados para autorizar', exact: true }).click();
    await adminPage
      .getByRole('button', { name: 'Autorizar liquidacao', exact: true })
      .first()
      .click();
    await expect(adminPage.getByRole('dialog')).toBeVisible();
    await adminPage.keyboard.press('Escape');
    await expect(adminPage.getByRole('dialog')).not.toBeVisible();
    await adminPage
      .getByRole('button', { name: 'Autorizar liquidacao', exact: true })
      .first()
      .click();
    const authorizedRequest = adminPage.waitForResponse(
      (response) => response.request().method() === 'POST' && response.url().endsWith('/authorize'),
    );
    await adminPage.getByRole('button', { name: 'Confirmar autorizacao', exact: true }).click();
    await expect(adminPage.getByRole('heading', { name: 'Progresso da liquidacao' })).toBeVisible();
    await expect(
      adminPage.getByText('Concluida: 1 de 1 palpites', { exact: true }).first(),
    ).toBeVisible();
    const authorized = (await authorizedRequest).request();
    const balanceBeforeRetry = await api(student, '/api/v1/me/credits');
    await api(
      admin,
      `/api/v1/operations/settlements/events/${first.detail.eventId}/authorize`,
      'POST',
      authorized.postDataJSON(),
      201,
      authorized.headers()['idempotency-key'],
    );
    expect((await api(student, '/api/v1/me/credits')).balance).toBe(balanceBeforeRetry.balance);
    await result(admin, second.detail.eventId);
    await settle(admin, second.detail.eventId);
    const awards = await api(student, '/api/v1/me/achievements');
    expect(awards.items.some((item: { state: string }) => item.state === 'AWARDED')).toBe(true);
    const available = await api(student, '/api/v1/me/credits');
    await predict(student, spend.market, available.balance);
    await result(admin, first.detail.eventId, true);
    await settle(admin, first.detail.eventId);
    const correctedAwards = await api(student, '/api/v1/me/achievements');
    expect(correctedAwards.items.some((item: { state: string }) => item.state === 'REVOKED')).toBe(
      true,
    );
    const debt = await api(student, '/api/v1/me/credit-corrections');
    expect(debt.items.some((item: { pending: number }) => item.pending > 0)).toBe(true);
    // Independent administrators, genuine OIDC sessions and real CSRF for all writes.
    const owner = await api(athlete, '/api/v1/profile/me');
    const proposal = await api(
      admin,
      '/api/v1/operations/credit-corrections',
      'POST',
      {
        identityId: owner.identityId,
        kind: 'CREDIT',
        reason: 'PROVEN_TECHNICAL_FAILURE',
        amount: 50,
        evidenceType: 'PREDICTION',
        evidenceId: athleteTicket.predictionId,
        description: 'Isolated certification technical correction',
        calculation: 'Return exactly one accepted stake of 50.00',
      },
      201,
    );
    await api(
      admin,
      `/api/v1/operations/credit-corrections/${proposal.caseId}/approve`,
      'POST',
      undefined,
      403,
    );
    const before = await api(athlete, '/api/v1/me/credits');
    await api(checker, `/api/v1/operations/credit-corrections/${proposal.caseId}/approve`, 'POST');
    await api(checker, `/api/v1/operations/credit-corrections/${proposal.caseId}/approve`, 'POST');
    const after = await api(athlete, '/api/v1/me/credits');
    expect(after.balance).toBe(before.balance + 50);
    await promisify(execFile)(
      process.execPath,
      [
        resolve(process.cwd(), '../scripts/phase6-e2e.mjs'),
        'release-athlete',
        required('BETUFU_E2E_ISOLATED_RUN'),
      ],
      { windowsHide: true },
    );
    const ranking = await api(student, '/api/v1/public/ranking/lifetime');
    const inactive = ranking.entries.find(
      (entry: { nickname: string }) => entry.nickname === 'atleta-ufu',
    );
    expect(inactive.verificationActive).toBe(false);
    expect(inactive.wins).toBe(1);
    console.log(
      '[phase6-e2e] Real results, settlement UI, grant/revoke, spent payout recovery and distinct administrator approval verified.',
    );
  } finally {
    await browser.close();
  }
}
