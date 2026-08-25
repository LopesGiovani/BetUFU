import { expect, test } from '@playwright/test';

const CLIENT_ORIGIN = 'http://localhost:4200';
const OPERATIONS_PATH = '/operacao/painel';

test.describe('E2E Operations Panel Surface S4 (OPER-06, S4 Responsividade 320px e Motivo Literal)', () => {
  test('em viewport mobile 320px, renderiza painel sem rolagem horizontal e preserva motivo de auditoria com caracteres < e > literalmente', async ({
    page,
  }) => {
    // 1. Configura viewport estrito para 320px de largura
    await page.setViewportSize({ width: 320, height: 640 });

    const literalReason = 'Correção administrativa <urgente> com delimitadores <teste> e símbolos < > literais.';

    // 2. Intercepta exclusivamente GET /api/v1/operations/panel
    await page.route('**/api/v1/operations/panel', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          queriedAt: '2026-08-20T14:30:00.000Z',
          timeWindow: {
            since: '2026-08-13T00:00:00.000Z',
            until: '2026-08-20T14:30:00.000Z',
          },
          rejectedEnrollmentsCount: {
            duplicateCount: 3,
            attemptLimitCount: 1,
            totalCount: 4,
          },
          administrativeCorrections: {
            recentEvents: [
              {
                eventId: 'evt-corr-e2e-01',
                occurredAt: '2026-08-19T10:00:00.000Z',
                actorIdentityId: 'admin-id-01',
                actorRole: 'administrador geral',
                targetIdentityId: 'user-id-01',
                targetNickname: 'aluno_ufu_long_nickname',
                action: 'CORRECAO_MATRICULA',
                outcome: 'PERMITIDO',
                hasReason: true,
                reason: literalReason,
              },
            ],
          },
          roleChanges: {
            recentEvents: [
              {
                eventId: 'evt-role-e2e-01',
                occurredAt: '2026-08-19T11:00:00.000Z',
                actorIdentityId: 'admin-id-01',
                actorRole: 'administrador geral',
                targetIdentityId: 'admin-id-01',
                targetNickname: 'admin_geral_01',
                action: 'AUTOCONCESSAO_PAPEL',
                outcome: 'BLOQUEADO',
                hasReason: false,
              },
            ],
          },
          unregisteredEnrollmentsCount: {
            count: 27,
          },
          unavailableBlocks: [],
        }),
      });
    });

    // 3. Intercepta GET /api/v1/session para garantir sessão autenticada
    await page.route('**/api/v1/session', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          authenticated: true,
          username: 'admin',
          displayName: 'Administrador Geral',
          roles: ['ROLE_ADMINISTRADOR_GERAL'],
        }),
      });
    });

    // 4. Navega para a rota de produção /operacao/painel
    await page.goto(`${CLIENT_ORIGIN}${OPERATIONS_PATH}`);

    // 5. Valida título e presença dos 4 cartões
    const title = page.locator('#operations-panel-title');
    await expect(title).toBeVisible();
    await expect(title).toHaveText('Painel operacional');

    const cards = page.locator('.panel-card');
    await expect(cards).toHaveCount(4);

    // 6. Prova ausência de rolagem horizontal em 320px
    const scrollProof = await page.evaluate(() => {
      const doc = document.documentElement;
      const body = document.body;
      const scrollWidth = Math.max(doc.scrollWidth, body.scrollWidth);
      const clientWidth = doc.clientWidth;
      return {
        clientWidth,
        scrollWidth,
        hasHorizontalScroll: scrollWidth > clientWidth,
        windowInnerWidth: window.innerWidth,
      };
    });

    expect(scrollProof.clientWidth).toBe(320);
    expect(scrollProof.windowInnerWidth).toBe(320);
    expect(scrollProof.hasHorizontalScroll).toBe(false);
    expect(scrollProof.scrollWidth).toBeLessThanOrEqual(320);

    // 7. Prova preservação literal do motivo de auditoria com < e >
    const reasonElement = page.locator('.reason-text');
    await expect(reasonElement).toBeVisible();
    await expect(reasonElement).toHaveText(literalReason);

    // 8. O papel contextualiza, mas não pode mascarar quem executou a ação auditada.
    await expect(page.locator('.corrections-card .audit-actor')).toContainText('admin-id-01');
    await expect(page.locator('.roles-card .audit-actor').first()).toContainText('admin-id-01');

    // Valida que o DOM renderizou texto puro sem criar tags HTML internas
    const childTagsCount = await reasonElement.evaluate((el) => el.children.length);
    expect(childTagsCount).toBe(0);
  });
});
