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
import { ProfileStore } from '../../features/profile/profile.store';
import { profileCompletionGuard } from './profile-completion.guard';

describe('profileCompletionGuard (D-39)', () => {
  const status = signal<'initial' | 'loading' | 'empty' | 'populated' | 'error'>('initial');
  const hasProfile = signal(false);
  const loadProfile = vi.fn<() => Promise<void>>();

  beforeEach(() => {
    status.set('initial');
    hasProfile.set(false);
    loadProfile.mockReset();
    loadProfile.mockResolvedValue(undefined);

    TestBed.configureTestingModule({
      providers: [
        provideRouter([]),
        {
          provide: ProfileStore,
          useValue: { status, hasProfile, loadProfile },
        },
      ],
    });
  });

  async function evaluate(url: string): Promise<boolean | UrlTree> {
    return await TestBed.runInInjectionContext(
      () =>
        profileCompletionGuard(
          {} as ActivatedRouteSnapshot,
          { url } as RouterStateSnapshot,
        ) as Promise<boolean | UrlTree>,
    );
  }

  it('permite S1 sem consultar o perfil e evita loop de redirecionamento', async () => {
    const result = await evaluate('/conta/perfil/completar?origem=primeiro-acesso');

    expect(result).toBe(true);
    expect(loadProfile).not.toHaveBeenCalled();
  });

  it.each([
    '/oauth2/authorization/keycloak',
    '/login/oauth2/code/keycloak?code=opaque&state=opaque',
    '/error',
  ])('permite a rota tecnica %s sem consultar o perfil', async (url) => {
    const result = await evaluate(url);

    expect(result).toBe(true);
    expect(loadProfile).not.toHaveBeenCalled();
  });

  it('permite rota de produto quando o perfil ja esta completo', async () => {
    status.set('populated');
    hasProfile.set(true);

    expect(await evaluate('/operacao/painel')).toBe(true);
    expect(loadProfile).not.toHaveBeenCalled();
  });

  it('carrega o perfil uma vez e permite a rota quando o BFF devolve perfil completo', async () => {
    loadProfile.mockImplementation(async () => {
      status.set('populated');
      hasProfile.set(true);
    });

    expect(await evaluate('/conta/perfil')).toBe(true);
    expect(loadProfile).toHaveBeenCalledTimes(1);
  });

  it('redireciona perfil incompleto para S1 por UrlTree', async () => {
    loadProfile.mockImplementation(async () => {
      status.set('empty');
      hasProfile.set(false);
    });

    const result = await evaluate('/conta/matricula');

    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/conta/perfil/completar');
  });

  it('falha fechado em erro de carga sem transformar estado cliente em autorizacao', async () => {
    loadProfile.mockImplementation(async () => {
      status.set('error');
      hasProfile.set(false);
    });

    const result = await evaluate('/operacao/papeis');

    expect(result).toBeInstanceOf(UrlTree);
    expect(TestBed.inject(Router).serializeUrl(result as UrlTree)).toBe('/conta/perfil/completar');
  });
});
