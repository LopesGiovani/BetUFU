import type { Routes } from '@angular/router';

export const BUILD_SPECIFIC_ROUTES: Routes = [
  {
    path: 'tracer',
    loadComponent: () =>
      import('./features/tracer/tracer-page.component').then(
        ({ TracerPageComponent }) => TracerPageComponent,
      ),
  },
];
