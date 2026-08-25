import type { Routes } from '@angular/router';
import { AuthenticatedLayoutComponent } from './layout/authenticated-layout.component';

export const BUILD_SPECIFIC_ROUTES: Routes = [
  {
    path: '',
    component: AuthenticatedLayoutComponent,
    children: [
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
        path: 'operacao/painel',
        loadComponent: () =>
          import('./features/operations/operations-panel-page.component').then(
            ({ OperationsPanelPageComponent }) => OperationsPanelPageComponent,
          ),
      },
    ],
  },
];
