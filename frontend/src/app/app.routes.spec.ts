import { describe, expect, it } from 'vitest';
import { RankingPageComponent } from './features/ranking/ranking-page.component';
import { GroupPageComponent } from './features/ranking/group-page.component';
import { SettlementHistoryPageComponent } from './features/settlement/settlement-history-page.component';
import { AchievementListComponent } from './features/ranking/achievement-list.component';
import { SettlementOperationsPageComponent } from './features/settlement/settlement-operations-page.component';
import { APP_ROUTES } from './app.routes';
import { ArenaShellComponent } from './layout/arena-shell.component';
import { PublicAgendaPageComponent } from './features/sports/public-agenda/public-agenda-page.component';
import { PublicBracketPageComponent } from './features/sports/public-bracket/public-bracket-page.component';
import { SportsBracketPageComponent } from './features/sports/operations/bracket/sports-bracket-page.component';
import { PublicStandingsPageComponent } from './features/sports/public-standings/public-standings-page.component';
import { SportsStandingsPageComponent } from './features/sports/operations/standings/sports-standings-page.component';
import { AthleteLinkProposalPageComponent } from './features/sports/link-proposal/athlete-link-proposal-page.component';
import { PublicMarketPageComponent } from './features/odds/public-market/public-market-page.component';
import { OddsOperationsPageComponent } from './features/odds/operations/odds-operations-page.component';
import { authenticationGuard } from './core/session/authentication.guard';
import { profileCompletionGuard } from './core/profile/profile-completion.guard';
import { LoginRequiredPageComponent } from './features/session/login-required-page.component';
import { NotFoundPageComponent } from './features/errors/not-found-page.component';

function childrenOf(route: (typeof APP_ROUTES)[number]): readonly (typeof APP_ROUTES)[number][] {
  return route.children ?? [];
}

it('Fase 6 oferece ranking publico e quatro rotas privadas com os guards existentes', async () => {
  const arena = APP_ROUTES.find((route) => route.component === ArenaShellComponent);
  const ranking = arena?.children?.find((route) => route.path === 'ranking');
  await expect(ranking?.loadComponent?.()).resolves.toBe(RankingPageComponent);
  const guarded = APP_ROUTES.find((route) => route.canActivateChild?.includes(authenticationGuard));
  expect(guarded?.canActivateChild).toEqual([authenticationGuard, profileCompletionGuard]);
  for (const [path, component] of [
    ['conta/historico', SettlementHistoryPageComponent],
    ['conta/grupos', GroupPageComponent],
    ['conta/conquistas', AchievementListComponent],
    ['operacao/liquidacoes', SettlementOperationsPageComponent],
  ] as const) {
    const route = guarded?.children?.find((route) => route.path === path);
    await expect(route?.children?.[0].loadComponent?.()).resolves.toBe(component);
    expect(arena?.children?.some((route) => route.path === path)).toBe(false);
  }
});

it('resolve o deep link publico de odds sem guard autenticado ou rotas operacionais', async () => {
  const arena = APP_ROUTES.find((route) =>
    route.children?.some((candidate) => candidate.path === 'odds/mercados/:marketId'),
  );
  const route = childrenOf(arena!).find(
    (candidate) => candidate.path === 'odds/mercados/:marketId',
  );

  expect(route).toBeDefined();
  expect(route?.canActivate).toBeUndefined();
  expect(route?.loadComponent).toBeDefined();
  await expect(route?.loadComponent?.()).resolves.toBe(PublicMarketPageComponent);
});

it('mantem /arena como alias publico da agenda', async () => {
  const arena = APP_ROUTES.find((route) => route.component === ArenaShellComponent);
  const route = childrenOf(arena!).find((candidate) => candidate.path === 'arena');

  expect(route).toBeDefined();
  expect(route?.canActivate).toBeUndefined();
  await expect(route?.loadComponent?.()).resolves.toBe(PublicAgendaPageComponent);
});

it('mantem a operacao de odds protegida e separada da Arena publica', async () => {
  const guarded = APP_ROUTES.find((route) => route.canActivateChild?.includes(authenticationGuard));
  const route = childrenOf(guarded!).find(
    (candidate) => candidate.path === 'operacao/odds/mercados',
  );
  expect(route?.component).toBeDefined();
  expect(guarded?.canActivateChild).toEqual([authenticationGuard, profileCompletionGuard]);
  const detail = childrenOf(route!).find((candidate) => candidate.path === ':marketId');
  await expect(detail?.loadComponent?.()).resolves.toBe(OddsOperationsPageComponent);
});

it('publica o estado de login requerido fora do ramo protegido', async () => {
  const route = APP_ROUTES.find((candidate) => candidate.path === 'entrar');

  expect(route).toBeDefined();
  expect(route?.canActivate).toBeUndefined();
  await expect(route?.loadComponent?.()).resolves.toBe(LoginRequiredPageComponent);
});

it('mantem o wildcard como ultima rota raiz e separado do estado de login', async () => {
  const route = APP_ROUTES.at(-1);

  expect(route?.path).toBe('**');
  expect(route?.canActivate).toBeUndefined();
  await expect(route?.loadComponent?.()).resolves.toBe(NotFoundPageComponent);
});

describe('rotas de chaveamento e classificação', () => {
  it('resolve o deep link publico de chaveamento sem guard autenticado', async () => {
    const arena = APP_ROUTES.find((route) =>
      route.children?.some((candidate) => candidate.path === 'chaveamentos/:bracketId'),
    );
    const route = childrenOf(arena!).find(
      (candidate) => candidate.path === 'chaveamentos/:bracketId',
    );

    expect(route).toBeDefined();
    expect(route?.canActivate).toBeUndefined();
    expect(route?.loadComponent).toBeDefined();
    await expect(route?.loadComponent?.()).resolves.toBe(PublicBracketPageComponent);
  });

  it('mantem o preview operacional de chaveamento dentro do guard de perfil e com id opaco', async () => {
    const guarded = APP_ROUTES.find((route) =>
      route.canActivateChild?.includes(authenticationGuard),
    );
    const route = childrenOf(guarded!).find(
      (candidate) => candidate.path === 'operacao/esportes/chaveamentos/:bracketId',
    );

    expect(route).toBeDefined();
    expect(guarded?.canActivateChild).toEqual([authenticationGuard, profileCompletionGuard]);
    const page = childrenOf(route!).find((candidate) => candidate.path === '');
    await expect(page?.loadComponent?.()).resolves.toBe(SportsBracketPageComponent);
  });

  it('resolve o deep link publico de classificacao sem guard autenticado', async () => {
    const arena = APP_ROUTES.find((route) =>
      route.children?.some((candidate) => candidate.path === 'classificacoes/:standingsId'),
    );
    const route = childrenOf(arena!).find(
      (candidate) => candidate.path === 'classificacoes/:standingsId',
    );

    expect(route).toBeDefined();
    expect(route?.canActivate).toBeUndefined();
    expect(route?.loadComponent).toBeDefined();
    await expect(route?.loadComponent?.()).resolves.toBe(PublicStandingsPageComponent);
  });

  it('mantem o preview operacional de classificacao dentro do guard de perfil e com id opaco', async () => {
    const guarded = APP_ROUTES.find((route) =>
      route.canActivateChild?.includes(authenticationGuard),
    );
    const route = childrenOf(guarded!).find(
      (candidate) => candidate.path === 'operacao/esportes/classificacoes/:standingsId',
    );

    expect(route).toBeDefined();
    expect(guarded?.canActivateChild).toEqual([authenticationGuard, profileCompletionGuard]);
    const page = childrenOf(route!).find((candidate) => candidate.path === '');
    await expect(page?.loadComponent?.()).resolves.toBe(SportsStandingsPageComponent);
  });

  it('resolve a rota pessoal da proposta dentro do guard de perfil e sem id na URL', async () => {
    const guarded = APP_ROUTES.find((route) =>
      route.canActivateChild?.includes(authenticationGuard),
    );
    const account = childrenOf(guarded!).find((candidate) => candidate.path === 'conta');
    const route = childrenOf(account!).find((candidate) => candidate.path === 'vinculo-atleta');

    expect(route).toBeDefined();
    expect(guarded?.canActivateChild).toEqual([authenticationGuard, profileCompletionGuard]);
    await expect(route?.loadComponent?.()).resolves.toBe(AthleteLinkProposalPageComponent);
  });
});
