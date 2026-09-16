import { describe, expect, it } from 'vitest';
import { contextualLoginUrl } from './contextual-login-url';

describe('contextualLoginUrl', () => {
  it('codifica a rota Angular atual como um unico parametro interno', () => {
    const currentUrl =
      '/odds/mercados/11111111-1111-4111-8111-111111111111?selection=AWAY#bilhete';

    expect(contextualLoginUrl(currentUrl)).toBe(
      '/login/start?returnTo=' + encodeURIComponent(currentUrl),
    );
  });

  it.each([
    'https://evil.invalid/steal',
    '//evil.invalid/steal',
    '/\\evil.invalid/steal',
    '/login/oauth2/code/keycloak',
    '/oauth2/authorization/keycloak',
    '/login/start?returnTo=/odds/mercados/1',
  ])('falha para a Arena ao receber rota nao interna ou tecnica: %s', (currentUrl) => {
    expect(contextualLoginUrl(currentUrl)).toBe('/login/start?returnTo=%2F');
  });
});
