import type { Routes } from '@angular/router';
import { AuthenticatedLayoutComponent } from './layout/authenticated-layout.component';

export const BUILD_SPECIFIC_ROUTES: Routes = [
  {
    path: '',
    component: AuthenticatedLayoutComponent,
    children: [
      {
        path: 'conta/perfil/completar',
        loadComponent: () =>
          import('./features/profile/profile-gate-page.component').then(
            ({ ProfileGatePageComponent }) => ProfileGatePageComponent,
          ),
      },
      {
        path: 'conta/perfil',
        loadComponent: () =>
          import('./features/profile/profile-page.component').then(
            ({ ProfilePageComponent }) => ProfilePageComponent,
          ),
      },
      {
        path: 'conta/matricula',
        loadComponent: () =>
          import('./features/enrollment/enrollment-page.component').then(
            ({ EnrollmentPageComponent }) => EnrollmentPageComponent,
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
    ],
  },
];
