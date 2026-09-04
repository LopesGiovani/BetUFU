import { describe, expect, it } from 'vitest';
import { APP_ROUTES } from './app.routes';
import { PublicBracketPageComponent } from './features/sports/public-bracket/public-bracket-page.component';
import { SportsBracketPageComponent } from './features/sports/operations/bracket/sports-bracket-page.component';
import { PublicStandingsPageComponent } from './features/sports/public-standings/public-standings-page.component';
import { SportsStandingsPageComponent } from './features/sports/operations/standings/sports-standings-page.component';
import { AthleteLinkProposalPageComponent } from './features/sports/link-proposal/athlete-link-proposal-page.component';

function childrenOf(route: (typeof APP_ROUTES)[number]): readonly (typeof APP_ROUTES)[number][] {
  return route.children ?? [];
}

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
    const guarded = APP_ROUTES.find((route) => route.canActivateChild);
    const route = childrenOf(guarded!).find(
      (candidate) => candidate.path === 'operacao/esportes/chaveamentos/:bracketId',
    );

    expect(route).toBeDefined();
    expect(guarded?.canActivateChild).toBeDefined();
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
    const guarded = APP_ROUTES.find((route) => route.canActivateChild);
    const route = childrenOf(guarded!).find(
      (candidate) => candidate.path === 'operacao/esportes/classificacoes/:standingsId',
    );

    expect(route).toBeDefined();
    expect(guarded?.canActivateChild).toBeDefined();
    const page = childrenOf(route!).find((candidate) => candidate.path === '');
    await expect(page?.loadComponent?.()).resolves.toBe(SportsStandingsPageComponent);
  });

  it('resolve a rota pessoal da proposta dentro do guard de perfil e sem id na URL', async () => {
    const guarded = APP_ROUTES.find((route) => route.canActivateChild);
    const account = childrenOf(guarded!).find((candidate) => candidate.path === 'conta');
    const route = childrenOf(account!).find((candidate) => candidate.path === 'vinculo-atleta');

    expect(route).toBeDefined();
    expect(guarded?.canActivateChild).toBeDefined();
    await expect(route?.loadComponent?.()).resolves.toBe(AthleteLinkProposalPageComponent);
  });
});
