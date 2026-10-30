import { chromium, expect, type Page } from '@playwright/test';
import { execFileSync } from 'node:child_process';
import { resolve } from 'node:path';
import { randomUUID } from 'node:crypto';

export default async function setup() {
  if (process.env['BETUFU_E2E_INTEGRATED'] !== 'true' ||
      !/^betufu-phase7-e2e-[a-f0-9]{16}$/u.test(process.env['BETUFU_E2E_ISOLATED_RUN'] ?? ''))
    throw new Error('Missing owned isolated Phase7 context.');
  execFileSync(process.execPath, [resolve('../scripts/validate-phase7-approvals.mjs'),
    '--plan', '07-31', '--stage', 'implementation'], { stdio: 'inherit', windowsHide: true });
  // A genuine accepted prediction later settles after this reader's enrollment is released.
  const browser = await chromium.launch({ channel: 'chrome', headless: true,
    args: ['--host-resolver-rules=MAP localhost 127.0.0.1'] });
  try {
    const context = await browser.newContext({ baseURL: 'http://localhost:4300' });
    const page = await context.newPage();
    await page.goto('/'); await page.locator('a.arena-nav__login:visible').click();
    await page.locator('#username').fill(process.env['BETUFU_E2E_READER_USERNAME']!);
    await page.locator('#password').fill(process.env['BETUFU_E2E_READER_PASSWORD']!);
    await page.getByRole('button', { name: 'Entrar', exact: true }).click();
    await expect(page.locator('a.arena-nav__account:visible')).toBeVisible();
    const read = async (path: string) => {
      const response = await context.request.get(path); expect(response.status()).toBe(200); return response.json();
    };
    const agenda = await read('/api/v1/public/sports/events');
    const eventId = agenda.items[process.env['BETUFU_E2E_PROJECT'] === 'phase7-320px' ? 1 : 0].eventId;
    const event = await read(`/api/v1/public/sports/events/${eventId}`);
    const market = await read(`/api/v1/public/odds/markets/${event.marketId}`);
    const csrf = (await context.cookies()).find(cookie => cookie.name === 'XSRF-TOKEN')!;
    const accepted = await context.request.post('/api/v1/me/predictions', {
      headers: { Origin: 'http://localhost:4300', 'X-XSRF-TOKEN': decodeURIComponent(csrf.value), 'Idempotency-Key': randomUUID() },
      data: { marketId: event.marketId, marketVersionId: market.marketVersionId,
        publicationId: market.current.publicationId, ruleVersion: market.explanation.algorithmVersion,
        selection: 'HOME', stake: 10 },
    });
    expect(accepted.status()).toBe(201); expect((await accepted.json()).predictionId).toBeTruthy();
  } finally { await browser.close(); }
}

// Other journeys acknowledge the actual dialog; its complete behavior is tested first.
export async function handleIntegratedRules(page: Page) {
  if (process.env['BETUFU_E2E_INTEGRATED'] !== 'true') return;
  await page.addLocatorHandler(page.getByRole('dialog', { name: 'Regras da conversa', exact: true }), async dialog => {
    const saved = page.waitForResponse(response => response.url().endsWith('/api/v1/chat/rules/preference/dismiss') &&
      response.request().method() === 'POST');
    await dialog.getByRole('button', { name: 'Fechar regras', exact: true }).click();
    expect((await saved).status()).toBe(200);
  });
}
