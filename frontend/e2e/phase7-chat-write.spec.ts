import { expect, test, type Page } from '@playwright/test';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { resolve } from 'node:path';
import { randomUUID } from 'node:crypto';

function setting(name: string): string {
  const value = process.env[name];
  if (!value) throw new Error(`Missing isolated setting ${name}`);
  return value;
}
async function login(page: Page, prefix: string) {
  await page.goto('/');
  await page.locator('a.arena-nav__login:visible').click();
  await Promise.race([
    page.getByLabel('Endereço de email').waitFor({ state: 'visible' }),
    page.locator('a.arena-nav__account:visible').waitFor({ state: 'visible' }),
  ]);
  if (await page.locator('a.arena-nav__account:visible').isVisible()) return;
  await page.getByLabel('Endereço de email').fill(setting(`${prefix}_USERNAME`));
  await page
    .getByRole('textbox', { name: 'Senha', exact: true })
    .fill(setting(`${prefix}_PASSWORD`));
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page.locator('a.arena-nav__account:visible')).toBeVisible();
}
async function compose(page: Page, text: string) {
  const input = page.getByRole('textbox', { name: 'Sua mensagem', exact: true });
  await expect(input).toBeEnabled();
  await input.fill(text);
  await page.getByRole('button', { name: 'Enviar mensagem', exact: true }).click();
}
async function factCount(page: Page, path: string, text: string) {
  const response = await page.context().request.get(path);
  expect(response.status()).toBe(200);
  const body = await response.json();
  return body.messages.filter((message: { text?: string }) => message.text === text).length;
}

test('real writes: offline, lost ACK, volatile queue, scroll and session purge', async ({
  page,
}, info) => {
  test.setTimeout(180000);
  const mobile = info.project.name === 'phase7-320px';
  const prefix = mobile ? 'BETUFU_E2E_OTHER' : 'BETUFU_E2E';
  const faults: string[] = [];
  page.on('pageerror', (error) => faults.push(error.name));
  await login(page, prefix);
  await page.goto('/comunidade');
  const input = page.getByRole('textbox', { name: 'Sua mensagem', exact: true });
  await expect(input).toBeEnabled();
  await input.fill('😀'.repeat(280));
  await expect(page.locator('.chat-composer')).toContainText('280/280');
  await input.fill('😀'.repeat(281));
  await expect(page.getByRole('button', { name: 'Enviar mensagem', exact: true })).toBeDisabled();

  const offline = `Offline ${randomUUID()}`;
  const posts: { key: string | undefined; text: string; time: number }[] = [];
  page.on('request', (request) => {
    if (request.method() === 'POST' && /\/chat\/.+\/messages$/u.test(request.url()))
      posts.push({
        key: request.headers()['idempotency-key'],
        text: request.postDataJSON().text,
        time: Date.now(),
      });
  });
  await page.context().setOffline(true);
  await compose(page, offline);
  await expect(page.locator('.chat-outbox')).toContainText('Aguardando conexao');
  expect(posts).toHaveLength(0);
  await page.context().setOffline(false);
  await expect(page.locator('.chat-pending')).toContainText('Enviada');
  await expect.poll(() => factCount(page, '/api/v1/chat/global', offline)).toBe(1);

  const lost = `Lost ACK ${randomUUID()}`;
  const keys: string[] = [];
  let lostOnce = false;
  await page.route('**/api/v1/chat/global/messages', async (route) => {
    if (route.request().postDataJSON().text !== lost) {
      await route.continue();
      return;
    }
    keys.push(route.request().headers()['idempotency-key']);
    if (!lostOnce) {
      lostOnce = true;
      // Commit a real request, then lose only its network acknowledgement.
      const response = await route.fetch();
      expect(response.status()).toBe(201);
      await route.abort('failed');
    } else await route.continue();
  });
  await compose(page, lost);
  await expect.poll(() => keys.length).toBe(2);
  await expect(page.locator('.chat-pending')).toContainText('Enviada');
  expect(keys[0]).toBeTruthy();
  expect(keys[1]).toBe(keys[0]);
  await expect.poll(() => factCount(page, '/api/v1/chat/global', lost)).toBe(1);
  await page.unroute('**/api/v1/chat/global/messages');

  const volatile = `Discard on refresh ${randomUUID()}`;
  await page.context().setOffline(true);
  await compose(page, volatile);
  await page.reload({ waitUntil: 'commit' }).catch(() => {
    /* Real offline reload destroys the document. */
  });
  await page.context().setOffline(false);
  await page.goto('/comunidade');
  await expect(input).toBeEnabled();
  // Refresh purges memory. It must not revive a queued command from storage.
  await expect(page.locator('.chat-pending')).toHaveCount(0);
  expect(await factCount(page, '/api/v1/chat/global', volatile)).toBe(0);

  // Hold only the real logout transport; local invalidation must happen before its response.
  let continueLogout!: () => void;
  const logoutGate = new Promise<void>((done) => {
    continueLogout = done;
  });
  let logoutStarted!: () => void;
  const started = new Promise<void>((done) => {
    logoutStarted = done;
  });
  await page.route('**/api/v1/session/logout', async (route) => {
    logoutStarted();
    await logoutGate;
    await route.continue();
  });
  const discarded = `Discard on logout ${randomUUID()}`;
  await page.context().setOffline(true);
  await compose(page, discarded);
  await page.locator('button.arena-nav__logout:visible').click();
  await started;
  await page.context().setOffline(false);
  await page.waitForTimeout(3500);
  expect(posts.filter((p) => p.text === discarded)).toHaveLength(0);
  await expect(page.locator('.chat-pending')).toHaveCount(0);
  const logoutResponse = page.waitForResponse((r) => r.url().includes('/api/v1/session/logout'));
  continueLogout();
  expect((await logoutResponse).status()).toBe(200);
  await page.unroute('**/api/v1/session/logout');
  await page.goto('/comunidade');
  await expect(page.locator('a.chat-login')).toBeVisible();
  expect((await page.context().request.get('/api/v1/chat/global')).status()).toBe(401);

  expect(faults).toEqual([]);
});

test('real terminal403 stops automatic retry after current enrollment changes', async ({
  page,
}, info) => {
  const mobile = info.project.name === 'phase7-320px';
  await login(page, mobile ? 'BETUFU_E2E_OTHER' : 'BETUFU_E2E');
  const agenda = await page.context().request.get('/api/v1/public/sports/events');
  const eventId = (await agenda.json()).items[mobile ? 1 : 0].eventId;
  const eventPath = `/api/v1/chat/events/${eventId}`;
  await page.goto(`/eventos/${eventId}`);
  const input = page.getByRole('textbox', { name: 'Sua mensagem', exact: true });
  // Change only the owned actor's enrollment between eligibility read and real POST.
  await page.route(`**${eventPath}/messages`, async (route) => {
    await promisify(execFile)(
      process.execPath,
      [
        resolve(process.cwd(), '../scripts/phase7-e2e.mjs'),
        mobile ? 'release-athlete' : 'release-student',
        setting('BETUFU_E2E_ISOLATED_RUN'),
      ],
      { windowsHide: true },
    );
    await route.continue();
  });
  const rejected = `Terminal ${randomUUID()}`;
  await promisify(execFile)(
    process.execPath,
    [
      resolve(process.cwd(), '../scripts/phase7-e2e.mjs'),
      mobile ? 'release-athlete' : 'release-student',
      setting('BETUFU_E2E_ISOLATED_RUN'),
    ],
    { windowsHide: true },
  );
  const csrf = (await page.context().cookies()).find((cookie) => cookie.name === 'XSRF-TOKEN');
  const terminal = await page.context().request.post(`${eventPath}/messages`, {
    headers: {
      Origin: new URL(page.url()).origin,
      'X-XSRF-TOKEN': csrf!.value,
      'Idempotency-Key': randomUUID(),
    },
    data: { text: rejected },
  });
  expect(terminal.status()).toBe(403);
  await page.waitForTimeout(3500);
  expect(await factCount(page, eventPath, rejected)).toBe(0);
});
