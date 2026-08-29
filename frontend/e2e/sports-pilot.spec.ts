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
