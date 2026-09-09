import type { Routes } from '@angular/router';
import { ConsoleShellComponent } from './layout/console-shell.component';

export const PUBLIC_MARKET_ROUTES: Routes = [
  {
    path: 'odds/mercados/:marketId',
    loadComponent: () =>
      import('./features/odds/public-market/public-market-page.component').then(
        ({ PublicMarketPageComponent }) => PublicMarketPageComponent,
      ),
  },
];

export const BUILD_SPECIFIC_ROUTES: Routes = [
  {
    path: 'operacao/esportes',
    component: ConsoleShellComponent,
    data: { consoleVariant: 'operational' },
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () =>
          import('./features/sports/operations/catalog/sports-catalog-page.component').then(
            ({ SportsCatalogPageComponent }) => SportsCatalogPageComponent,
          ),
      },
      {
        path: 'catalogo',
        loadComponent: () =>
          import('./features/sports/operations/catalog/sports-catalog-page.component').then(
            ({ SportsCatalogPageComponent }) => SportsCatalogPageComponent,
          ),
      },
      {
        path: 'organizacoes',
        loadComponent: () =>
          import('./features/sports/operations/organizations/sports-organizations-page.component').then(
            ({ SportsOrganizationsPageComponent }) => SportsOrganizationsPageComponent,
          ),
      },
      {
        path: 'importacao',
        loadComponent: () =>
          import('./features/sports/operations/tabular-import/sports-tabular-import-page.component').then(
            ({ SportsTabularImportPageComponent }) => SportsTabularImportPageComponent,
          ),
      },
    ],
  },
  {
    path: 'conta',
    component: ConsoleShellComponent,
    data: { consoleVariant: 'personal' },
    children: [
      {
        path: 'perfil/completar',
        loadComponent: () =>
          import('./features/profile/profile-gate-page.component').then(
            ({ ProfileGatePageComponent }) => ProfileGatePageComponent,
          ),
      },
      {
        path: 'perfil',
        loadComponent: () =>
          import('./features/profile/profile-page.component').then(
            ({ ProfilePageComponent }) => ProfilePageComponent,
          ),
      },
      {
        path: 'matricula',
        loadComponent: () =>
          import('./features/enrollment/enrollment-page.component').then(
            ({ EnrollmentPageComponent }) => EnrollmentPageComponent,
          ),
      },
      {
        path: 'vinculo-atleta',
        loadComponent: () =>
          import('./features/sports/link-proposal/athlete-link-proposal-page.component').then(
            ({ AthleteLinkProposalPageComponent }) => AthleteLinkProposalPageComponent,
          ),
      },
    ],
  },
  {
    path: 'operacao',
    component: ConsoleShellComponent,
    data: { consoleVariant: 'operational' },
    children: [
      {
        path: 'painel',
        loadComponent: () =>
          import('./features/operations/operations-panel-page.component').then(
            ({ OperationsPanelPageComponent }) => OperationsPanelPageComponent,
          ),
      },
      {
        path: 'papeis',
        loadComponent: () =>
          import('./features/operations/role-admin-page.component').then(
            ({ RoleAdminPageComponent }) => RoleAdminPageComponent,
          ),
      },
      {
        path: 'matriculas/corrigir',
        loadComponent: () =>
          import('./features/operations/enrollment-admin-page.component').then(
            ({ EnrollmentAdminPageComponent }) => EnrollmentAdminPageComponent,
          ),
      },
    ],
  },
  {
    path: 'conta/perfil/esportivo',
    component: ConsoleShellComponent,
    data: { consoleVariant: 'personal' },
    children: [
      {
        path: '',
        loadComponent: () =>
          import('./features/sports/athlete-profile/athlete-profile-page.component').then(
            ({ AthleteProfilePageComponent }) => AthleteProfilePageComponent,
          ),
      },
    ],
  },
  {
    path: 'tracer',
    loadComponent: () =>
      import('./features/tracer/tracer-page.component').then(
        ({ TracerPageComponent }) => TracerPageComponent,
      ),
  },
  {
    path: 'diagnostic',
    loadComponent: () =>
      import('./features/diagnostic/diagnostic-page.component').then(
        ({ DiagnosticPageComponent }) => DiagnosticPageComponent,
      ),
  },
];
