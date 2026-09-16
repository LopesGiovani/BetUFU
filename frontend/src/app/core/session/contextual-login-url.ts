const TECHNICAL_PATH_PREFIXES = [
  '/login',
  '/logout',
  '/oauth2',
  '/api',
  '/actuator',
  '/error',
  '/ws',
] as const;

export function contextualLoginUrl(currentUrl: string): string {
  const target = isSafeInternalUrl(currentUrl) ? currentUrl : '/';
  return '/login/start?returnTo=' + encodeURIComponent(target);
}

function isSafeInternalUrl(value: string): boolean {
  if (
    !value.startsWith('/') ||
    value.startsWith('//') ||
    value.includes('\\') ||
    /[\u0000-\u001F\u007F]/u.test(value)
  ) {
    return false;
  }

  const path = value.split(/[?#]/u, 1)[0].toLowerCase();
  if (path.includes('//') || /(?:^|\/)\.{1,2}(?:\/|$)/u.test(path)) return false;
  return !TECHNICAL_PATH_PREFIXES.some(
    (prefix) => path === prefix || path.startsWith(prefix + '/'),
  );
}
