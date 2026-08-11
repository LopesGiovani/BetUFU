import type { Routes } from '@angular/router';

import { BUILD_SPECIFIC_ROUTES } from './app.routes.development';

export const APP_ROUTES: Routes = [...BUILD_SPECIFIC_ROUTES];
