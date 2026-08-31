import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { signal } from '@angular/core';
import { describe, expect, it, vi } from 'vitest';
import { PublicAthletePageComponent } from './public-athlete-page.component';
import { SportsPublicViewStore } from '../public-view/sports-public-view.store';

describe('PublicAthletePageComponent', () => {
  it('carrega atleta por URL direta e exibe estatísticas públicas nomeadas', async () => {
    const store = { isLoading: signal(false), isNotFound: signal(false), isError: signal(false), errorMessage: signal(null), data: signal({ athleteId: 'a1', name: 'Ana', slug: 'ana', teams: [], events: [], stats: { totalParticipations: 2, totalWins: 1, totalLosses: 1 } }), retry: vi.fn(), load: vi.fn() };
    await TestBed.configureTestingModule({ imports: [PublicAthletePageComponent], providers: [{ provide: SportsPublicViewStore, useValue: store }, { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'a1' } } } }] }).compileComponents();
    const fixture = TestBed.createComponent(PublicAthletePageComponent); fixture.detectChanges();
    expect(store.load).toHaveBeenCalledWith('athlete', 'a1'); expect(fixture.nativeElement.textContent).toContain('participações públicas'); expect(fixture.nativeElement.textContent).not.toContain('matrícula');
  });
});
