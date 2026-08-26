import { inject } from '@angular/core';
import { CanActivateChildFn, Router } from '@angular/router';
import { ProfileStore } from '../../features/profile/profile.store';

const PROFILE_COMPLETION_URL = '/conta/perfil/completar';
const ERROR_URL = '/error';
const AUTH_TECHNICAL_ROUTE_PREFIXES = Object.freeze([
  '/oauth2/authorization/',
  '/login/oauth2/code/',
]);

function routePath(url: string): string {
  const fragmentBoundary = url.indexOf('#');
  const withoutFragment = fragmentBoundary === -1 ? url : url.slice(0, fragmentBoundary);
  const queryBoundary = withoutFragment.indexOf('?');
  return queryBoundary === -1 ? withoutFragment : withoutFragment.slice(0, queryBoundary);
}

function isExemptRoute(url: string): boolean {
  const path = routePath(url);
  return path === PROFILE_COMPLETION_URL
    || path === ERROR_URL
    || AUTH_TECHNICAL_ROUTE_PREFIXES.some((prefix) => path.startsWith(prefix));
}

export const profileCompletionGuard: CanActivateChildFn = async (_route, state) => {
  if (isExemptRoute(state.url)) {
    return true;
  }

  const profileStore = inject(ProfileStore);
  const router = inject(Router);

  if (profileStore.status() === 'initial') {
    await profileStore.loadProfile();
  }

  return profileStore.hasProfile()
    ? true
    : router.createUrlTree([PROFILE_COMPLETION_URL]);
};
