import type { Routes } from '@angular/router';

import { profileCompletionGuard } from './core/profile/profile-completion.guard';
import { BUILD_SPECIFIC_ROUTES } from './app.routes.development';
import { ArenaShellComponent } from './layout/arena-shell.component';

// Build-specific routes include both the legacy personal console and the scoped sports console.

export const APP_ROUTES: Routes = [
  {
    path: '',
    component: ArenaShellComponent,
    children: [
      {
        path: '',
        pathMatch: 'full',
        loadComponent: () =>
          import('./features/sports/public-agenda/public-agenda-page.component').then(
            ({ PublicAgendaPageComponent }) => PublicAgendaPageComponent,
          ),
      },
    ],
  },
  {
    path: '',
    canActivateChild: [profileCompletionGuard],
    children: BUILD_SPECIFIC_ROUTES,
  },
];
