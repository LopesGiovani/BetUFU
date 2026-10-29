import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';

test.use({ actionTimeout: 15_000 });

const PAUSES = '/api/v1/operations/emergency-pauses';
function setting(name: string) {
  const value = process.env[name];
  if (!value) throw new Error(`Missing isolated ${name}`);
  return value;
}
async function login(page: Page, prefix: string) {
  await page.goto('/');
  await page.locator('a.arena-nav__login:visible').click();
  await page.locator('#username').fill(setting(`${prefix}_USERNAME`));
  await page.locator('#password').fill(setting(`${prefix}_PASSWORD`));
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page.locator('a.arena-nav__account:visible')).toBeVisible();
}
async function api(page: Page, path: string, method = 'GET', data?: unknown, expected = 200) {
  const csrf = (await page.context().cookies()).find((c) => c.name === 'XSRF-TOKEN');
  const response = await page.request.fetch(path, {
    method, data, headers: { Origin: 'http://localhost:4300',
      ...(csrf ? { 'X-XSRF-TOKEN': decodeURIComponent(csrf.value) } : {}),
      'Idempotency-Key': randomUUID() },
  });
  expect(response.status(), `${method} ${path}`).toBe(expected);
  return response.json();
}
async function confirm(admin: Page) {
  const dialog = admin.getByRole('dialog');
  await expect(dialog).toBeVisible();
  await dialog.getByLabel('Motivo da operacao').fill('Contencao operacional isolada');
  const response = admin.waitForResponse((r) => r.url().endsWith(PAUSES) && r.request().method() === 'POST');
  await dialog.getByRole('button', { name: 'Confirmar operacao', exact: true }).click();
  expect((await response).status()).toBe(201);
  await expect(dialog).not.toBeVisible();
}
async function pause(admin: Page, kind: string, id: string) {
  await admin.getByLabel('Alvo da pausa').selectOption(kind);
  await admin.getByLabel('Identificador do alvo').fill(id);
  await admin.getByRole('button', { name: 'Pausar novos envios', exact: true }).click();
  await confirm(admin);
  const facts = await api(admin, PAUSES);
  const fact = facts.find((f: { targetId: string; kind: string }) => f.kind === kind && f.targetId === id);
  expect(fact).toBeTruthy();
  return fact.factId as string;
}
async function resume(admin: Page, factId: string) {
  await admin.locator(`[data-pause-id="${factId}"]`).getByRole('button', { name: 'Retomar novos envios' }).click();
  await confirm(admin);
  await expect(admin.locator(`[data-pause-id="${factId}"]`)).toHaveCount(0);
}

test('four real pause targets preserve history, receipts and accepted settlement; only administrator commands', async ({ page, browser }, info) => {
  const contexts: BrowserContext[] = [];
  const errors: string[] = [];
  const administrativeCalls: string[] = [];
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('request', (request) => { if (request.url().includes(PAUSES)) administrativeCalls.push(request.url()); });
  try {
    await login(page, 'BETUFU_E2E');
    const pages: Page[] = [];
    for (const prefix of ['BETUFU_E2E_ADMIN', 'BETUFU_E2E_MODERATOR', 'BETUFU_E2E_CHECKER']) {
      const context = await browser.newContext({ baseURL: 'http://localhost:4300', viewport: info.project.use.viewport, locale: 'pt-BR' });
      contexts.push(context);
      const other = await context.newPage();
      other.on('pageerror', (error) => errors.push(error.message));
      await login(other, prefix);
      pages.push(other);
    }
    const [admin, moderator, checker] = pages;
    const agenda = await api(page, '/api/v1/public/sports/events');
    const eventId = agenda.items[info.project.name === 'phase7-320px' ? 1 : 0].eventId;
    await page.getByRole('button', { name: /Próximos/u }).click();
    await page.locator(`a[href="/eventos/${eventId}"]`).first().click({ force: true });
    const odds = page.getByRole('link', { name: 'Ver odds da partida', exact: true });
    const marketId = (await odds.getAttribute('href'))!.split('/').at(-1)!;
    await odds.click();
    const stake = page.getByLabel('Creditos do palpite', { exact: true });
    await expect(stake).toBeEnabled();
    const publicMarket = await api(page, `/api/v1/public/odds/markets/${marketId}`);
    const prediction = { marketId, marketVersionId: publicMarket.marketVersionId,
      publicationId: publicMarket.current.publicationId, ruleVersion: publicMarket.explanation.algorithmVersion,
      selection: 'HOME', stake: 10 };
    await admin.goto('/operacao/painel');
    await admin.getByRole('link', { name: 'Pausas de emergencia', exact: true }).click();
    await expect(admin.locator('main')).toHaveCount(1);
    await expect(admin.getByText('Nenhuma pausa ativa.', { exact: true })).toBeVisible();
    await moderator.goto('/operacao/pausas');
    await expect(moderator.getByRole('alert')).toHaveText('Somente o administrador geral pode gerenciar pausas.');
    await expect(moderator.getByRole('button', { name: 'Pausar novos envios', exact: true })).toHaveCount(0);
    await api(moderator, PAUSES, 'POST', { clientCommandId: randomUUID(), kind: 'MARKET', targetId: marketId, action: 'PAUSE', reason: 'forbidden' }, 403);

    // A canceled native dialog must neither mutate nor lose the triggering focus.
    await admin.getByLabel('Identificador do alvo').fill(marketId);
    const trigger = admin.getByRole('button', { name: 'Pausar novos envios', exact: true });
    await trigger.focus(); await admin.keyboard.press('Enter');
    await expect(admin.getByRole('dialog')).toBeVisible();
    await admin.getByRole('dialog').getByLabel('Motivo da operacao').fill('Cancelamento sem efeito');
    await admin.getByRole('dialog').getByLabel('Motivo da operacao').focus();
    await admin.keyboard.press('Shift+Tab');
    await expect(admin.getByRole('dialog').getByRole('button', { name: 'Confirmar operacao', exact: true })).toBeFocused();
    await admin.keyboard.press('Tab');
    await expect(admin.getByRole('dialog').getByLabel('Motivo da operacao')).toBeFocused();
    await admin.keyboard.press('Escape');
    await expect(admin.getByRole('dialog')).not.toBeVisible();
    await expect(trigger).toBeFocused();
    expect(await api(admin, PAUSES)).toEqual([]);

    const marketPause = await pause(admin, 'MARKET', marketId);
    await expect(stake).toBeDisabled();
    await expect(page.getByText('Novos palpites pausados temporariamente.', { exact: true })).toBeVisible();
    await api(page, '/api/v1/me/predictions', 'POST', prediction, 403);
    await page.reload();
    await expect(stake).toBeDisabled();
    await resume(admin, marketPause);
    await expect(stake).toBeEnabled();

    const chat = await page.context().newPage();
    chat.on('pageerror', (error) => errors.push(error.message));
    chat.on('request', (request) => { if (request.url().includes(PAUSES)) administrativeCalls.push(request.url()); });
    await chat.goto(`/eventos/${eventId}`);
    await chat.getByRole('heading', { name: 'Chat da partida', exact: true }).scrollIntoViewIfNeeded();
    const composer = chat.locator('textarea');
    await expect(chat.locator('.chat-connection')).toContainText('Ao vivo');
    await expect(composer).toBeEnabled();
    const historical = (await api(chat, `/api/v1/chat/events/${eventId}`)).messages;
    expect(historical.length).toBeGreaterThan(0);
    const eventPause = await pause(admin, 'EVENT', eventId);
    await expect(stake).toBeDisabled();
    expect((await api(chat, `/api/v1/chat/events/${eventId}/availability`)).messagesPaused).toBe(false);
    await expect(composer).toBeEnabled();
    const channelId = JSON.parse(setting('BETUFU_E2E_CHANNELS'))[eventId];
    expect(channelId).toMatch(/^[a-f0-9-]{36}$/u);
    const channelPause = await pause(admin, 'CHANNEL', channelId);
    await expect(composer).toBeDisabled();
    await expect(chat.getByText('Novos envios pausados temporariamente.', { exact: true })).toBeVisible();
    expect((await api(chat, `/api/v1/chat/events/${eventId}`)).messages).toEqual(historical);
    await api(chat, `/api/v1/chat/events/${eventId}/messages`, 'POST', { text: 'Bloqueado pelo canal' }, 403);
    await resume(admin, channelPause);
    await expect(composer).toBeEnabled();
    await resume(admin, eventPause);
    await expect(stake).toBeEnabled();

    await page.getByLabel('Mandante').check();
    await stake.fill('10');
    const accepted = page.waitForResponse((r) => r.url().endsWith('/api/v1/me/predictions') && r.request().method() === 'POST');
    await page.getByRole('button', { name: 'Enviar palpite', exact: true }).click();
    expect((await accepted).status()).toBe(201);
    await expect(page.getByRole('heading', { name: 'Palpite registrado', exact: true })).toBeVisible();
    const receipt = await api(page, `/api/v1/me/predictions/markets/${marketId}`);
    const credits = await api(page, '/api/v1/me/credits');
    const accountPause = await pause(admin, 'ACCOUNT', setting('BETUFU_E2E_STUDENT_ID'));
    await expect(composer).toBeDisabled();
    await expect(page.getByText('Novos palpites pausados temporariamente.', { exact: true })).toBeVisible();
    await expect(page.getByRole('heading', { name: 'Palpite registrado', exact: true })).toBeVisible();
    expect(await api(page, '/api/v1/me/credits')).toEqual(credits);
    expect(await api(page, `/api/v1/me/predictions/markets/${marketId}`)).toEqual(receipt);
    const restrictions = await page.context().newPage();
    await restrictions.goto('/conta/punicoes');
    await expect(restrictions.getByRole('heading', { name: 'Punicoes da conta', exact: true })).toBeVisible();

    const pausedMarket = await pause(admin, 'MARKET', marketId);
    const pausedEvent = await pause(admin, 'EVENT', eventId);
    const event = await api(admin, `/api/v1/operations/sports/events/${eventId}`);
    await api(admin, `/api/v1/operations/sports/events/${eventId}/results/confirm?expectedLockVersion=${event.lockVersion}`, 'POST', {
      scores: [{ slotNo: 1, score: 3 }, { slotNo: 2, score: 0 }], sourceReference: 'Real isolated paused settlement',
    });
    const candidates = await api(checker, '/api/v1/operations/settlements/candidates');
    const candidate = candidates.items.find((item: { eventId: string }) => item.eventId === eventId);
    expect(candidate).toBeTruthy();
    const settlement = await api(checker, `/api/v1/operations/settlements/events/${eventId}/authorize`, 'POST', {
      expectedOutcomeFactId: candidate.outcomeFactId, expectedConfirmedVersionId: candidate.confirmedVersionId,
    }, 201);
    expect(settlement.caseProgress.status).toBe('COMPLETE');
    const history = await api(page, '/api/v1/me/settlements');
    expect(history.items.some((item: { marketId: string; processingStatus: string }) => item.marketId === marketId && item.processingStatus === 'COMPLETE')).toBe(true);
    expect((await api(page, '/api/v1/me/credits')).balance).toBeGreaterThan(credits.balance);
    await resume(admin, pausedEvent); await resume(admin, pausedMarket); await resume(admin, accountPause);
    await page.reload();
    await expect(page.getByRole('heading', { name: 'Palpite registrado', exact: true })).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    expect(await admin.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    expect(administrativeCalls).toEqual([]);
    expect(errors).toEqual([]);
    const visitor = await browser.newContext({ baseURL: 'http://localhost:4300' }); contexts.push(visitor);
    const anonymous = await visitor.newPage();
    await api(anonymous, '/api/v1/chat/global/availability', 'GET', undefined, 401);
  } finally {
    await Promise.all(contexts.map((context) => context.close()));
  }
});
