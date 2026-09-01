import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { signal } from '@angular/core';
import { describe, expect, it, vi } from 'vitest';
import { PublicCompetitionPageComponent } from './public-competition-page.component';
import { SportsPublicViewStore } from '../public-view/sports-public-view.store';

describe('PublicCompetitionPageComponent', () => {
  it('carrega a competição pelo parâmetro da rota e renderiza o detalhe', async () => {
    const store = {
      isLoading: signal(false),
      isNotFound: signal(false),
      isError: signal(false),
      errorMessage: signal(null),
      data: signal({
        competitionId: 'c1',
        name: 'Jogos UFU',
        slug: 'jogos-ufu',
        editions: [],
        categories: [],
        phases: [],
        venues: [],
        teams: [],
        recentEvents: [],
      }),
      retry: vi.fn(),
      load: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [PublicCompetitionPageComponent],
      providers: [
        { provide: SportsPublicViewStore, useValue: store },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'c1' } } } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(PublicCompetitionPageComponent);
    fixture.detectChanges();
    expect(store.load).toHaveBeenCalledWith('competition', 'c1');
    expect(fixture.nativeElement.textContent).toContain('Jogos UFU');
  });
});
