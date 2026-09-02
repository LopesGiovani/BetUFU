import type { Routes } from '@angular/router';
import { AuthenticatedLayoutComponent } from './layout/authenticated-layout.component';
import { ConsoleShellComponent } from './layout/console-shell.component';

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
    ],
  },
  {
    path: '',
    component: AuthenticatedLayoutComponent,
    children: [
      {
        path: 'conta/perfil/esportivo',
        loadComponent: () =>
          import('./features/sports/athlete-profile/athlete-profile-page.component').then(
            ({ AthleteProfilePageComponent }) => AthleteProfilePageComponent,
          ),
      },
      {
        path: 'operacao/painel',
        loadComponent: () =>
          import('./features/operations/operations-panel-page.component').then(
            ({ OperationsPanelPageComponent }) => OperationsPanelPageComponent,
          ),
      },
      {
        path: 'operacao/papeis',
        loadComponent: () =>
          import('./features/operations/role-admin-page.component').then(
            ({ RoleAdminPageComponent }) => RoleAdminPageComponent,
          ),
      },
      {
        path: 'operacao/matriculas/corrigir',
        loadComponent: () =>
          import('./features/operations/enrollment-admin-page.component').then(
            ({ EnrollmentAdminPageComponent }) => EnrollmentAdminPageComponent,
          ),
      },
    ],
  },
];
