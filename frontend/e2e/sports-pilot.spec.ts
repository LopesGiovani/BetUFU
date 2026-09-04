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
      `/eventos/${FIRST_EVENT_ID}`,
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

const OWNER_PROPOSAL = {
  proposalId: 'proposal-03-26',
  athleteId: 'athlete-03-26',
  athleteName: 'Atleta Um',
  athleteSlug: 'atleta-um',
  status: 'PENDENTE',
  createdAt: '2026-09-03T10:00:00Z',
  updatedAt: '2026-09-03T10:00:00Z',
  expiresAt: '2026-10-03T10:00:00Z',
  decidedAt: null,
};

async function mockOwnerLink(
  page: Page,
  proposalStatus: 200 | 401 | 403 = 200,
): Promise<{ proposalRequestUrls: string[] }> {
  const proposalRequestUrls: string[] = [];
  await page.route('**/api/v1/session', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        authenticated: true,
        displayName: 'Jogador UFU',
        idleTimeout: 'PT15M',
        absoluteTimeout: null,
        lastValidActivityAt: new Date().toISOString(),
      }),
    });
  });
  await page.route('**/api/v1/profile/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        identityId: 'identity-owner-03-26',
        nickname: 'jogador_ufu',
        nicknameUpdatedAt: '2026-09-03T10:00:00.000Z',
        matriculaStatus: 'REGISTRADA',
        favoriteOrganizationId: null,
      }),
    });
  });
  await page.route('**/api/v1/me/sports/athlete-links/proposal', async (route) => {
    proposalRequestUrls.push(route.request().url());
    if (proposalStatus !== 200) {
      await route.fulfill({ status: proposalStatus, contentType: 'application/json', body: '{}' });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify(OWNER_PROPOSAL),
    });
  });
  return { proposalRequestUrls };
}

test.describe('@03-26-owner-event owner e deep link canonico', () => {
  test('segue CTA da agenda, abre o evento completo e preserva o deep link no refresh', async ({ page }) => {
    await mockPublicAgenda(page);
    await page.route(`**/api/v1/public/sports/events/${FIRST_EVENT_ID}`, async (route) => {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          publicationId: 'publication-03-26',
          eventId: FIRST_EVENT_ID,
          competitionId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
          editionId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
          versionNo: 1,
          competitionName: 'Jogos Universitários',
          editionName: 'Edição 2026',
          title: 'Engenharia contra Direito',
          format: 'HEAD_TO_HEAD',
          scheduledAt: new Date(Date.now() + 3_600_000).toISOString(),
          zoneId: 'America/Sao_Paulo',
          venueState: 'LOCAL_A_DEFINIR',
          sourceReference: 'sumula-03-26',
          publishedAt: new Date().toISOString(),
          slots: [
            { slotNo: 1, displayLabel: 'Engenharia' },
            { slotNo: 2, displayLabel: 'Direito' },
          ],
          sourceHistory: [],
        }),
      });
    });

    await page.goto('/');
    const eventLink = page.locator('app-event-row').first().getByRole('link');
    await expect(eventLink).toHaveAttribute('href', `/eventos/${FIRST_EVENT_ID}`);
    await eventLink.click();
    await expect(page).toHaveURL(`/eventos/${FIRST_EVENT_ID}`);
    await expect(page.getByRole('heading', { level: 1, name: /Engenharia/u })).toBeVisible();
    await expect(page.getByText('sumula-03-26').first()).toBeVisible();

    await page.reload();
    await expect(page).toHaveURL(`/eventos/${FIRST_EVENT_ID}`);
    await expect(page.getByRole('heading', { level: 1, name: /Engenharia/u })).toBeVisible();
    await expect(page.getByText('sumula-03-26').first()).toBeVisible();
  });

  test('owner abre a proposta pela identidade atual, sem proposalId, e o refresh preserva a tela', async ({ page }) => {
    const probe = await mockOwnerLink(page);

    await page.goto('/conta/perfil');
    const proposalLink = page.locator('.console-nav a[href="/conta/vinculo-atleta"]');
    await expect(proposalLink).toBeVisible();
    await proposalLink.click();
    await expect(page).toHaveURL('/conta/vinculo-atleta');
    await expect(page).not.toHaveURL(/proposalId/iu);
    await expect(page.getByRole('heading', { level: 1, name: /Proposta de/u })).toBeVisible();
    await expect(page.getByRole('heading', { level: 2, name: 'Atleta Um' })).toBeVisible();
    await expect(page.getByRole('button', { name: /Aceitar/u })).toBeVisible();
    expect(probe.proposalRequestUrls).toHaveLength(1);
    expect(probe.proposalRequestUrls[0]).toMatch(/\/api\/v1\/me\/sports\/athlete-links\/proposal$/u);
    expect(probe.proposalRequestUrls[0]).not.toContain('proposalId');

    await page.reload();
    await expect(page).toHaveURL('/conta/vinculo-atleta');
    await expect(page.getByRole('heading', { level: 2, name: 'Atleta Um' })).toBeVisible();
    await expect(page.locator('body')).not.toContainText('proposal-03-26');
    expect(probe.proposalRequestUrls).toHaveLength(2);
    expect(probe.proposalRequestUrls.every((url) => !url.includes('proposalId'))).toBe(true);
  });

  for (const status of [401, 403] as const) {
    test(`falha fechado com ${status} sem renderizar dados da proposta`, async ({ page }) => {
      const probe = await mockOwnerLink(page, status);

      await page.goto('/conta/vinculo-atleta');
      await expect(page).toHaveURL('/conta/vinculo-atleta');
      await expect(page.getByRole('heading', { level: 2, name: /N.o foi poss.vel carregar/u })).toBeVisible();
      await expect(page.locator('.proposal-card')).toHaveCount(0);
      await expect(page.locator('body')).not.toContainText('Atleta Um');
      await expect(page.locator('body')).not.toContainText('proposal-03-26');
      await expect(page.locator('body')).toContainText(status === 401 ? /sess.o expirou/u : /n.o tem acesso/u);
      expect(probe.proposalRequestUrls).toHaveLength(1);
      expect(probe.proposalRequestUrls[0]).not.toContain('proposalId');
    });
  }
});

type PersonalConsoleProbe = {
  readonly sensitiveMatricula: string;
  readonly putRequests: Array<{ url: string; body: string | null }>;
  readonly consoleMessages: string[];
};

async function mockPersonalConsole(page: Page): Promise<PersonalConsoleProbe> {
  const sensitiveMatricula = '2026123456';
  const putRequests: Array<{ url: string; body: string | null }> = [];
  const consoleMessages: string[] = [];
  let enrollmentRegistered = false;

  page.on('request', (request) => {
    if (request.method() === 'PUT') {
      putRequests.push({ url: request.url(), body: request.postData() });
    }
  });
  page.on('console', (message) => consoleMessages.push(message.text()));

  await page.route('**/api/v1/session', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        authenticated: true,
        displayName: 'Jogador UFU',
        idleTimeout: 'PT15M',
        absoluteTimeout: null,
        lastValidActivityAt: new Date().toISOString(),
      }),
    });
  });
  await page.route('**/api/v1/profile/me', async (route) => {
    if (route.request().method() === 'PUT') {
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({
          identityId: 'identity-personal-console',
          nickname: 'jogador_ufu',
          nicknameUpdatedAt: new Date().toISOString(),
          matriculaStatus: enrollmentRegistered ? 'REGISTRADA' : 'NAO_REGISTRADA',
          favoriteOrganizationId: null,
        }),
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        identityId: 'identity-personal-console',
        nickname: 'jogador_ufu',
        nicknameUpdatedAt: '2026-08-21T10:00:00.000Z',
        matriculaStatus: enrollmentRegistered ? 'REGISTRADA' : 'NAO_REGISTRADA',
        favoriteOrganizationId: null,
      }),
    });
  });
  await page.route('**/api/v1/profile/me/favorite-organization', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        identityId: 'identity-personal-console',
        nickname: 'jogador_ufu',
        nicknameUpdatedAt: '2026-08-21T10:00:00.000Z',
        matriculaStatus: enrollmentRegistered ? 'REGISTRADA' : 'NAO_REGISTRADA',
        favoriteOrganizationId: null,
      }),
    });
  });
  await page.route('**/api/v1/operations/sports/organizations', async (route) => {
    await route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
  });
  await page.route('**/api/v1/enrollment/me', async (route) => {
    if (route.request().method() === 'PUT') {
      enrollmentRegistered = true;
      await route.fulfill({
        status: 200,
        contentType: 'application/json',
        body: JSON.stringify({ status: 'REGISTRADA', createdAt: '2026-08-21T12:00:00.000Z' }),
      });
      return;
    }
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        status: enrollmentRegistered ? 'REGISTRADA' : 'NAO_REGISTRADA',
        createdAt: enrollmentRegistered ? '2026-08-21T12:00:00.000Z' : null,
      }),
    });
  });

  return { sensitiveMatricula, putRequests, consoleMessages };
}

async function tabUntil(page: Page, selector: string, attempts = 32): Promise<void> {
  for (let index = 0; index < attempts; index += 1) {
    if (await page.locator(selector).evaluate((element) => element === document.activeElement)) {
      return;
    }
    await page.keyboard.press('Tab');
  }
  throw new Error(`Elemento ${selector} não foi alcançado pelo teclado.`);
}

async function expectMeasuredAccessibility(page: Page, maxScrollMultiplier = 1): Promise<void> {
  const dimensions = await page.evaluate(() => ({
    innerWidth: window.innerWidth,
    clientWidth: document.documentElement.clientWidth,
    scrollWidth: Math.max(document.documentElement.scrollWidth, document.body.scrollWidth),
  }));
  expect(dimensions.scrollWidth).toBeLessThanOrEqual(dimensions.innerWidth * maxScrollMultiplier);
  expect(dimensions.clientWidth).toBeLessThanOrEqual(dimensions.innerWidth);

  const undersizedControls = await page
    .locator('.event-hero a, .event-row a, button, input, select, textarea, [role="button"]')
    .evaluateAll((elements) =>
      elements
        .filter((element) => {
          const style = window.getComputedStyle(element);
          return (
            style.display !== 'none' &&
            style.visibility !== 'hidden' &&
            element.getAttribute('tabindex') !== '-1'
          );
        })
        .filter((element) => {
          const rect = element.getBoundingClientRect();
          return rect.width > 0 && rect.height > 0 && (rect.width < 44 || rect.height < 44);
        })
        .map((element) => element.outerHTML.slice(0, 120)),
    );
  expect(undersizedControls).toEqual([]);

  await page.keyboard.press('Tab');
  const focusProof = await page.evaluate(() => {
    const active = document.activeElement as HTMLElement | null;
    if (!active || active === document.body) {
      return null;
    }
    const style = window.getComputedStyle(active);
    const outlineWidth = Number.parseFloat(style.outlineWidth) || 0;
    const shadowSpread = style.boxShadow
      .split(',')
      .map((shadow) => shadow.trim().match(/(-?\d+(?:\.\d+)?)px\s*$/u)?.[1])
      .map((value) => (value ? Number.parseFloat(value) : 0))
      .reduce((max, value) => Math.max(max, value), 0);
    return {
      tag: active.tagName,
      outlineWidth,
      shadowSpread,
      hasIndicator: outlineWidth >= 3 || shadowSpread >= 3,
    };
  });
  expect(focusProof).not.toBeNull();
  expect(focusProof?.hasIndicator).toBe(true);

  const contrastFailures = await page.evaluate(() => {
    type Rgba = [number, number, number, number];
    const parseColor = (value: string): Rgba | null => {
      const match = value.match(/rgba?\(([^)]+)\)/u);
      if (!match) return null;
      const channels = match[1].split(',').map((part) => Number.parseFloat(part.trim()));
      if (channels.length < 3 || channels.some((channel) => Number.isNaN(channel))) return null;
      return [channels[0], channels[1], channels[2], channels[3] ?? 1];
    };
    const composite = (foreground: Rgba, background: Rgba): Rgba => {
      const alpha = foreground[3] + background[3] * (1 - foreground[3]);
      if (alpha === 0) return [0, 0, 0, 0];
      return [
        (foreground[0] * foreground[3] + background[0] * background[3] * (1 - foreground[3])) / alpha,
        (foreground[1] * foreground[3] + background[1] * background[3] * (1 - foreground[3])) / alpha,
        (foreground[2] * foreground[3] + background[2] * background[3] * (1 - foreground[3])) / alpha,
        alpha,
      ];
    };
    const luminance = (color: Rgba): number =>
      color
        .slice(0, 3)
        .map((channel) => channel / 255)
        .map((channel) => (channel <= 0.03928 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4))
        .reduce((sum, channel, index) => sum + channel * [0.2126, 0.7152, 0.0722][index], 0);

    const failures: string[] = [];
    const candidates = Array.from(document.querySelectorAll<HTMLElement>('body *'))
      .filter((element) => {
        const style = window.getComputedStyle(element);
        const rect = element.getBoundingClientRect();
        return Boolean(element.textContent?.trim())
          && style.display !== 'none'
          && style.visibility !== 'hidden'
          && rect.width > 0
          && rect.height > 0
          && element.children.length === 0;
      })
      .slice(0, 120);

    for (const element of candidates) {
      const style = window.getComputedStyle(element);
      const foreground = parseColor(style.color);
      if (!foreground) continue;
      let background: Rgba | null = null;
      let current: HTMLElement | null = element;
      while (current && !background) {
        const candidate = parseColor(window.getComputedStyle(current).backgroundColor);
        if (candidate && candidate[3] >= 0.99) background = candidate;
        current = current.parentElement;
      }
      if (!background) continue;

      const blended = composite(foreground, background);
      const ratio = (Math.max(luminance(blended), luminance(background)) + 0.05)
        / (Math.min(luminance(blended), luminance(background)) + 0.05);
      const fontSize = Number.parseFloat(style.fontSize) || 16;
      const largeText = fontSize >= 24 || (fontSize >= 18.66 && Number.parseInt(style.fontWeight, 10) >= 700);
      const required = largeText ? 3 : 4.5;
      if (ratio + 0.01 < required) {
        const label = element.textContent?.trim() ?? '';
        failures.push(`${element.tagName}:${label.slice(0, 50)}=${ratio.toFixed(2)}`);
      }
    }
    return failures.slice(0, 8);
  });
  expect(contrastFailures).toEqual([]);
}

async function expectPersonalLayout(page: Page): Promise<void> {
  await expectMeasuredAccessibility(page);
  expect(await page.locator('body').innerText()).not.toMatch(/tracer|diagnostic|webgl/iu);
}

async function expectHeroSemantics(page: Page): Promise<void> {
  const slides = page.locator('.hero-slide');
  await expect(slides).toHaveCount(2);
  await expect(slides.nth(0)).toHaveAttribute('aria-hidden', 'false');
  await expect(slides.nth(1)).toHaveAttribute('aria-hidden', 'true');
  await expect(slides.nth(1)).toHaveAttribute('inert', '');
  await expect(slides.nth(1).getByRole('link', { name: 'Abrir evento', includeHidden: true })).toHaveAttribute('tabindex', '-1');
  const motionControl = page.getByRole('button', { name: 'Pausar destaques' });
  await motionControl.focus();
  await expect(motionControl).toBeFocused();
  await motionControl.click();
  await expect(page.getByRole('button', { name: 'Retomar destaques' })).toHaveAttribute('aria-pressed', 'true');
}

test.describe('@03-17-visual-matrix UI medida em dois navegadores', () => {
  test('desktop prova semântica do hero, foco, alvos e contraste', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await mockPublicAgenda(page);
    await page.goto('/');
    await expect(page.locator('app-event-row')).toHaveCount(3);
    await expectMeasuredAccessibility(page);
    await expectHeroSemantics(page);
  });

  test('320px com reduced motion mantém reflow, sem overflow e com controles mensuráveis', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.setViewportSize({ width: 320, height: 800 });
    await mockPublicAgenda(page);
    await page.goto('/');
    await expect(page.locator('app-event-row')).toHaveCount(3);
    await expectMeasuredAccessibility(page);
    await expectHeroSemantics(page);
  });

  test('200% automatizado mantém a superfície dentro do limite de reflow', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await mockPublicAgenda(page);
    await page.goto('/');
    await page.evaluate(() => {
      document.documentElement.style.zoom = '2';
    });
    await expect(page.locator('app-event-row')).toHaveCount(3);
    await expectMeasuredAccessibility(page, 2);
  });
});

test.describe('@03-19-personal-console Console pessoal', () => {
  test('desktop percorre perfil e matrícula, preserva teclado e não vaza o valor bruto', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    const probe = await mockPersonalConsole(page);

    await page.goto('/conta/perfil');
    await expect(page.locator('.console-header--personal')).toBeVisible();
    await expect(page.locator('.profile-title')).toHaveText('Seu perfil');
    await expectPersonalLayout(page);
    await tabUntil(page, 'a.skip-link');
    await expect(page.locator('a.skip-link')).toBeFocused();

    await page.locator('.console-nav a[href="/conta/matricula"]').click();
    await expect(page).toHaveURL(/\/conta\/matricula$/u);
    await expect(page.locator('.enrollment-title')).toBeVisible();
    await expectPersonalLayout(page);
    await tabUntil(page, '#matricula-input');
    await expect(page.locator('#matricula-input')).toBeFocused();

    await page.locator('#matricula-input').fill(probe.sensitiveMatricula);
    await page.getByRole('button', { name: /Registrar matr/ }).click();
    await expect(page.locator('.success-feedback')).toBeVisible();
    await expect(page.locator('body')).not.toContainText(probe.sensitiveMatricula);
    await expect(page).not.toHaveURL(new RegExp(probe.sensitiveMatricula, 'u'));

    const storage = await page.evaluate(() =>
      JSON.stringify({
        local: Object.entries(localStorage),
        session: Object.entries(sessionStorage),
      }),
    );
    expect(storage).not.toContain(probe.sensitiveMatricula);
    expect(probe.putRequests).toHaveLength(1);
    expect(probe.putRequests[0]?.url).toMatch(/\/api\/v1\/enrollment\/me$/u);
    expect(probe.putRequests[0]?.body).toContain(probe.sensitiveMatricula);
    expect(probe.consoleMessages.join('\n')).not.toContain(probe.sensitiveMatricula);
  });

  test('320px mantém reflow, foco e alvos sem overflow nas três rotas pessoais', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.setViewportSize({ width: 320, height: 800 });
    const probe = await mockPersonalConsole(page);

    for (const path of ['/conta/perfil/completar', '/conta/perfil', '/conta/matricula']) {
      await page.goto(path);
      await expect(page.locator('.console-header--personal')).toBeVisible();
      await expectPersonalLayout(page);
    }

    await page.goto('/conta/perfil/completar');
    await tabUntil(page, '#nickname-input');
    await expect(page.locator('#nickname-input')).toBeFocused();
    expect(probe.putRequests).toHaveLength(0);
  });
});

async function mockLegacyOperations(page: Page): Promise<void> {
  await page.route('**/api/v1/session', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({ authenticated: true, displayName: 'Administrador Geral' }),
    });
  });
  await page.route('**/api/v1/profile/me', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        identityId: 'admin-id-01',
        nickname: 'admin_geral_01',
        matriculaStatus: 'REGISTRADA',
        favoriteOrganizationId: null,
      }),
    });
  });
  await page.route('**/api/v1/operations/capabilities', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        capabilities: ['VIEW_OPERATIONAL_PANEL', 'MANAGE_ROLES', 'CORRECT_ENROLLMENT'],
      }),
    });
  });
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
        rejectedEnrollmentsCount: { duplicateCount: 1, attemptLimitCount: 0, totalCount: 1 },
        administrativeCorrections: { recentEvents: [] },
        roleChanges: { recentEvents: [] },
        unregisteredEnrollmentsCount: { count: 2 },
        unavailableBlocks: [],
      }),
    });
  });
  await page.route('**/api/v1/operations/roles/*', async (route) => {
    await route.fulfill({
      status: 200,
      contentType: 'application/json',
      body: JSON.stringify({
        identityId: 'bbbbbbbb-2222-4222-8222-222222222222',
        nickname: 'usuario_alvo',
        roles: [
          { role: 'ADMINISTRADOR_GERAL', status: 'NAO_CONCEDIDO' },
          { role: 'ADMINISTRADOR_COMPETICAO', status: 'NAO_CONCEDIDO' },
          { role: 'MODERADOR', status: 'NAO_CONCEDIDO' },
        ],
      }),
    });
  });
  await page.route('**/api/v1/operations/enrollments/correction', async (route) => {
    await route.fulfill({ status: 403, contentType: 'application/json', body: '{}' });
  });
}

test.describe('@03-20-legacy-operations Console operacional legado', () => {
  test('desktop percorre painel, papéis e correção sob o Console V', async ({ page }) => {
    await page.setViewportSize({ width: 1280, height: 800 });
    await mockLegacyOperations(page);

    await page.goto('/operacao/painel');
    await expect(page.locator('.console-header:not(.console-header--personal)')).toBeVisible();
    await expect(page.locator('#operations-panel-title')).toHaveText('Painel operacional');
    await expect(page.locator('.panel-card')).toHaveCount(4);

    await page.goto('/operacao/papeis');
    await expect(page.locator('.console-header:not(.console-header--personal)')).toBeVisible();
    await expect(page.locator('.page-title')).toHaveText('Papéis operacionais');

    await page.goto('/operacao/matriculas/corrigir');
    await expect(page.locator('.console-header:not(.console-header--personal)')).toBeVisible();
    await expect(page.locator('.page-title')).toHaveText('Corrigir matrícula');
    await expect(page.locator('body')).not.toContainText(/AuthenticatedLayoutComponent/u);
  });

  test('320px preserva reflow, foco, alvos e ausência de overflow horizontal', async ({ page }) => {
    await page.emulateMedia({ reducedMotion: 'reduce' });
    await page.setViewportSize({ width: 320, height: 800 });
    await mockLegacyOperations(page);

    for (const path of ['/operacao/painel', '/operacao/papeis', '/operacao/matriculas/corrigir']) {
      await page.goto(path);
      await expect(page.locator('.console-header:not(.console-header--personal)')).toBeVisible();
      const dimensions = await page.evaluate(() => ({
        innerWidth: window.innerWidth,
        scrollWidth: Math.max(document.documentElement.scrollWidth, document.body.scrollWidth),
      }));
      expect(dimensions.innerWidth).toBe(320);
      expect(dimensions.scrollWidth).toBeLessThanOrEqual(320);
      const undersized = await page.locator('a, button, input, textarea').evaluateAll((elements) =>
        elements.filter((element) => {
          const rect = element.getBoundingClientRect();
          return rect.width > 0 && (rect.width < 44 || rect.height < 44);
        }).length,
      );
      expect(undersized).toBe(0);
    }
  });
});
