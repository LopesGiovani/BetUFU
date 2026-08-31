import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { signal } from '@angular/core';
import { describe, expect, it, vi } from 'vitest';
import { PublicTeamPageComponent } from './public-team-page.component';
import { SportsPublicViewStore } from '../public-view/sports-public-view.store';

describe('PublicTeamPageComponent', () => {
  it('carrega equipe por deep link e mostra vazio honesto do elenco', async () => {
    const store = { isLoading: signal(false), isNotFound: signal(false), isError: signal(false), errorMessage: signal(null), data: signal({ teamId: 't1', organizationId: 'o1', organizationName: 'Atléticas', editionId: 'ed1', editionName: '2026', competitionId: 'c1', competitionName: 'Jogos', sportId: 's1', sportName: 'Futebol', categoryId: 'cat1', categoryName: 'Livre', name: 'Engenharia', slug: 'engenharia', events: [] }), retry: vi.fn(), load: vi.fn() };
    await TestBed.configureTestingModule({ imports: [PublicTeamPageComponent], providers: [{ provide: SportsPublicViewStore, useValue: store }, { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 't1' } } } }] }).compileComponents();
    const fixture = TestBed.createComponent(PublicTeamPageComponent); fixture.detectChanges();
    expect(store.load).toHaveBeenCalledWith('team', 't1'); expect(fixture.nativeElement.textContent).toContain('Nenhum atleta publicado nesta equipe.');
  });
});
