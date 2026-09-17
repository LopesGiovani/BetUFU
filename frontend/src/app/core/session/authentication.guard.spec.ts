import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
  provideRouter,
} from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SessionStore } from './session.store';
import { authenticationGuard } from './authentication.guard';

describe('authenticationGuard', () => {
  const status = signal<'checking' | 'authenticated' | 'expired' | 'logged_out'>('checking');
  const loadSession = vi.fn<() => Promise<void>>();

  beforeEach(() => {
    status.set('checking');
    loadSession.mockReset();
    loadSession.mockResolvedValue(undefined);

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: SessionStore,
          useValue: { status, isAuthenticated: () => status() === 'authenticated', loadSession },
        },
      ],
    });
  });

  async function evaluate(url: string): Promise<boolean | UrlTree> {
    return await TestBed.runInInjectionContext(
      () =>
        authenticationGuard(
          {} as ActivatedRouteSnapshot,
          { url } as RouterStateSnapshot,
        ) as Promise<boolean | UrlTree>,
    );
  }

  it('carrega a sessao inicial e libera a rota autenticada', async () => {
    loadSession.mockImplementation(async () => status.set('authenticated'));

    expect(await evaluate('/conta/perfil')).toBe(true);
    expect(loadSession).toHaveBeenCalledTimes(1);
  });

  it('reutiliza uma sessao autenticada sem nova chamada ao BFF', async () => {
    status.set('authenticated');

    expect(await evaluate('/operacao/painel')).toBe(true);
    expect(loadSession).not.toHaveBeenCalled();
  });

  it.each(['expired', 'logged_out'] as const)(
    'redireciona sessao %s ao estado publico e preserva query e fragmento',
    async (sessionStatus) => {
      status.set(sessionStatus);

      const result = await evaluate('/conta/matricula?origem=perfil#status');

      expect(result).toBeInstanceOf(UrlTree);
      expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe(
        '/entrar?returnTo=%2Fconta%2Fmatricula%3Forigem%3Dperfil%23status',
      );
      expect(loadSession).not.toHaveBeenCalled();
    },
  );

  it('falha fechado quando a consulta de sessao nao autentica', async () => {
    loadSession.mockImplementation(async () => status.set('expired'));

    const result = await evaluate('/operacao/odds/mercados/market-1');

    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe(
      '/entrar?returnTo=%2Foperacao%2Fodds%2Fmercados%2Fmarket-1',
    );
  });
});
