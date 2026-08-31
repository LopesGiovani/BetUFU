import { describe, expect, it } from 'vitest';
import { APP_ROUTES } from './app.routes';
import { PublicBracketPageComponent } from './features/sports/public-bracket/public-bracket-page.component';
import { SportsBracketPageComponent } from './features/sports/operations/bracket/sports-bracket-page.component';

function childrenOf(route: typeof APP_ROUTES[number]): readonly typeof APP_ROUTES[number][] {
  return route.children ?? [];
}

describe('rotas de chaveamento', () => {
  it('resolve o deep link publico sem guard autenticado', async () => {
    const arena = APP_ROUTES.find((route) => route.children?.some((candidate) => candidate.path === 'chaveamentos/:bracketId'));
    const route = childrenOf(arena!).find((candidate) => candidate.path === 'chaveamentos/:bracketId');

    expect(route).toBeDefined();
    expect(route?.canActivate).toBeUndefined();
    expect(route?.loadComponent).toBeDefined();
    await expect(route?.loadComponent?.()).resolves.toBe(PublicBracketPageComponent);
  });

  it('mantem o preview operacional dentro do guard de perfil e com id opaco', async () => {
    const guarded = APP_ROUTES.find((route) => route.canActivateChild);
    const route = childrenOf(guarded!).find(
      (candidate) => candidate.path === 'operacao/esportes/chaveamentos/:bracketId',
    );

    expect(route).toBeDefined();
    expect(guarded?.canActivateChild).toBeDefined();
    const page = childrenOf(route!).find((candidate) => candidate.path === '');
    await expect(page?.loadComponent?.()).resolves.toBe(SportsBracketPageComponent);
  });
});
