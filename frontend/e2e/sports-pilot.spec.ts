import { expect, test, type Page } from '@playwright/test';

const FIRST_EVENT_ID = '11111111-1111-4111-8111-111111111111';

function eventAt(hoursFromNow: number, id: string, left: string, right: string) {
  return {
    eventId: id,
    competitionId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
    editionId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    competitionName: 'Jogos Universitários',
    editionName: 'Edição 2026',
    title: `${left} contra ${right}`,
    format: 'HEAD_TO_HEAD',
    scheduledAt: new Date(Date.now() + hoursFromNow * 60 * 60 * 1_000).toISOString(),
    zoneId: 'America/Sao_Paulo',
    venueState: 'LOCAL_A_DEFINIR',
    slots: [
      { slotNo: 1, displayLabel: left },
      { slotNo: 2, displayLabel: right },
    ],
  };
}

async function mockPublicAgenda(page: Page): Promise<{ sessionCalls: () => number }> {
  let sessionCallCount = 0;
  await page.route('**/api/v1/public/sports/events?**', async (route) => {
    const items = [
      eventAt(1, FIRST_EVENT_ID, 'Engenharia', 'Direito'),
      eventAt(2, '22222222-2222-4222-8222-222222222222', 'Medicina', 'Computação'),
      eventAt(3, '33333333-3333-4333-8333-333333333333', 'Economia', 'Biologia'),
    ];
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        items,
        page: 0,
        size: 50,
        totalElements: items.length,
        totalPages: 1,
        hasNext: false,
      }),
    });
  });
  await page.route('**/api/v1/session', async (route) => {
    sessionCallCount += 1;
    await route.fulfill({ status: 401, contentType: 'application/json', body: '{}' });
  });
  await page.route('**/api/v1/profile/me', async (route) => {
    sessionCallCount += 1;
    await route.fulfill({ status: 401, contentType: 'application/json', body: '{}' });
  });
  return { sessionCalls: () => sessionCallCount };
}

test.describe('@03-03-home-v Arena pública variante V', () => {
  test('desktop consome a agenda real, alterna somente a composição e permanece pública', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    const calls = await mockPublicAgenda(page);
    await page.goto('/');

    await expect(page.getByRole('heading', { level: 1, name: /Hoje em quadra.*Engenharia × Direito/u })).toBeAttached();
    await expect(page.locator('app-event-row')).toHaveCount(3);
    await expect(page.getByText('Engenharia × Direito').first()).toBeVisible();
    await expect(page.getByText('Local a definir').first()).toBeVisible();
    await expect(page.getByText('Agenda publicada', { exact: true })).toBeVisible();
    expect(calls.sessionCalls()).toBe(0);

    const slides = page.locator('.hero-slide');
    await expect(slides.nth(0)).toHaveAttribute('aria-hidden', 'false');
    await expect(slides.nth(1)).toHaveAttribute('aria-hidden', 'true');
    await expect(slides.nth(1)).toHaveAttribute('inert', '');
    await expect(slides.nth(1).getByRole('link', { name: 'Abrir evento', includeHidden: true })).toHaveAttribute('tabindex', '-1');

    await page.waitForTimeout(5_650);
    await expect(slides.nth(0)).toHaveAttribute('aria-hidden', 'true');
    await expect(slides.nth(1)).toHaveAttribute('aria-hidden', 'false');
    await expect(slides.nth(1).getByRole('link', { name: 'Abrir evento' })).toHaveAttribute(
      'href',
      `#agenda-event-${FIRST_EVENT_ID}`,
    );

    const motionControl = page.getByRole('button', { name: 'Pausar destaques' });
    await motionControl.focus();
    await expect(motionControl).toBeFocused();
    await motionControl.click();
    await expect(page.getByRole('button', { name: 'Retomar destaques' })).toHaveAttribute('aria-pressed', 'true');

    await page.getByRole('button', { name: 'Filtrar agenda' }).click();
    await expect(page.getByLabel('Competição')).toBeVisible();
    await expect(page.getByLabel('Edição')).toBeVisible();
    await page.getByLabel('Competição').selectOption('aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa');
    await page.getByLabel('Edição').selectOption('bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb');
    await page.getByRole('button', { name: 'Aplicar filtros' }).click();
    await expect(page).toHaveURL(/competitionId=aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/u);
    await expect(page).toHaveURL(/editionId=bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/u);
    await page.getByRole('button', { name: 'Próximos' }).click();
    await expect(page).toHaveURL(/period=upcoming/u);
    await expect(page).toHaveURL(/competitionId=aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa/u);
    await expect(page).toHaveURL(/editionId=bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb/u);

    await page.getByRole('link', { name: 'Competições' }).click();
    await expect(page).toHaveURL(/#agenda$/u);
    await expect.poll(() => page.locator('#agenda').evaluate((element) =>
      Math.abs(element.getBoundingClientRect().top),
    )).toBeLessThan(18);
  });

  test('320px usa reflow sem barra horizontal, preserva a faixa e fixa o hero com movimento reduzido', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.setViewportSize({ width: 320, height: 800 });
    const calls = await mockPublicAgenda(page);
    await page.goto('/');
    await expect(page.locator('app-event-row')).toHaveCount(3);

    const scrollProof = await page.evaluate(() => ({
      innerWidth: window.innerWidth,
      clientWidth: document.documentElement.clientWidth,
      scrollWidth: Math.max(document.documentElement.scrollWidth, document.body.scrollWidth),
    }));
    expect(scrollProof.innerWidth).toBe(320);
    expect(scrollProof.clientWidth).toBe(320);
    expect(scrollProof.scrollWidth).toBeLessThanOrEqual(320);

    const ribbon = await page.locator('app-sport-ribbon .ribbon').boundingBox();
    const agendaTitle = await page.locator('#agenda-title').boundingBox();
    expect(ribbon).not.toBeNull();
    expect(agendaTitle).not.toBeNull();
    expect((ribbon?.y ?? 0) + (ribbon?.height ?? 0)).toBeLessThan(agendaTitle?.y ?? 0);

    const slides = page.locator('.hero-slide');
    await page.waitForTimeout(5_650);
    await expect(slides.nth(0)).toHaveAttribute('aria-hidden', 'false');
    await expect(slides.nth(1)).toHaveAttribute('aria-hidden', 'true');
    await expect(page.getByRole('link', { name: 'Competições' })).toBeVisible();
    await expect(page.getByRole('link', { name: 'Entrar' })).toBeVisible();
    await expect(page.getByRole('button', { name: 'Menu' })).toHaveCount(0);
    const motionControl = await page.getByRole('button', { name: 'Pausar destaques' }).boundingBox();
    expect(motionControl).not.toBeNull();
    expect((motionControl?.x ?? 0) + (motionControl?.width ?? 0)).toBeLessThanOrEqual(320);
    expect(calls.sessionCalls()).toBe(0);
  });
});

test('@03-11-public-p1-p6 abre os detalhes públicos por URL direta e navega entre identidades', async ({ page }) => {
  const eventId = FIRST_EVENT_ID;
  await page.route('**/api/v1/public/sports/competitions/comp-public', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ competitionId: 'comp-public', name: 'Jogos UFU', slug: 'jogos-ufu', editions: [], categories: [], phases: [], venues: [], teams: [{ teamId: 'team-public', organizationId: 'org-public', organizationName: 'Atléticas', editionId: 'edition-public', sportId: 'sport-public', sportName: 'Futebol', categoryId: 'category-public', categoryName: 'Livre', name: 'Engenharia', slug: 'engenharia' }], recentEvents: [] }) });
  });
  await page.route(`**/api/v1/public/sports/events/${eventId}`, async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ publicationId: 'publication-public', eventId, competitionId: 'comp-public', editionId: 'edition-public', versionNo: 1, competitionName: 'Jogos UFU', editionName: '2026', title: 'Engenharia contra Direito', format: 'HEAD_TO_HEAD', scheduledAt: new Date(Date.now() + 3_600_000).toISOString(), zoneId: 'America/Sao_Paulo', venueState: 'LOCAL_A_DEFINIR', sourceReference: 'sumula-publica', publishedAt: new Date().toISOString(), slots: [{ slotNo: 1, displayLabel: 'Engenharia' }, { slotNo: 2, displayLabel: 'Direito' }], sourceHistory: [] }) });
  });
  await page.route('**/api/v1/public/sports/organizations/org-public', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ organizationId: 'org-public', name: 'Atléticas', slug: 'atleticas', teams: [], rosterMembers: [], events: [] }) });
  });
  await page.route('**/api/v1/public/sports/teams/team-public', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ teamId: 'team-public', organizationId: 'org-public', organizationName: 'Atléticas', editionId: 'edition-public', editionName: '2026', competitionId: 'comp-public', competitionName: 'Jogos UFU', sportId: 'sport-public', sportName: 'Futebol', categoryId: 'category-public', categoryName: 'Livre', name: 'Engenharia', slug: 'engenharia', events: [] }) });
  });
  await page.route('**/api/v1/public/sports/athletes/athlete-public', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ athleteId: 'athlete-public', name: 'Ana', slug: 'ana', teams: [], events: [], stats: { totalParticipations: 0, totalWins: 0, totalLosses: 0 } }) });
  });

  await page.goto('/competicoes/comp-public');
  await expect(page.getByRole('heading', { level: 1, name: 'Jogos UFU' })).toBeVisible();
  await page.getByRole('link', { name: 'Engenharia' }).first().click();
  await expect(page).toHaveURL(/\/equipes\/team-public$/u);
  await expect(page.getByRole('heading', { level: 1, name: 'Engenharia' })).toBeVisible();
  await page.goto('/organizacoes/org-public');
  await expect(page.getByRole('heading', { level: 1, name: 'Atléticas' })).toBeVisible();
  await page.goto('/atletas/athlete-public');
  await expect(page.getByRole('heading', { level: 1, name: 'Ana' })).toBeVisible();
  await page.goto(`/eventos/${eventId}`);
  await expect(page.getByRole('heading', { level: 1, name: 'Engenharia × Direito' })).toBeVisible();
  await expect(page.getByText('sumula-publica').first()).toBeVisible();
});
