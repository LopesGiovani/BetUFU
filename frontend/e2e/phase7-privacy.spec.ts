import { handleIntegratedRules } from './phase7.setup';
import { expect, test, type BrowserContext, type Page } from '@playwright/test';
import { randomUUID } from 'node:crypto';

test.use({ actionTimeout: 15_000 });
const ROOT = '/api/v1/privacy';
function setting(key: string) { const value = process.env[key]; if (!value) throw new Error(`Missing isolated ${key}`); return value; }
async function login(page: Page, prefix: string) {
  await handleIntegratedRules(page);
  await page.goto('/'); await page.locator('a.arena-nav__login:visible').click();
  await page.locator('#username').fill(setting(`${prefix}_USERNAME`)); await page.locator('#password').fill(setting(`${prefix}_PASSWORD`));
  await page.getByRole('button', { name: 'Entrar', exact: true }).click(); await expect(page.locator('a.arena-nav__account:visible')).toBeVisible();
}
async function api(page: Page, path: string, method = 'GET', data?: unknown, expected = 200) {
  const csrf = (await page.context().cookies()).find(item => item.name === 'XSRF-TOKEN');
  const response = await page.request.fetch(path, { method, data, headers: { Origin: 'http://localhost:4300',
    ...(csrf ? { 'X-XSRF-TOKEN': decodeURIComponent(csrf.value) } : {}) } });
  expect(response.status(), `${method} ${path}`).toBe(expected); return response.json();
}
async function confirm(page: Page, path: string, expected: number) {
  const dialog = page.getByRole('dialog'); await expect(dialog).toBeVisible();
  const url = page.url(); const navigations: string[] = [];
  const trackNavigation = (request: import('@playwright/test').Request) => {
    if (request.isNavigationRequest() && request.frame() === page.mainFrame()) navigations.push(request.url());
  };
  page.on('request', trackNavigation);
  try {
  const response = page.waitForResponse(r => r.url().endsWith(path) && r.request().method() === 'POST');
  await dialog.getByRole('button', { name: 'Confirmar operacao', exact: true }).click();
  const result = await response; expect(result.status()).toBe(expected);
  if (expected < 300) await expect(dialog).not.toBeVisible();
  expect(page.url()).toBe(url); expect(navigations).toEqual([]);
  } finally { page.off('request', trackNavigation); }
}
async function displayedCase(page: Page) {
  const protocol = page.locator('section[aria-labelledby="privacy-detail"] p').first().locator('span');
  await expect(protocol).toBeVisible();
  return api(page, `${ROOT}/requests/${await protocol.innerText()}`);
}
async function respond(admin: Page, caseId: string, decision: string, text: string) {
  await admin.getByLabel('Decisao operacional').selectOption(decision); await admin.getByLabel('Resposta ao titular').fill(text);
  await admin.getByRole('button', { name: 'Registrar resposta', exact: true }).click();
  await confirm(admin, `${ROOT}/requests/${caseId}/responses`, 200);
}

test('owner discovers privacy, paused rights remain accessible and only administrator replies with real restricted exports', async ({ page, browser }, info) => {
  const contexts: BrowserContext[] = []; const errors: string[] = [];
  let restorePause: (() => Promise<unknown>) | null = null;
  page.on('pageerror', error => errors.push(error.message));
  try {
    await login(page, 'BETUFU_E2E'); await page.locator('a.arena-nav__account:visible').click();
    await page.getByRole('link', { name: 'Privacidade', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Privacidade da conta', exact: true })).toBeVisible();
    const pages: Page[] = [];
    for (const prefix of ['BETUFU_E2E_ADMIN', 'BETUFU_E2E_OTHER', 'BETUFU_E2E_MODERATOR']) {
      const context = await browser.newContext({ baseURL: 'http://localhost:4300', viewport: info.project.use.viewport, locale: 'pt-BR' }); contexts.push(context);
      const other = await context.newPage(); other.on('pageerror', error => errors.push(error.message)); await login(other, prefix); pages.push(other);
    }
    const [admin, other, moderator] = pages;
    await admin.goto('/operacao/painel'); await admin.getByRole('link', { name: 'Privacidade dos titulares', exact: true }).click();
    await expect(admin.getByLabel('Identificador do pedido')).toBeVisible();
    const command = { clientCommandId: randomUUID(), kind: 'ACCOUNT', targetId: setting('BETUFU_E2E_STUDENT_ID'), action: 'PAUSE', reason: 'Direitos permanecem acessiveis durante pausa isolada' };
    const pause = await api(admin, '/api/v1/operations/emergency-pauses', 'POST', command, 201);
    restorePause = () => api(admin, '/api/v1/operations/emergency-pauses', 'POST', { ...command, clientCommandId: randomUUID(), action: 'RESUME', resumedPauseId: pause.factId }, 201);

    const trigger = page.getByRole('button', { name: 'Solicitar direito', exact: true });
    await trigger.focus(); await page.keyboard.press('Enter');
    await expect(page.getByRole('dialog')).toBeVisible(); await page.keyboard.press('Escape');
    await expect(trigger).toBeFocused(); await expect(page.getByRole('dialog')).not.toBeVisible();
    const before = await api(page, `${ROOT}/me/cases`);
    await trigger.click(); await confirm(page, `${ROOT}/requests`, 201); const request = await displayedCase(page); const caseId = request.caseId;
    expect(request.state).toBe('PENDING');
    await expect(page.getByRole('heading', { name: 'Acompanhamento do pedido', exact: true })).toBeVisible();
    const ownCases = await api(page, `${ROOT}/me/cases`); expect(ownCases.length).toBe(before.length + 1);
    await api(other, `${ROOT}/requests/${caseId}`, 'GET', undefined, 403);
    await api(moderator, `${ROOT}/requests/${caseId}/responses`, 'POST', { decision: 'ANALYZE', responseText: 'forbidden' }, 403);
    await api(page, `${ROOT}/requests/${caseId}/responses`, 'POST', { decision: 'APPROVE', responseText: 'forbidden' }, 403);
    await moderator.goto('/operacao/privacidade');
    await expect(moderator.getByRole('alert')).toHaveText('Somente o administrador geral pode analisar e responder pedidos.');
    await expect(moderator.getByLabel('Identificador do pedido')).toHaveCount(0);
    await admin.getByLabel('Identificador do pedido').fill(caseId); await admin.getByRole('button', { name: 'Consultar pedido', exact: true }).click();
    await respond(admin, caseId, 'ANALYZE', 'Conferencia real em andamento');
    await page.getByRole('button', { name: 'Atualizar pedido', exact: true }).click();
    await expect(page.getByText('Conferencia real em andamento', { exact: true })).toBeVisible();
    await admin.getByRole('button', { name: 'Preparar exportacao restrita', exact: true }).click();
    await confirm(admin, `${ROOT}/requests/${caseId}/exports`, 201);
    const artifact = await api(admin, `${ROOT}/requests/${caseId}/exports/latest`);
    expect(artifact.complete).toBe(false); expect(artifact.blockedClasses.length).toBeGreaterThan(0);
    expect(artifact.data.IDENTITY.some((record: { fields: { nickname: string } }) => record.fields.nickname === 'aluno-ufu')).toBe(true);
    expect(artifact.data.COMMON_MESSAGES.length).toBeGreaterThan(0);
    await api(other, `${ROOT}/requests/${caseId}/exports/latest`, 'GET', undefined, 403);
    await api(admin, `${ROOT}/requests/${caseId}/responses`, 'POST', { decision: 'APPROVE', responseText: 'Nao pode concluir exportacao parcial' }, 409);
    await page.getByRole('button', { name: 'Atualizar pedido', exact: true }).click();
    const downloadEvent = page.waitForEvent('download'); await page.getByRole('button', { name: 'Baixar exportacao privada', exact: true }).click();
    const download = await downloadEvent; const stream = await download.createReadStream(); const chunks: Buffer[] = [];
    for await (const chunk of stream!) chunks.push(Buffer.from(chunk));
    const downloaded = JSON.parse(Buffer.concat(chunks).toString('utf8'));
    expect(downloaded.exportId).toBe(artifact.exportId); expect(downloaded.complete).toBe(false);
    await respond(admin, caseId, 'REJECT', 'Pedido indeferido com resposta rastreavel; categorias ainda pendentes.');
    await page.getByRole('button', { name: 'Atualizar pedido', exact: true }).click();
    await expect(page.getByText('Pedido indeferido com resposta rastreavel; categorias ainda pendentes.', { exact: true })).toBeVisible();

    await page.getByLabel('Direito solicitado').selectOption('NOTICE'); await trigger.click();
    await confirm(page, `${ROOT}/requests`, 201); const notice = await displayedCase(page);
    await admin.getByLabel('Identificador do pedido').fill(notice.caseId); await admin.getByRole('button', { name: 'Consultar pedido', exact: true }).click();
    await respond(admin, notice.caseId, 'APPROVE', 'Aviso apresentado pelo canal autenticado.');
    expect((await api(page, `${ROOT}/requests/${notice.caseId}`)).state).toBe('RESPONDED');

    await admin.getByText('Atleta sem conta', { exact: true }).click();
    await admin.getByLabel('Identificador do atleta').fill(setting('BETUFU_E2E_PRIVATE_ATHLETE_ID'));
    await admin.getByLabel('Referencia restrita da comprovacao').fill('comprovacao-restrita-isolada');
    await admin.getByLabel('Conferi a identidade fora do sistema e o canal de resposta').check();
    await admin.getByLabel('Direito do atleta').selectOption('NOTICE'); await admin.getByRole('button', { name: 'Abrir pedido verificado', exact: true }).click();
    await confirm(admin, `${ROOT}/requests`, 201); const athlete = await displayedCase(admin);
    await respond(admin, athlete.caseId, 'APPROVE', 'Resposta encaminhada pelo canal verificado apos conferencia externa.');
    await api(other, `${ROOT}/requests/${athlete.caseId}`, 'GET', undefined, 403);
    const athleteView = await api(admin, `${ROOT}/requests/${athlete.caseId}`);
    expect(JSON.stringify(athleteView)).not.toContain('comprovacao-restrita-isolada'); expect(athleteView.state).toBe('RESPONDED');
    await restorePause(); restorePause = null;
    await page.reload(); await expect(page.getByText(notice.caseId, { exact: true }).first()).toBeVisible();
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    expect(await admin.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
    await page.locator('.console-nav__account').click(); await page.getByRole('button', { name: 'Sair', exact: true }).click();
    await expect(page.getByRole('heading', { name: 'Privacidade da conta', exact: true })).toHaveCount(0);
    await expect(page.getByText(notice.caseId, { exact: true })).toHaveCount(0);
    await api(page, `${ROOT}/me/cases`, 'GET', undefined, 401); expect(errors).toEqual([]);
  } finally {
    try { if (restorePause) await restorePause(); }
    finally { await Promise.all(contexts.map(context => context.close())); }
  }
});
