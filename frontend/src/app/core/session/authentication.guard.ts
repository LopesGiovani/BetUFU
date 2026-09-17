import { inject } from '@angular/core';
import { CanActivateChildFn, Router } from '@angular/router';
import { SessionStore } from './session.store';

const LOGIN_REQUIRED_URL = '/entrar';

export const authenticationGuard: CanActivateChildFn = async (_route, state) => {
  const sessionStore = inject(SessionStore);
  const router = inject(Router);

  if (sessionStore.status() === 'checking') {
    await sessionStore.loadSession();
  }

  return sessionStore.isAuthenticated()
    ? true
    : router.createUrlTree([LOGIN_REQUIRED_URL], {
        queryParams: { returnTo: state.url },
      });
};
