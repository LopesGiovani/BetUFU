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
    ],
  },
];
