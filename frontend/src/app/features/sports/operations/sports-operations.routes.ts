import { inject } from '@angular/core';
import { CanMatchFn, Route, Router, Routes } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { ConsoleShellComponent } from '../../../layout/console-shell.component';
import { SportsCatalogApiAdapter } from './catalog/sports-catalog-api.adapter';
import { SportsScopeStore } from './scope/sports-scope.store';

export type SportsOperationKey = 'O1' | 'O2' | 'O3' | 'O4' | 'O5' | 'O6' | 'O7' | 'O8';

export interface SportsOperationNavigationItem {
  readonly key: SportsOperationKey;
  readonly label: string;
  readonly path: string;
}

export const SPORTS_OPERATION_NAVIGATION: readonly SportsOperationNavigationItem[] = Object.freeze([
  Object.freeze({ key: 'O1', label: 'Visão geral', path: '/operacao/esportes' }),
  Object.freeze({ key: 'O2', label: 'Catálogo', path: '/operacao/esportes/catalogo' }),
  Object.freeze({
    key: 'O3',
    label: 'Organizações e equipes',
    path: '/operacao/esportes/organizacoes',
  }),
  Object.freeze({ key: 'O4', label: 'Atletas e elencos', path: '/operacao/esportes/atletas' }),
  Object.freeze({ key: 'O5', label: 'Importar CSV/XLSX', path: '/operacao/esportes/importacao' }),
  Object.freeze({ key: 'O6', label: 'Eventos e resultados', path: '/operacao/esportes/eventos' }),
  Object.freeze({
    key: 'O7',
    label: 'Chaveamento e classificação',
    path: '/operacao/esportes/chaveamentos',
  }),
  Object.freeze({ key: 'O8', label: 'Revisões de vínculo', path: '/operacao/esportes/revisoes' }),
]);

const SPORTS_CAPABILITY = 'MANAGE_SPORTS_CATALOG';

function requestedCompetitionId(router: Router): string | null {
  const currentNavigation = router.getCurrentNavigation();
  const queryParams =
    currentNavigation?.extractedUrl?.queryParams ?? router.parseUrl(router.url).queryParams;
  const requested = queryParams['competitionId'];
  return typeof requested === 'string' && requested.trim() !== '' ? requested.trim() : null;
}

/**
 * Revalidates the server-provided scope before a lazy route can match.
 * A URL competition id is only a lookup hint; membership in the BFF response is authoritative.
 */
export function createSportsOperationCanMatch(
  options: { allowEmpty?: boolean; allowUnselected?: boolean } = {},
): CanMatchFn {
  return async () => {
    const adapter = inject(SportsCatalogApiAdapter);
    const scope = inject(SportsScopeStore);
    const router = inject(Router);

    let capabilities: readonly string[];
    try {
      capabilities = await firstValueFrom(adapter.fetchCapabilities());
    } catch {
      return false;
    }
    if (!capabilities.includes(SPORTS_CAPABILITY)) {
      return false;
    }

    try {
      await scope.load(requestedCompetitionId(router));
    } catch {
      return false;
    }

    if (scope.isError() || scope.isDenied() || scope.deepLinkRejected()) {
      return false;
    }
    if (scope.isEmpty()) {
      return options.allowEmpty === true && scope.bootstrapAllowed();
    }
    return scope.hasSelection() || (options.allowUnselected === true && scope.isReady());
  };
}

export const sportsOperationCanMatch = createSportsOperationCanMatch();
const sportsBootstrapCanMatch = createSportsOperationCanMatch({
  allowEmpty: true,
  allowUnselected: true,
});

const childRoute = (
  path: string,
  key: SportsOperationKey,
  loadComponent: NonNullable<Route['loadComponent']>,
  canMatch: CanMatchFn = sportsOperationCanMatch,
): Route => ({ path, data: { operation: key }, canMatch: [canMatch], loadComponent });

export const sportsOperationsRoutes: Routes = [
  {
    path: 'operacao/esportes',
    component: ConsoleShellComponent,
    children: [
      childRoute(
        '',
        'O1',
        () =>
          import('./catalog/sports-catalog-page.component').then(
            ({ SportsCatalogPageComponent }) => SportsCatalogPageComponent,
          ),
        sportsBootstrapCanMatch,
      ),
      childRoute('catalogo', 'O2', () =>
        import('./catalog/sports-catalog-page.component').then(
          ({ SportsCatalogPageComponent }) => SportsCatalogPageComponent,
        ),
      ),
      childRoute('organizacoes', 'O3', () =>
        import('./organizations/sports-organizations-page.component').then(
          ({ SportsOrganizationsPageComponent }) => SportsOrganizationsPageComponent,
        ),
      ),
      childRoute('atletas', 'O4', () =>
        import('./athletes/sports-athletes-page.component').then(
          ({ SportsAthletesPageComponent }) => SportsAthletesPageComponent,
        ),
      ),
      childRoute('elencos', 'O4', () =>
        import('./rosters/sports-rosters-page.component').then(
          ({ SportsRostersPageComponent }) => SportsRostersPageComponent,
        ),
      ),
      childRoute('importacao', 'O5', () =>
        import('./tabular-import/sports-tabular-import-page.component').then(
          ({ SportsTabularImportPageComponent }) => SportsTabularImportPageComponent,
        ),
      ),
      childRoute('eventos', 'O6', () =>
        import('./events-results/sports-event-result-page.component').then(
          ({ SportsEventResultPageComponent }) => SportsEventResultPageComponent,
        ),
      ),
      childRoute('chaveamentos', 'O7', () =>
        import('./bracket/sports-bracket-page.component').then(
          ({ SportsBracketPageComponent }) => SportsBracketPageComponent,
        ),
      ),
      childRoute('classificacoes', 'O7', () =>
        import('./standings/sports-standings-page.component').then(
          ({ SportsStandingsPageComponent }) => SportsStandingsPageComponent,
        ),
      ),
      childRoute('revisoes', 'O8', () =>
        import('./link-review/athlete-link-review-page.component').then(
          ({ AthleteLinkReviewPageComponent }) => AthleteLinkReviewPageComponent,
        ),
      ),
    ],
  },
  {
    path: 'operacao/esportes/chaveamentos/:bracketId',
    component: ConsoleShellComponent,
    canMatch: [sportsOperationCanMatch],
    children: [
      {
        path: '',
        data: { operation: 'O7' satisfies SportsOperationKey },
        loadComponent: () =>
          import('./bracket/sports-bracket-page.component').then(
            ({ SportsBracketPageComponent }) => SportsBracketPageComponent,
          ),
      },
    ],
  },
  {
    path: 'operacao/esportes/classificacoes/:standingsId',
    component: ConsoleShellComponent,
    canMatch: [sportsOperationCanMatch],
    children: [
      {
        path: '',
        data: { operation: 'O7' satisfies SportsOperationKey },
        loadComponent: () =>
          import('./standings/sports-standings-page.component').then(
            ({ SportsStandingsPageComponent }) => SportsStandingsPageComponent,
          ),
      },
    ],
  },
];
