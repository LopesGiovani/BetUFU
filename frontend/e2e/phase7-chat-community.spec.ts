import { expect, test, type Page, type BrowserContext } from '@playwright/test';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { resolve } from 'node:path';

function setting(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`Missing isolated setting ${name}`);
  return value;
}
async function login(page: Page, prefix: string): Promise<void> {
  await page.goto('/');
  await Promise.race([
    page.locator('a.arena-nav__account:visible').waitFor(),
    page.locator('a.arena-nav__login:visible').waitFor(),
  ]);
  if (await page.locator('a.arena-nav__account:visible').isVisible()) return;
  await page.locator('a.arena-nav__login:visible').click();
  await Promise.race([
    page.locator('#username').waitFor(),
    page.locator('a.arena-nav__account:visible').waitFor(),
  ]);
  if (await page.locator('a.arena-nav__account:visible').isVisible()) return;
  await page.locator('#username').fill(setting(`${prefix}_USERNAME`));
  await page.locator('#password').fill(setting(`${prefix}_PASSWORD`));
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page.locator('a.arena-nav__account:visible')).toBeVisible();
}
async function state(context: BrowserContext) {
  const response = await context.request.get('/api/v1/chat/me/mutes');
  expect(response.status()).toBe(200);
  return response.json() as Promise<{ version: number; participantIds: string[] }>;
}
async function resetMutes(context: BrowserContext) {
  let current = await state(context);
  const csrf = (await context.cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN')!;
  for (const id of current.participantIds) {
    const response = await context.request.delete(`/api/v1/chat/me/mutes/${id}`, {
      headers: {
        Origin: 'http://localhost:4300',
        'X-XSRF-TOKEN': csrf.value,
        'If-Match': `"${current.version}"`,
      },
    });
    expect(response.status()).toBe(200);
    current = await response.json();
  }
}

test('real community: private mute push, rollback, cross-channel scope, report and logout', async ({
  page,
  browser,
}, info) => {
  test.setTimeout(180000);
  const contexts: BrowserContext[] = [];
  const faults: string[] = [];
  const watch = (p: Page) => p.on('pageerror', (error) => faults.push(error.message));
  watch(page);
  await login(page, 'BETUFU_E2E');
  await page.goto('/comunidade');
  const publicResponse = await page.context().request.get('/api/v1/chat/global');
  expect(publicResponse.status()).toBe(200);
  const publicBody = await publicResponse.json();
  const target = publicBody.messages.find((m: { removed: boolean }) => !m.removed);
  expect(target).toBeTruthy();
  expect(JSON.stringify(publicBody)).not.toMatch(/reporter|reason|participantIds|reportId/);
  const id = target.messageId as string;
  const text = target.text as string;
  await expect(page.locator(`[data-message-id="${id}"]`)).toContainText(text);

  try {
    for (let n = 0; n < 2; n++) {
      const context = await browser.newContext({
        baseURL: 'http://localhost:4300',
        viewport: info.project.use.viewport,
        locale: 'pt-BR',
      });
      contexts.push(context);
      const p = await context.newPage();
      watch(p);
      await login(p, 'BETUFU_E2E_OTHER');
    }
    const owner = contexts[0].pages()[0];
    const sameOwner = contexts[1].pages()[0];
    await resetMutes(contexts[0]);
    for (const p of [owner, sameOwner]) {
      await p.goto('/comunidade');
      await expect(p.locator(`[data-message-id="${id}"]`)).toContainText(text);
      await expect(p.locator('.chat-connection')).toContainText('Ao vivo');
    }
    const message = owner.locator(`[data-message-id="${id}"]`);
    await message.locator('summary').click();
    const report = message.getByRole('button', { name: 'Denunciar mensagem', exact: true });
    const requests: string[] = [];
    owner.on('request', (req) => {
      if (req.method() === 'POST' && req.url().includes('/reports'))
        requests.push(req.headers()['idempotency-key']);
    });
    await report.click();
    await expect(owner.getByRole('dialog')).toBeVisible();
    expect(await owner.getByRole('combobox').locator('option').count()).toBe(5);
    for (let step = 0; step < 5; step++) {
      await owner.keyboard.press('Tab');
      expect(
        await owner
          .getByRole('dialog')
          .evaluate((dialog) => dialog.contains(document.activeElement)),
      ).toBe(true);
    }
    await owner.keyboard.press('Escape');
    await expect(owner.getByRole('dialog')).toHaveCount(0);
    await expect(report).toBeFocused();
    expect(requests).toHaveLength(0);
    await report.click();
    await owner.getByRole('combobox').selectOption('SPAM');
    await owner.getByRole('button', { name: 'Enviar denuncia', exact: true }).click();
    await expect(owner.getByRole('dialog')).toContainText('Denuncia recebida');
    expect(requests).toHaveLength(1);
    const dialog = await owner.getByRole('dialog').boundingBox();
    expect(dialog!.width).toBeLessThanOrEqual(info.project.use.viewport!.width);
    await owner.getByRole('button', { name: 'Cancelar', exact: true }).click();

    // Drop the transport without creating a fake business response: failure rolls back locally.
    await owner.route('**/api/v1/chat/me/mutes/*', (route) =>
      route.request().method() === 'PUT' ? route.abort('failed') : route.continue(),
    );
    await message.getByRole('button', { name: 'Silenciar pessoa', exact: true }).click();
    await expect(owner.getByRole('alert')).toContainText('Nao foi possivel sincronizar');
    await expect(message).toContainText(text);
    await owner.unroute('**/api/v1/chat/me/mutes/*');
    await owner.getByRole('button', { name: 'Tentar novamente', exact: true }).click();
    await expect(message).toHaveCount(0);
    await expect(sameOwner.locator(`[data-message-id="${id}"]`)).toHaveCount(0);
    await expect
      .poll(async () => (await state(contexts[0])).participantIds)
      .toContain(target.participantId);
    await expect(page.locator(`[data-message-id="${id}"]`)).toContainText(text);
    expect((await state(page.context())).participantIds).not.toContain(target.participantId);
    await owner.locator('.chat-muted summary').click();
    await owner.getByRole('button', { name: 'Desfazer silenciamento', exact: true }).click();
    await expect(message).toContainText(text);
    await expect(sameOwner.locator(`[data-message-id="${id}"]`)).toContainText(text);

    await message.locator('summary').click();
    await message.getByRole('button', { name: 'Silenciar pessoa', exact: true }).click();
    await expect(message).toHaveCount(0);
    const agenda = await contexts[0].request.get('/api/v1/public/sports/events');
    const eventId = (await agenda.json()).items[0].eventId;
    await owner.goto(`/eventos/${eventId}`);
    // The event page loads the chat only when its deferred placeholder enters the viewport.
    await owner
      .getByRole('heading', { name: 'Chat da partida', exact: true })
      .scrollIntoViewIfNeeded();
    await expect(owner.locator('.chat-connection')).toContainText('Ao vivo');
    await expect(
      owner.locator('.chat-message strong').filter({ hasText: target.nickname }),
    ).toHaveCount(0);
    await owner.locator('.chat-muted summary').click();
    await owner.getByRole('button', { name: 'Desfazer silenciamento', exact: true }).click();
    await expect(
      owner.locator('.chat-message strong').filter({ hasText: target.nickname }).first(),
    ).toBeVisible();

    // The real inactive actor still reports; enrollment gates only message writing.
    await promisify(execFile)(
      process.execPath,
      [
        resolve(process.cwd(), '../scripts/phase7-e2e.mjs'),
        'release-athlete',
        setting('BETUFU_E2E_ISOLATED_RUN'),
      ],
      { windowsHide: true },
    );
    await owner.goto('/comunidade');
    await expect(owner.getByRole('textbox', { name: 'Sua mensagem', exact: true })).toBeDisabled();
    // A different reason for an already-reported occurrence correctly conflicts; use a new occurrence.
    const inactiveTarget = publicBody.messages[info.project.name === 'phase7-320px' ? 2 : 1];
    const inactiveMessage = owner.locator(`[data-message-id="${inactiveTarget.messageId}"]`);
    await inactiveMessage.locator('summary').click();
    await inactiveMessage.getByRole('button', { name: 'Denunciar mensagem', exact: true }).click();
    await owner.getByRole('combobox').selectOption('OFENSA');
    const inactiveReceipt = owner.waitForResponse(
      (response) => response.request().method() === 'POST' && response.url().endsWith('/reports'),
    );
    await owner.getByRole('button', { name: 'Enviar denuncia', exact: true }).click();
    expect((await inactiveReceipt).status()).toBe(201);
    await expect(owner.getByRole('dialog')).toContainText('Denuncia recebida');
    await owner.getByRole('button', { name: 'Cancelar', exact: true }).click();
    await owner.locator('button.arena-nav__logout:visible').click();
    await expect(owner.locator('.chat-muted')).toHaveCount(0);
    await expect(owner.getByRole('dialog')).toHaveCount(0);
    expect((await contexts[0].request.get('/api/v1/chat/me/mutes')).status()).toBe(401);
    await Promise.race([
      owner.waitForURL('http://localhost:4300/', { waitUntil: 'domcontentloaded' }),
      owner.locator('#kc-logout').waitFor({ state: 'visible', timeout: 15000 }),
    ]);
    if (await owner.locator('#kc-logout').isVisible()) await owner.locator('#kc-logout').click();
    await owner.waitForURL('http://localhost:4300/', { waitUntil: 'domcontentloaded' });
    await expect(owner.locator('a.arena-nav__login:visible')).toBeVisible();
    const stored = await owner.evaluate(() =>
      JSON.stringify({ local: { ...localStorage }, session: { ...sessionStorage } }),
    );
    expect(stored).not.toContain(target.participantId);
    expect(stored).not.toContain(id);
    await owner.goto(`/eventos/${eventId}`);
    await owner
      .getByRole('heading', { name: 'Chat da partida', exact: true })
      .scrollIntoViewIfNeeded();
    await owner.locator('.chat-message summary').first().click();
    await expect(
      owner.getByRole('link', { name: 'Entrar para denunciar ou silenciar' }).first(),
    ).toHaveAttribute('href', `/login/start?returnTo=${encodeURIComponent(`/eventos/${eventId}`)}`);
    expect(await owner.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    expect(faults).toEqual([]);
  } finally {
    await Promise.all(contexts.map((context) => context.close()));
  }
});
