import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Route, Router } from '@angular/router';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SportsCatalogApiAdapter } from './catalog/sports-catalog-api.adapter';
import { SportsScopeStore } from './scope/sports-scope.store';
import {
  SPORTS_OPERATION_NAVIGATION,
  createSportsOperationCanMatch,
  sportsOperationsRoutes,
} from './sports-operations.routes';

describe('sportsOperationsRoutes (O1-O8)', () => {
  let capabilities: ReturnType<typeof vi.fn>;
  let scope: {
    load: ReturnType<typeof vi.fn>;
    isError: ReturnType<typeof vi.fn>;
    isDenied: ReturnType<typeof vi.fn>;
    deepLinkRejected: ReturnType<typeof vi.fn>;
    isEmpty: ReturnType<typeof vi.fn>;
    isReady: ReturnType<typeof vi.fn>;
    bootstrapAllowed: ReturnType<typeof vi.fn>;
    hasSelection: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    capabilities = vi.fn(() => of(['MANAGE_SPORTS_CATALOG']));
    scope = {
      load: vi.fn().mockResolvedValue(undefined),
      isError: vi.fn(() => false),
      isDenied: vi.fn(() => false),
      deepLinkRejected: vi.fn(() => false),
      isEmpty: vi.fn(() => false),
      isReady: vi.fn(() => true),
      bootstrapAllowed: vi.fn(() => false),
      hasSelection: vi.fn(() => true),
    };

    TestBed.configureTestingModule({
      providers: [
        { provide: SportsCatalogApiAdapter, useValue: { fetchCapabilities: capabilities } },
        { provide: SportsScopeStore, useValue: scope },
        provideRouter([]),
      ],
    });
  });

  it('mantém a subnavegação canônica em O1 a O8, sem introduzir um nono destino', () => {
    expect(SPORTS_OPERATION_NAVIGATION.map((item) => item.key)).toEqual([
      'O1',
      'O2',
      'O3',
      'O4',
      'O5',
      'O6',
      'O7',
      'O8',
    ]);

    const parent = sportsOperationsRoutes.find((route) => route.path === 'operacao/esportes');
    expect(parent).toBeDefined();
    expect(parent?.children?.map((route) => route.data?.['operation'])).toEqual([
      'O1',
      'O2',
      'O3',
      'O4',
      'O4',
      'O5',
      'O6',
      'O7',
      'O7',
      'O8',
    ]);
  });

  it('protege cada destino lazy com canMatch, inclusive os deep links O7', () => {
    const routePaths = sportsOperationsRoutes.map((route) => route.path);
    expect(routePaths).toContain('operacao/esportes');
    expect(routePaths).toContain('operacao/esportes/chaveamentos/:bracketId');
    expect(routePaths).toContain('operacao/esportes/classificacoes/:standingsId');

    const parent = sportsOperationsRoutes.find((route) => route.path === 'operacao/esportes');
    expect(parent?.children?.every((route) => route.canMatch?.length === 1)).toBe(true);
    expect(
      sportsOperationsRoutes
        .filter((route) => route.path?.includes('/:'))
        .every((route) => route.canMatch?.length === 1),
    ).toBe(true);
  });

  it('falha fechado quando a capability está ausente e não consulta o escopo', async () => {
    capabilities.mockReturnValue(of([]));
    const guard = createSportsOperationCanMatch();
    const result = await TestBed.runInInjectionContext(() => guard({} as Route, [], {} as never));

    expect(result).toBe(false);
    expect(scope.load).not.toHaveBeenCalled();
  });

  it('rejeita deep link do escopo B quando o BFF só autorizou o escopo A', async () => {
    const rejected = signal(false);
    scope.deepLinkRejected.mockImplementation(() => rejected());
    scope.load.mockImplementation(async (requested: string | null) => {
      rejected.set(requested === 'scope-b');
    });

    const router = TestBed.inject(Router);
    vi.spyOn(router, 'getCurrentNavigation').mockReturnValue({
      extractedUrl: router.parseUrl('/operacao/esportes/catalogo?competitionId=scope-b'),
    } as never);
    const result = await TestBed.runInInjectionContext(() =>
      createSportsOperationCanMatch()({} as Route, [], {} as never),
    );

    expect(scope.load).toHaveBeenCalledWith('scope-b');
    expect(result).toBe(false);
  });

  it('permite O1 com múltiplos escopos autorizados para escolha por nome', async () => {
    scope.hasSelection.mockReturnValue(false);
    const result = await TestBed.runInInjectionContext(() =>
      createSportsOperationCanMatch({ allowEmpty: true, allowUnselected: true })(
        {} as Route,
        [],
        {} as never,
      ),
    );

    expect(result).toBe(true);
  });

  it('permite O1 vazio somente para bootstrap autorizado pelo servidor', async () => {
    scope.isEmpty.mockReturnValue(true);
    scope.isReady.mockReturnValue(false);
    scope.hasSelection.mockReturnValue(false);
    scope.bootstrapAllowed.mockReturnValue(true);
    const result = await TestBed.runInInjectionContext(() =>
      createSportsOperationCanMatch({ allowEmpty: true, allowUnselected: true })(
        {} as Route,
        [],
        {} as never,
      ),
    );

    expect(result).toBe(true);
  });
});
