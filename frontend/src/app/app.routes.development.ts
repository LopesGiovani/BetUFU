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
    ],
  },
];
