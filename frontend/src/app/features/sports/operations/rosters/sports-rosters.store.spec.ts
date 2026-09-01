import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { SportsRosterDraft } from '../../../../api/generated/model/sports-roster-draft';
import { SportsRostersApiAdapter } from './sports-rosters-api.adapter';
import { SportsRostersStore } from './sports-rosters.store';

const DRAFT: SportsRosterDraft = {
  rosterId: 'roster-a',
  teamId: 'team-a',
  teamName: 'Futsal Engenharia',
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
  status: 'RASCUNHO',
  lockVersion: 2,
  members: [
    {
      athleteId: 'athlete-a',
      athleteName: 'Ana Silva',
      athleteSlug: 'ana-silva',
      jerseyNumber: 10,
      positionName: 'Ala',
    },
  ],
};

describe('SportsRostersStore', () => {
  let adapter: Record<string, ReturnType<typeof vi.fn>>;
  let store: SportsRostersStore;

  beforeEach(() => {
    adapter = {
      fetchDraft: vi.fn(() => of(DRAFT)),
      fetchPublication: vi.fn(() => throwError(() => new HttpErrorResponse({ status: 404 }))),
      updateDraft: vi.fn(() => of(DRAFT)),
      publish: vi.fn(),
      correct: vi.fn(),
      isConflict: vi.fn(
        (error: unknown) => error instanceof HttpErrorResponse && error.status === 409,
      ),
      isUnauthorized: vi.fn(
        (error: unknown) =>
          error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403),
      ),
    };
    TestBed.configureTestingModule({
      providers: [SportsRostersStore, { provide: SportsRostersApiAdapter, useValue: adapter }],
    });
    store = TestBed.inject(SportsRostersStore);
  });

  it('preserva o rascunho local e informa conflito HTTP 409', async () => {
    await store.load('team-a');
    adapter['updateDraft'].mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409 })),
    );
    expect(await store.updateDraft(DRAFT.members)).toBe(false);
    expect(store.draft()?.rosterId).toBe('roster-a');
    expect(store.conflictMessage()).toContain('rascunho local foi preservado');
  });

  it('limpa rascunho, preview e selecao em 403', async () => {
    await store.load('team-a');
    store.previewSnapshot(DRAFT);
    adapter['updateDraft'].mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 403 })),
    );
    expect(await store.updateDraft(DRAFT.members)).toBe(false);
    expect(store.status()).toBe('denied');
    expect(store.draft()).toBeNull();
    expect(store.preview()).toBeNull();
    expect(store.selectedTeamId()).toBeNull();
  });
});
