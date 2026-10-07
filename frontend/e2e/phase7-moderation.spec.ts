import { expect, test, type Page, type BrowserContext } from '@playwright/test';
import { randomUUID } from 'node:crypto';

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
async function headers(context: BrowserContext) {
  const token = (await context.cookies()).find((c) => c.name === 'XSRF-TOKEN')!;
  return { Origin: 'http://localhost:4300', 'X-XSRF-TOKEN': token.value };
}
async function confirm(page: Page, reason: string) {
  await expect(page.getByRole('dialog')).toBeVisible();
  await page.getByRole('textbox', { name: 'Motivo da operacao' }).fill(reason);
  const response = page.waitForResponse(
    (r) => r.url().includes('/api/v1/chat/moderation/') &&
      (r.request().method() === 'POST' || r.url().includes('/evidence?')),
  );
  await page.getByRole('button', { name: 'Confirmar operacao', exact: true }).click();
  expect((await response).ok()).toBe(true);
  await expect(page.getByRole('dialog')).toHaveCount(0);
}

test('scoped moderator forwards, administrator sanctions, owner requests review and administrator annuls', async ({
  page,
  browser,
}, info) => {
  test.setTimeout(180000);
  const contexts: BrowserContext[] = [];
  const errors: string[] = [];
  page.on('pageerror', (e) => errors.push(e.message));
  try {
    await login(page, 'BETUFU_E2E');
    const creditsBefore = await (await page.request.get('/api/v1/me/credits')).json();
    const rankBefore = await (await page.request.get('/api/v1/public/ranking/lifetime')).json();
    const agenda = await (await page.request.get('/api/v1/public/sports/events')).json();
    const eventId = agenda.items[info.project.name === 'phase7-320px' ? 1 : 0].eventId;
    const messages = await (await page.request.get(`/api/v1/chat/events/${eventId}`)).json();
    const target = messages.messages.find((m: { removed: boolean }) => !m.removed);
    const original = target.text;
    const roles = ['BETUFU_E2E_MODERATOR', 'BETUFU_E2E_ADMIN', 'BETUFU_E2E_OTHER'];
    const pages: Page[] = [];
    for (const prefix of roles) {
      const ctx = await browser.newContext({
        baseURL: 'http://localhost:4300',
        viewport: info.project.use.viewport,
        locale: 'pt-BR',
      });
      contexts.push(ctx);
      const p = await ctx.newPage();
      pages.push(p);
      p.on('pageerror', (e) => errors.push(e.message));
      await login(p, prefix);
    }
    const [moderator, admin, reporter] = pages;
    await reporter.goto('/');
    await reporter.getByRole('button', { name: /Próximos/u }).click();
    const eventLink = reporter.locator(`a[href="/eventos/${eventId}"]`).first();
    await expect(eventLink).toBeVisible();
    await eventLink.click({ force: true });
    await reporter
      .getByRole('heading', { name: 'Chat da partida', exact: true })
      .scrollIntoViewIfNeeded();
    const liveMessage = reporter.locator(`[data-message-id="${target.messageId}"]`);
    await expect(liveMessage).toContainText(original);
    await expect(reporter.locator('.chat-connection')).toContainText('Ao vivo');
    const report = await reporter.request.post(
      `/api/v1/chat/messages/${target.messageId}/reports`,
      {
        headers: { ...(await headers(reporter.context())), 'Idempotency-Key': randomUUID() },
        data: { reason: 'OFENSA' },
      },
    );
    expect(report.status()).toBe(201);
    await moderator.goto('/conta/punicoes');
    const navigation = moderator.locator('a[href="/operacao/comunidade/moderacao"]');
    await navigation.first().scrollIntoViewIfNeeded();
    await navigation.first().click();
    const queue = await (await moderator.request.get('/api/v1/chat/moderation/cases')).json();
    const item = queue.find((c: { messageId: string }) => c.messageId === target.messageId);
    expect(item).toBeTruthy();
    const caseId = item.caseId as string;
    const card = moderator.locator(`[data-case-id="${caseId}"]`);
    await expect(card).toBeVisible();
    await expect(moderator.getByRole('button', { name: 'Propor sancao' })).toHaveCount(0);
    expect(await moderator.content()).not.toContain(original);
    expect(
      (
        await moderator.request.get(`/api/v1/chat/moderation/cases/${caseId}/sanctions/suggestion`)
      ).status(),
    ).toBe(403);
    expect(
      (
        await reporter.request.get(
          `/api/v1/chat/moderation/cases/${caseId}/evidence?reason=Consulta`,
        )
      ).status(),
    ).toBe(403);

    const forward = card.getByRole('button', { name: 'Encaminhar caso', exact: true });
    await forward.click();
    for (let i = 0; i < 4; i++) {
      await moderator.keyboard.press('Tab');
      expect(
        await moderator
          .getByRole('dialog')
          .evaluate((dialog) => dialog.contains(document.activeElement)),
      ).toBe(true);
    }
    await moderator.keyboard.press('Escape');
    await expect(moderator.getByRole('dialog')).toHaveCount(0);
    await expect(forward).toBeFocused();
    await forward.click();
    await confirm(moderator, 'Encaminhar para analise do administrador');
    await moderator.reload();
    await expect(card).toContainText('ESCALATED');
    const duplicate = {
      clientCommandId: randomUUID(),
      reason: 'Retry auditavel do encaminhamento',
    };
    for (let i = 0; i < 2; i++)
      expect(
        (
          await moderator.request.post(`/api/v1/chat/moderation/cases/${caseId}/forward`, {
            headers: await headers(moderator.context()),
            data: duplicate,
          })
        ).status(),
      ).toBe(200);
    await admin.goto('/operacao/painel');
    await admin.getByRole('link', { name: 'Moderacao da comunidade', exact: true }).click();
    const adminCard = admin.locator(`[data-case-id="${caseId}"]`);
    await expect(adminCard).toContainText('ESCALATED');
    await adminCard.getByRole('button', { name: 'Consultar original restrito' }).click();
    await confirm(admin, 'Consultar evidencia para decidir este caso');
    await expect(
      admin
        .getByRole('region', { name: 'Evidencia restrita' })
        .or(admin.locator('aside[aria-label="Evidencia restrita"]')),
    ).toContainText(original);
    await admin.getByRole('button', { name: 'Fechar evidencia' }).click();
    await adminCard.getByRole('button', { name: 'Propor sancao' }).click();
    await expect(admin.getByRole('dialog')).toContainText(/CHAT_SUSPENSION_(24H|7D)/);
    await confirm(admin, 'Sancao confirmada apos analise da ocorrencia');
    await page.goto('/conta/punicoes');
    const own = await (await page.request.get('/api/v1/chat/me/sanctions')).json();
    const sanction = own.find((s: { active: boolean }) => s.active);
    expect(sanction).toBeTruthy();
    const ownCard = page.locator(`[data-sanction-id="${sanction.sanctionId}"]`);
    await expect(ownCard).toContainText('Sancao confirmada');
    expect(
      (
        await reporter.request.post(`/api/v1/chat/me/sanctions/${sanction.sanctionId}/reviews`, {
          headers: await headers(reporter.context()),
          data: { clientCommandId: randomUUID(), reason: 'Terceiro sem titularidade' },
        })
      ).status(),
    ).toBe(404);
    expect(
      (
        await page.request.post('/api/v1/chat/global/messages', {
          headers: { ...(await headers(page.context())), 'Idempotency-Key': randomUUID() },
          data: { text: 'Escrita bloqueada durante sancao' },
        })
      ).status(),
    ).toBe(403);
    await ownCard.getByRole('button', { name: 'Pedir revisao' }).click();
    await page
      .getByRole('textbox', { name: 'Explique seu pedido' })
      .fill('Solicito reavaliacao da ocorrencia');
    await page.getByRole('button', { name: 'Enviar pedido de revisao' }).click();
    await expect(page.getByRole('dialog')).toHaveCount(0);
    await expect(ownCard).toContainText('PENDING');
    await admin.getByRole('button', { name: 'Atualizar casos' }).click();
    const review = (await (await admin.request.get('/api/v1/chat/moderation/reviews')).json()).find(
      (r: { sanctionId: string }) => r.sanctionId === sanction.sanctionId,
    );
    await admin
      .locator(`[data-review-id="${review.reviewId}"]`)
      .getByRole('button', { name: 'Anular sancao' })
      .click();
    await confirm(admin, 'Ocorrencia anulada apos revisao documentada');
    await page.getByRole('button', { name: 'Atualizar punicoes' }).click();
    await expect(ownCard).toContainText('ANNULLED');
    await adminCard.getByRole('button', { name: 'Remover mensagem' }).click();
    await confirm(admin, 'Remocao visual sem apagar o fato historico');
    await expect(liveMessage).toContainText('Mensagem removida');
    await expect(liveMessage).not.toContainText(original);
    const redacted = await (await page.request.get(`/api/v1/chat/events/${eventId}`)).json();
    expect(
      redacted.messages.find((m: { messageId: string }) => m.messageId === target.messageId)
        .removed,
    ).toBe(true);
    expect((await (await page.request.get('/api/v1/me/credits')).json()).balance).toBe(
      creditsBefore.balance,
    );
    expect(
      (await (await page.request.get('/api/v1/public/ranking/lifetime')).json()).entries,
    ).toEqual(rankBefore.entries);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    await moderator.goto('/');
    const loggedOut = moderator.waitForResponse(
      (r) => r.url().endsWith('/api/v1/session/logout') && r.request().method() === 'POST',
    );
    await moderator.locator('button.arena-nav__logout:visible').click();
    const logoutResponse = await loggedOut;
    expect(logoutResponse.ok()).toBe(true);
    await moderator.locator('#kc-logout').waitFor({ state: 'visible', timeout: 15000 });
    await moderator.locator('#kc-logout').click();
    await moderator.waitForURL('http://localhost:4300/', { waitUntil: 'domcontentloaded' });
    await expect(moderator.locator('a.arena-nav__login:visible')).toBeVisible();
    expect((await moderator.request.get('/api/v1/chat/moderation/cases')).status()).toBe(401);
    expect(errors).toEqual([]);
  } finally {
    await Promise.all(contexts.map((c) => c.close()));
  }
});
