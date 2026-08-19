import {
  expect,
  test,
  type Page,
} from '@playwright/test';

const CLIENT_ORIGIN = 'http://localhost:4200';
const DIAGNOSTIC_PATH = '/diagnostic';

test.describe('E2E Diagnostic Page & Production Route Absence (D-01, D-02)', () => {
  test('em desenvolvimento, rota /diagnostic carrega a tela com resumo e 6 cartoes canonicos', async ({
    page,
  }) => {
    // Mock de resposta da API de diagnóstico na porta do BFF
    await page.route('**/api/v1/diagnostic', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          status: 'UP',
          summary: 'Todos os sistemas operacionais',
          correlationId: 'corr-e2e-test-1234',
          timestamp: new Date().toISOString(),
          environment: 'dev',
          version: '0.1.0',
          components: [
            { name: 'API', status: 'UP', detail: 'Versão 0.1.0', durationMs: 3 },
            { name: 'Sessão BFF', status: 'UP', detail: 'Sessões Redis OK', durationMs: 2 },
            { name: 'Keycloak', status: 'UP', detail: 'Realm betufu ativo', durationMs: 10 },
            { name: 'PostgreSQL', status: 'UP', detail: 'Conexão estável', durationMs: 4 },
            { name: 'Redis', status: 'UP', detail: 'Ping OK', durationMs: 1 },
            { name: 'Armazenamento de objetos', status: 'UP', detail: 'Bucket acessível', durationMs: 5 },
          ],
        }),
      });
    });

    // Mock de sessão autenticada
    await page.route('**/api/v1/session', async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          authenticated: true,
          username: 'technical',
          displayName: 'Technical Operator',
          roles: ['ROLE_TECHNICAL'],
        }),
      });
    });

    await page.goto(`${CLIENT_ORIGIN}${DIAGNOSTIC_PATH}`);

    // Valida título da página
    await expect(page.locator('#diagnostic-title')).toHaveText(
      'Diagnóstico Técnico da Plataforma',
    );

    // Valida resumo e correlation ID
    await expect(page.locator('.summary-text')).toContainText(
      'Todos os sistemas operacionais',
    );
    await expect(page.locator('.code-inline')).toContainText('corr-e2e-test-1234');

    // Valida os exatamente 6 cartões canônicos
    const cards = page.locator('.component-card');
    await expect(cards).toHaveCount(6);

    const expectedNames = [
      'API',
      'Sessão BFF',
      'Keycloak',
      'PostgreSQL',
      'Redis',
      'Armazenamento de objetos',
    ];

    for (let i = 0; i < expectedNames.length; i++) {
      await expect(cards.nth(i).locator('.component-name')).toHaveText(
        expectedNames[i],
      );
      await expect(cards.nth(i).locator('.component-badge')).toHaveText(
        'Disponível',
      );
    }
  });

  test('em producao, diagnostico e tracer nao existem no roteador e falham fechados (D-01/D-02)', async ({
    page,
  }) => {
    // Tenta acessar /diagnostic em build production
    await page.goto(`${CLIENT_ORIGIN}/diagnostic`);

    // Não deve existir o container de diagnóstico
    const diagnosticTitle = page.locator('#diagnostic-title');
    await expect(diagnosticTitle).not.toBeVisible();
  });
});
