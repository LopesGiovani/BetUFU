import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SportsCatalogApiAdapter } from '../catalog/sports-catalog-api.adapter';
import { SportsScopeStore } from './sports-scope.store';

const competition = (id: string, name: string) => ({
  competitionId: id,
  name,
  slug: id,
  status: 'ATIVA' as const,
  lockVersion: 0,
  sportId: null,
  sportName: null,
});

describe('SportsScopeStore', () => {
  let adapter: {
    fetchScope: ReturnType<typeof vi.fn>;
    fetchCapabilities: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    adapter = {
      fetchScope: vi.fn(),
      fetchCapabilities: vi.fn(),
    };
    TestBed.configureTestingModule({
      providers: [SportsScopeStore, { provide: SportsCatalogApiAdapter, useValue: adapter }],
    });
  });

  it('seleciona somente uma competição retornada pelo BFF e rejeita deep link desconhecido', async () => {
    adapter.fetchScope.mockReturnValue(
      of({
        competitions: [competition('a', 'Olimpíada UFU'), competition('b', 'Intercursos')],
        bootstrapAllowed: false,
      }),
    );
    adapter.fetchCapabilities.mockReturnValue(of(['MANAGE_SPORTS_CATALOG']));
    const store = TestBed.inject(SportsScopeStore);

    await store.load('nao-autorizada');

    expect(store.status()).toBe('ready');
    expect(store.selectedCompetitionId()).toBeNull();
    expect(store.deepLinkRejected()).toBe(true);
    expect(store.competitions().map((item) => item.name)).toEqual(['Intercursos', 'Olimpíada UFU']);
  });

  it('pré-seleciona escopo único por nome e não habilita bootstrap sem capability', async () => {
    adapter.fetchScope.mockReturnValue(
      of({ competitions: [competition('a', 'Olimpíada UFU')], bootstrapAllowed: true }),
    );
    adapter.fetchCapabilities.mockReturnValue(of(['MANAGE_SPORTS_CATALOG']));
    const store = TestBed.inject(SportsScopeStore);

    await store.load();

    expect(store.selectedCompetition()?.name).toBe('Olimpíada UFU');
    expect(store.bootstrapAllowed()).toBe(false);
  });

  it('falha fechado sem capability ou quando o BFF não responde', async () => {
    adapter.fetchScope.mockReturnValue(
      of({ competitions: [competition('a', 'Olimpíada UFU')], bootstrapAllowed: false }),
    );
    adapter.fetchCapabilities.mockReturnValue(of([]));
    const denied = TestBed.inject(SportsScopeStore);
    await denied.load();
    expect(denied.status()).toBe('denied');
    expect(denied.competitions()).toEqual([]);

    const failedAdapter = {
      fetchScope: vi.fn(() => throwError(() => new Error('offline'))),
      fetchCapabilities: vi.fn(() => of([])),
    };
    TestBed.resetTestingModule();
    TestBed.configureTestingModule({
      providers: [SportsScopeStore, { provide: SportsCatalogApiAdapter, useValue: failedAdapter }],
    });
    const failed = TestBed.inject(SportsScopeStore);
    await failed.load();
    expect(failed.status()).toBe('error');
  });
});
