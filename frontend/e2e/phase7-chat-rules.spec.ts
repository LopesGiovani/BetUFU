import { expect, test, type Page } from '@playwright/test';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';
import { resolve } from 'node:path';

test.use({ actionTimeout: 15_000 });
const PREF = '/api/v1/chat/rules/preference';
function setting(key: string) { const value = process.env[key]; if (!value) throw new Error(`Missing ${key}`); return value; }
async function login(page: Page, prefix: string) {
  await page.goto('/'); await page.locator('a.arena-nav__login:visible').click();
  await page.locator('#username').fill(setting(`${prefix}_USERNAME`)); await page.locator('#password').fill(setting(`${prefix}_PASSWORD`));
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();
  await expect(page.locator('a.arena-nav__account:visible')).toBeVisible();
  await page.locator('a[href="/comunidade"]:visible').click();
}
async function preference(page: Page) {
  const response = await page.request.get(PREF); expect(response.status()).toBe(200); return response.json();
}
async function closeRules(page: Page, escape = false) {
  const response = page.waitForResponse(r => r.url().endsWith(`${PREF}/dismiss`) && r.request().method() === 'POST');
  if (escape) await page.keyboard.press('Escape'); else await page.getByRole('button', { name: 'Fechar regras', exact: true }).click();
  expect((await response).status()).toBe(200);
  await expect(page.getByRole('dialog', { name: 'Regras da conversa', exact: true })).not.toBeVisible();
  await expect(page.getByText('Salvando preferência de regras...', { exact: true })).toHaveCount(0);
}

test('rules persist per account across devices; guests stay manual; network failures and session replacement remain honest', async ({ page, browser }, info) => {
  const mobile = info.project.name === 'phase7-320px';
  const accountA = mobile ? 'BETUFU_E2E_ADMIN' : 'BETUFU_E2E';
  const accountB = mobile ? 'BETUFU_E2E_MODERATOR' : 'BETUFU_E2E_OTHER';
  const faults: string[] = []; page.on('pageerror', error => faults.push(error.message));
  const sameContext = await browser.newContext({ baseURL: 'http://localhost:4300', viewport: info.project.use.viewport });
  const otherContext = await browser.newContext({ baseURL: 'http://localhost:4300', viewport: info.project.use.viewport });
  try {
    await page.goto('/'); await page.locator('a[href="/comunidade"]:visible').click();
    await expect(page.getByRole('dialog')).not.toBeVisible();
    await page.locator('a.chat-login').click();
    await page.locator('#username').fill(setting(`${accountA}_USERNAME`)); await page.locator('#password').fill(setting(`${accountA}_PASSWORD`));
    await page.getByRole('button', { name: 'Entrar', exact: true }).click();
    const dialog = page.getByRole('dialog', { name: 'Regras da conversa', exact: true });
    await expect(dialog).toBeVisible();
    const close = dialog.getByRole('button', { name: 'Fechar regras', exact: true }); await expect(close).toBeFocused();
    await page.keyboard.press('Tab'); await expect(close).toBeFocused(); await page.keyboard.press('Shift+Tab'); await expect(close).toBeFocused();
    expect(await preference(page)).toEqual({ dismissed: false }); await closeRules(page, true);
    await expect(page.getByRole('button', { name: 'Regras', exact: true })).toBeFocused();
    expect(await preference(page)).toEqual({ dismissed: true });
    await page.reload(); await expect(page.locator('a.arena-nav__account:visible')).toBeVisible();
    await expect(dialog).not.toBeVisible();
    const same = await sameContext.newPage(); await login(same, accountA);
    await expect(same.getByText('Consultando preferência de regras...', { exact: true })).toHaveCount(0);
    await expect(same.getByRole('dialog')).not.toBeVisible(); expect(await preference(same)).toEqual({ dismissed: true });
    await same.getByRole('button', { name: 'Regras', exact: true }).click(); await expect(same.getByRole('dialog')).toBeVisible();
    await same.mouse.click(1, 1); await expect(same.getByRole('dialog')).not.toBeVisible();
    await expect(same.getByRole('button', { name: 'Regras', exact: true })).toBeFocused();

    const other = await otherContext.newPage(); other.on('pageerror', error => faults.push(error.message));
    // Keep the loaded route module in the same document before interrupting real API reads.
    await other.goto('/comunidade'); await other.goto('/'); await login(other, accountB);
    await expect(other.getByRole('dialog')).toBeVisible(); expect(await preference(other)).toEqual({ dismissed: false });
    await other.goBack(); await expect(other).toHaveURL('http://localhost:4300/');
    await expect(other.locator('a.arena-nav__account:visible')).toBeVisible();
    await expect(other.locator('a[href="/comunidade"]:visible')).toBeVisible(); await otherContext.setOffline(true);
    await other.locator('a[href="/comunidade"]:visible').click();
    await expect(other.getByText('Não foi possível consultar a preferência. Tente novamente ou abra Regras.', { exact: true })).toBeVisible();
    await expect(other.getByRole('dialog')).not.toBeVisible();
    await otherContext.setOffline(false); await other.getByRole('button', { name: 'Tentar sincronizar regras', exact: true }).click();
    await expect(other.getByRole('dialog')).toBeVisible();
    await otherContext.setOffline(true); await other.keyboard.press('Escape');
    await expect(other.getByText('A preferência não foi salva. O aviso pode reaparecer em outro dispositivo.', { exact: true })).toBeVisible();
    await expect(other.getByRole('dialog')).not.toBeVisible(); await otherContext.setOffline(false);

    await page.locator('a.arena-nav__account:visible').click();
    await expect(page).toHaveURL(/\/conta\/perfil$/);
    await page.locator('.console-nav__account').click();
    await page.getByRole('button', { name: 'Sair', exact: true }).click();
    await page.waitForURL(url => url.origin === 'http://localhost:8281' || url.href === 'http://localhost:4300/');
    if (new URL(page.url()).origin === 'http://localhost:8281') await page.locator('#kc-logout').click();
    await expect(page).toHaveURL('http://localhost:4300/');
    expect((await page.request.get(PREF)).status()).toBe(401);
    await login(page, accountB); await expect(dialog).toBeVisible();
    expect(await preference(page)).toEqual({ dismissed: false }); await closeRules(page);
    await other.getByRole('button', { name: 'Tentar sincronizar regras', exact: true }).click();
    await expect(other.getByText('A preferência não foi salva. O aviso pode reaparecer em outro dispositivo.', { exact: true })).toHaveCount(0);
    expect(await preference(other)).toEqual({ dismissed: true });
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);

    const run = setting('BETUFU_E2E_ISOLATED_RUN');
    await promisify(execFile)(process.execPath, [resolve('../scripts/phase7-e2e.mjs'), 'expire', run]);
    await page.reload(); await expect(page.locator('a.chat-login')).toBeVisible(); await expect(dialog).not.toBeVisible();
    expect((await page.request.get(PREF)).status()).toBe(401);
    const publicReads: string[] = []; page.on('request', request => { if (request.url().includes(PREF)) publicReads.push(request.url()); });
    const agenda = await page.request.get('/api/v1/public/sports/events'); expect(agenda.status()).toBe(200);
    const eventId = (await agenda.json()).items[0].eventId;
    await page.goto(`/eventos/${eventId}`);
    await page.getByRole('heading', { name: 'Chat da partida', exact: true }).scrollIntoViewIfNeeded();
    const manual = page.getByRole('button', { name: 'Regras', exact: true });
    await expect(manual).toBeVisible(); await expect(page.getByRole('dialog')).not.toBeVisible();
    await manual.click(); await expect(page.getByRole('dialog')).toBeVisible();
    await page.keyboard.press('Escape'); await expect(manual).toBeFocused(); expect(publicReads).toEqual([]); expect(faults).toEqual([]);
  } finally { await otherContext.setOffline(false); await sameContext.close(); await otherContext.close(); }
});
