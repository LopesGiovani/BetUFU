import type { Routes } from '@angular/router';

import { profileCompletionGuard } from './core/profile/profile-completion.guard';
import { BUILD_SPECIFIC_ROUTES } from './app.routes.development';

export const APP_ROUTES: Routes = [
  {
    path: '',
    canActivateChild: [profileCompletionGuard],
    children: BUILD_SPECIFIC_ROUTES,
  },
];
