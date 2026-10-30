import { handleIntegratedRules } from './phase7.setup';
import { expect, test, type Page } from '@playwright/test';
import { execFile } from 'node:child_process';
import { resolve } from 'node:path';
import { promisify } from 'node:util';
import { randomUUID } from 'node:crypto';

function setting(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`Missing isolated setting ${name}`);
  return value;
}

async function login(page: Page, prefix = 'BETUFU_E2E'): Promise<void> {
  if (process.env['BETUFU_E2E_INTEGRATED'] === 'true' && prefix === 'BETUFU_E2E') prefix = 'BETUFU_E2E_READER';
  await handleIntegratedRules(page);
  await page.goto('/');
  await page.locator('a.arena-nav__login:visible').click();
  await page.getByLabel('Endereço de email').fill(setting(`${prefix}_USERNAME`));
  await page
    .getByRole('textbox', { name: 'Senha', exact: true })
    .fill(setting(`${prefix}_PASSWORD`));
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page.locator('a.arena-nav__account:visible')).toBeVisible();
}

async function read(page: Page, path: string) {
  const response = await page.context().request.get(path);
  expect(response.status()).toBe(200);
  return response.json();
}

async function post(page: Page, path: string, text: string): Promise<void> {
  const csrf = (await page.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN');
  expect(csrf).toBeDefined();
  const response = await page.context().request.post(path, {
    headers: {
      Origin: new URL(page.url()).origin,
      'X-XSRF-TOKEN': csrf!.value,
      'Idempotency-Key': randomUUID(),
    },
    data: { text },
  });
  expect(response.status()).toBe(201);
}

test('chat real: descoberta, historico, STOMP, reconexao e logout em cada viewport', async ({
  page,
  browser,
  baseURL,
}, testInfo) => {
  test.setTimeout(180_000);
  const faults: string[] = [];
  const sockets: import('@playwright/test').WebSocket[] = [];
  const closedSockets = new Set<import('@playwright/test').WebSocket>();
  page.on('websocket', (socket) => {
    sockets.push(socket);
    socket.on('socketerror', (error) => console.log('Chat WebSocket:', error));
    socket.on('close', () => closedSockets.add(socket));
  });
  page.on('pageerror', (error) => faults.push(error.name));
  await page.goto('/');
  const link = page.locator('a[href="/comunidade"]:visible');
  await expect(link).toHaveCount(1);
  await link.focus();
  await page.keyboard.press('Enter');
  await expect(page).toHaveURL(/\/comunidade$/u);
  await expect(page.getByRole('heading', { name: 'Comunidade', exact: true })).toBeVisible();
  await expect(page.locator('a.chat-login')).toHaveAttribute(
    'href',
    '/login/start?returnTo=%2Fcomunidade',
  );
  const privateRead = await page.context().request.get('/api/v1/chat/global');
  expect(privateRead.status()).toBe(401);
  expect(await privateRead.text()).not.toContain('Historico');

  const agenda = await read(page, '/api/v1/public/sports/events');
  const eventId = agenda.items[testInfo.project.name === 'phase7-320px' ? 1 : 0].eventId;
  const eventPath = `/api/v1/chat/events/${eventId}`;
  const event = await read(page, eventPath);
  expect(event.messages).toHaveLength(50);
  expect(event.historyCursor).toBeTruthy();
  await page.goto('/');
  await page.getByRole('button', { name: /Próximos/u }).click();
  const eventLink = page.locator(`a[href="/eventos/${eventId}"]`).first();
  await eventLink.waitFor({ state: 'visible', timeout: 15_000 });
  await eventLink.click({ timeout: 15_000, force: true });
  await page
    .getByRole('heading', { name: 'Chat da partida', exact: true })
    .scrollIntoViewIfNeeded();
  const rows = page.locator('.chat-message');
  await expect(rows).toHaveCount(50);
  await expect(page.locator('.chat-connection')).toHaveText('Ao vivo');
  expect(await page.locator('.chat-message img, .chat-message script').count()).toBe(0);
  await expect(page.locator('.chat-message').last()).toContainText('<img');
  await expect(page.getByRole('link', { name: 'Entrar na conta', exact: true })).toHaveAttribute(
    'href',
    `/login/start?returnTo=${encodeURIComponent(`/eventos/${eventId}`)}`,
  );

  const viewport = page.locator('.chat-history');
  await viewport.evaluate((element) => {
    element.scrollTop = 250;
  });
  const anchor = await rows.first().getAttribute('data-message-id');
  const before = await rows.first().boundingBox();
  const beforeViewport = await viewport.boundingBox();
  await page.getByRole('button', { name: 'Carregar mensagens anteriores', exact: true }).click();
  await expect(rows).toHaveCount(60);
  const after = await page.locator(`[data-message-id="${anchor}"]`).boundingBox();
  const afterViewport = await viewport.boundingBox();
  expect(Math.abs(after!.y - afterViewport!.y - (before!.y - beforeViewport!.y))).toBeLessThan(3);
  await expect(
    page.getByRole('button', { name: 'Carregar mensagens anteriores' }),
  ).not.toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await expect(page.locator('main')).toHaveCount(1);

  const senderContext = await browser.newContext({ baseURL });
  try {
    const sender = await senderContext.newPage();
    await login(sender, 'BETUFU_E2E_OTHER');
    await viewport.focus();
    await viewport.evaluate((element) => {
      element.scrollTop = 100;
    });
    const oldTop = await viewport.evaluate((element) => element.scrollTop);
    const pushRead = page.waitForResponse(
      (response) => response.request().method() === 'GET' && response.url().includes(eventPath),
      { timeout: 15_000 },
    );
    const liveText = `Ao vivo ${randomUUID()}`;
    await post(sender, `${eventPath}/messages`, liveText);
    await pushRead;
    await expect(rows).toHaveCount(61);
    await expect(page.getByRole('button', { name: /Novas mensagens/u })).toBeVisible();
    expect(await viewport.evaluate((element) => element.scrollTop)).toBeCloseTo(oldTop, 0);
    await expect(viewport).toBeFocused();
    await page.getByRole('button', { name: /Novas mensagens/u }).click();
    expect(
      await viewport.evaluate(
        (element) => element.scrollHeight - element.clientHeight - element.scrollTop,
      ),
    ).toBeLessThan(3);

    // A real offline interval closes the browser socket. Reconnect must rebuild the read.
    await page.context().setOffline(true);
    await expect(page.locator('.chat-connection')).not.toHaveText('Ao vivo');
    const restoredHistory = page.waitForResponse(
      (response) =>
        response.request().method() === 'GET' &&
        response.url().includes(eventPath) &&
        response.url().includes('historyCursor='),
      { timeout: 15_000 },
    );
    await page.context().setOffline(false);
    await expect(page.locator('.chat-connection')).toHaveText('Ao vivo');
    expect((await restoredHistory).status()).toBe(200);
    await expect(page.locator('.chat-panel')).toHaveAttribute('aria-busy', 'false');
    await expect(rows.last()).toContainText(liveText);
    const reads: string[] = [];
    const onRead = (request: import('@playwright/test').Request) => {
      if (request.url().includes(eventPath) && request.method() === 'GET')
        reads.push(request.url());
    };
    page.on('request', onRead);
    await page.waitForTimeout(3500);
    page.off('request', onRead);
    expect(reads).toHaveLength(0);

    await login(page);
    if (process.env['BETUFU_E2E_INTEGRATED'] === 'true')
      await post(page, '/api/v1/chat/global/messages', `Leitor isolado ${randomUUID()}`);
    await page.locator('a[href="/comunidade"]:visible').click();
    await expect(page.locator('.chat-message')).toHaveCount(50);
    await expect(page.locator('.chat-own').first()).toContainText('Você');
    await expect(page.locator('.chat-connection')).toHaveText('Ao vivo');
    await page.reload();
    await expect(page.locator('.chat-message')).toHaveCount(50);
    await expect(page.locator('.chat-own').first()).toContainText('Você');

    // Current inactive enrollment can still read; this operation affects only owned fixtures.
    await promisify(execFile)(
      process.execPath,
      [
        resolve(process.cwd(), '../scripts/phase7-e2e.mjs'),
        process.env['BETUFU_E2E_INTEGRATED'] === 'true' ? 'release-reader' : 'release-student',
        setting('BETUFU_E2E_ISOLATED_RUN'),
      ],
      { windowsHide: true },
    );
    await page.reload();
    await expect(page.locator('.chat-message')).toHaveCount(50);
    const logoutRead = await page.context().request.get('/api/v1/chat/global');
    expect(logoutRead.status()).toBe(200);
    await page.locator('button.arena-nav__logout:visible').click();
    await page.goto('/comunidade');
    await expect(page.locator('.chat-message')).toHaveCount(0);
    await expect(page.locator('a.chat-login')).toBeVisible();
    expect((await page.context().request.get('/api/v1/chat/global')).status()).toBe(401);
    await expect.poll(() => closedSockets.size).toBeGreaterThan(0);
    const closedSocketCount = sockets.length;
    await page.waitForTimeout(1500);
    expect(sockets).toHaveLength(closedSocketCount);
    await expect(page.locator('main')).toHaveCount(1);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    expect(faults).toHaveLength(0);
  } finally {
    await senderContext.close();
  }
});
