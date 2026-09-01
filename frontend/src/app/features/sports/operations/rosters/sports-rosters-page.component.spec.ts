import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SportsRosterDraft } from '../../../../api/generated/model/sports-roster-draft';
import { SportsRostersApiAdapter } from './sports-rosters-api.adapter';
import { SportsRostersPageComponent } from './sports-rosters-page.component';
import { SportsRostersStore } from './sports-rosters.store';
import { SportsOrganizationsApiAdapter } from '../organizations/sports-organizations-api.adapter';
import { SportsScopeStore } from '../scope/sports-scope.store';

const TEAM = {
  teamId: 'team-a',
  organizationId: 'org-a',
  organizationName: 'Atletica Engenharia',
  competitionId: 'competition-a',
  competitionName: 'Olimpiada UFU',
  editionId: 'edition-a',
  editionName: '2026',
  sportId: 'sport-a',
  sportName: 'Futsal',
  categoryId: 'category-a',
  categoryName: 'Livre',
  name: 'Futsal Engenharia',
  slug: 'futsal-engenharia',
  status: 'PUBLICADO' as const,
  lockVersion: 0,
};
const DRAFT: SportsRosterDraft = {
  rosterId: 'roster-a',
  teamId: 'team-a',
  teamName: TEAM.name,
  organizationId: TEAM.organizationId,
  organizationName: TEAM.organizationName,
  competitionId: TEAM.competitionId,
  competitionName: TEAM.competitionName,
  editionId: TEAM.editionId,
  editionName: TEAM.editionName,
  sportId: TEAM.sportId,
  sportName: TEAM.sportName,
  categoryId: TEAM.categoryId,
  categoryName: TEAM.categoryName,
  status: 'RASCUNHO',
  lockVersion: 0,
  members: [],
};

describe('SportsRostersPageComponent', () => {
  let fixture: ComponentFixture<SportsRostersPageComponent>;
  let rosterAdapter: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(async () => {
    rosterAdapter = {
      fetchDraft: vi.fn(() => of(DRAFT)),
      fetchPublication: vi.fn(() => throwError(() => ({ status: 404 }))),
      updateDraft: vi.fn(() => of(DRAFT)),
      publish: vi.fn(),
      correct: vi.fn(),
      isConflict: vi.fn(() => false),
      isUnauthorized: vi.fn(() => false),
    };
    const scope = {
      competitions: signal([
        {
          competitionId: 'competition-a',
          name: 'Olimpiada UFU',
          slug: 'olimpiada-ufu',
          status: 'ATIVA' as const,
          lockVersion: 0,
        },
      ]),
      selectedCompetitionId: signal<string | null>('competition-a'),
      hasSelection: signal(true),
      isLoading: signal(false),
      isDenied: signal(false),
      isEmpty: signal(false),
      isError: signal(false),
      errorMessage: signal<string | null>(null),
      load: vi.fn().mockResolvedValue(undefined),
    };
    await TestBed.configureTestingModule({
      imports: [SportsRostersPageComponent],
      providers: [
        SportsRostersStore,
        { provide: SportsRostersApiAdapter, useValue: rosterAdapter },
        { provide: SportsScopeStore, useValue: scope },
        {
          provide: SportsOrganizationsApiAdapter,
          useValue: { fetchTeams: vi.fn(() => of([TEAM])) },
        },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(SportsRostersPageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    await vi.waitFor(() => expect(rosterAdapter['fetchDraft']).toHaveBeenCalled(), {
      timeout: 1_000,
    });
    fixture.detectChanges();
  });

  it('exibe a equipe autorizada por nome e abre seu elenco', () => {
    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Futsal Engenharia');
    expect(root.textContent).not.toContain('team-a');
    expect(root.textContent).not.toContain('scopeRef');
  });

  it('nao abre uma equipe que nao veio do catalogo', async () => {
    await fixture.componentInstance.changeTeam('team-fora-do-catalogo');
    expect(rosterAdapter['fetchDraft']).toHaveBeenCalledTimes(1);
  });
});
