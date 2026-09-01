import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { describe, expect, it, beforeEach, vi } from 'vitest';
import { SportsPublicViewApiAdapter } from './sports-public-view-api.adapter';
import { SportsPublicViewStore } from './sports-public-view.store';

describe('SportsPublicViewStore', () => {
  let adapter: { fetch: ReturnType<typeof vi.fn> };
  beforeEach(() => {
    adapter = { fetch: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        SportsPublicViewStore,
        { provide: SportsPublicViewApiAdapter, useValue: adapter },
      ],
    });
  });
  it('carrega detalhe público e preserva somente o estado adaptado', async () => {
    adapter.fetch.mockReturnValue(
      of({
        competitionId: 'c1',
        name: 'Jogos',
        slug: 'jogos',
        editions: [],
        categories: [],
        phases: [],
        venues: [],
        teams: [],
        recentEvents: [],
      }),
    );
    const store = TestBed.inject(SportsPublicViewStore);
    await store.load('competition', 'c1');
    expect(store.isPopulated()).toBe(true);
    expect(store.data()).toEqual(expect.objectContaining({ competitionId: 'c1' }));
    expect(adapter.fetch).toHaveBeenCalledWith('competition', 'c1');
  });
  it('transforma 404 em estado público sem detalhes internos', async () => {
    adapter.fetch.mockReturnValue(
      throwError(
        () => new HttpErrorResponse({ status: 404, error: { correlationId: 'secret-id' } }),
      ),
    );
    const store = TestBed.inject(SportsPublicViewStore);
    await store.load('team', 't1');
    expect(store.isNotFound()).toBe(true);
    expect(store.errorMessage()).toContain('não está disponível');
    expect(store.errorMessage()).not.toContain('secret-id');
  });
  it('ignora resposta antiga quando uma rota mais recente foi solicitada', async () => {
    let resolveOld!: (value: unknown) => void;
    adapter.fetch
      .mockReturnValueOnce(
        new Promise((resolve) => {
          resolveOld = resolve;
        }),
      )
      .mockReturnValueOnce(
        of({
          athleteId: 'a2',
          name: 'Ana',
          slug: 'ana',
          teams: [],
          events: [],
          stats: { totalParticipations: 0, totalWins: 0, totalLosses: 0 },
        }),
      );
    const store = TestBed.inject(SportsPublicViewStore);
    const old = store.load('athlete', 'a1');
    const latest = store.load('athlete', 'a2');
    resolveOld({
      athleteId: 'a1',
      name: 'Antigo',
      slug: 'antigo',
      teams: [],
      events: [],
      stats: { totalParticipations: 0, totalWins: 0, totalLosses: 0 },
    });
    await Promise.all([old, latest]);
    expect(store.id()).toBe('a2');
    expect((store.data() as { athleteId: string }).athleteId).toBe('a2');
  });
});
