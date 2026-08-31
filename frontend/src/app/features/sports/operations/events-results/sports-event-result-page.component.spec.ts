import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SportsCatalogTree } from '../../../../api/generated/model/sports-catalog-tree';
import { SportsCompetitionSummary } from '../../../../api/generated/model/sports-competition-summary';
import { SportsEventDetail } from '../../../../api/generated/model/sports-event-detail';
import { SportsResultVersion } from '../../../../api/generated/model/sports-result-version';
import { SportsTeamSummary } from '../../../../api/generated/model/sports-team-summary';
import { SportsCatalogApiAdapter } from '../catalog/sports-catalog-api.adapter';
import { SportsOrganizationsApiAdapter } from '../organizations/sports-organizations-api.adapter';
import { SportsScopeStore } from '../scope/sports-scope.store';
import { SportsEventResultApiAdapter } from './sports-event-result-api.adapter';
import { SportsEventResultPageComponent } from './sports-event-result-page.component';
import { SportsEventResultStore } from './sports-event-result.store';

const COMPETITION: SportsCompetitionSummary = {
  competitionId: 'comp-1',
  name: 'Olimpíada Universitária UFU',
  slug: 'olimpiada-ufu',
  status: 'ATIVA',
  lockVersion: 0,
  sportId: null,
  sportName: null,
};

const TREE: SportsCatalogTree = {
  competition: COMPETITION,
  sports: [{ sportId: 'sport-1', name: 'Futsal', slug: 'futsal', status: 'ATIVA', lockVersion: 0 }],
  editions: [{ editionId: 'edition-1', competitionId: 'comp-1', name: 'Edição 2026', slug: '2026', startDate: null, endDate: null, status: 'ATIVA', lockVersion: 0 }],
  categories: [{ categoryId: 'cat-1', editionId: 'edition-1', name: 'Livre', slug: 'livre', status: 'ATIVA', lockVersion: 0 }],
  phases: [{ phaseId: 'phase-1', editionId: 'edition-1', categoryId: 'cat-1', name: 'Fase de Grupos', slug: 'grupos', status: 'ATIVA', lockVersion: 0 }],
  venues: [{
    venueId: 'venue-1',
    competitionId: 'comp-1',
    name: 'Ginásio Educação Física',
    address: null,
    mapUrl: null,
    notes: null,
    lockVersion: 0,
    spaces: [{ venueSpaceId: 'space-1', venueId: 'venue-1', name: 'Quadra Principal', notes: null, lockVersion: 0 }],
  }],
};

const TEAMS: SportsTeamSummary[] = [
  { teamId: 'team-1', organizationId: 'org-1', organizationName: 'Atlética Engenharia', competitionId: 'comp-1', editionId: 'edition-1', sportId: 'sport-1', sportName: 'Futsal', categoryId: 'cat-1', categoryName: 'Livre', name: 'Futsal Engenharia', slug: 'futsal-eng', status: 'PUBLICADO', lockVersion: 0 },
  { teamId: 'team-2', organizationId: 'org-2', organizationName: 'Atlética Medicina', competitionId: 'comp-1', editionId: 'edition-1', sportId: 'sport-1', sportName: 'Futsal', categoryId: 'cat-1', categoryName: 'Livre', name: 'Futsal Medicina', slug: 'futsal-med', status: 'PUBLICADO', lockVersion: 0 },
];

const EVENT_1: SportsEventDetail = {
  eventId: 'event-1',
  competitionId: 'comp-1',
  competitionName: 'Olimpíada Universitária UFU',
  editionId: 'edition-1',
  editionName: 'Edição 2026',
  title: 'Engenharia vs Medicina',
  format: 'HEAD_TO_HEAD',
  status: 'PUBLICADO',
  scheduledAt: '2026-09-01T14:00:00Z',
  zoneId: 'America/Sao_Paulo',
  venueState: 'LOCAL_DEFINIDO',
  venueId: 'venue-1',
  venueName: 'Ginásio Educação Física',
  venueSpaceId: 'space-1',
  venueSpaceName: 'Quadra Principal',
  sourceReference: 'Boletim Oficial nº 01',
  lockVersion: 1,
  slots: [
    { slotNo: 1, displayLabel: 'Futsal Engenharia', teamId: 'team-1', teamName: 'Futsal Engenharia' },
    { slotNo: 2, displayLabel: 'Futsal Medicina', teamId: 'team-2', teamName: 'Futsal Medicina' },
  ],
  createdAt: '2026-08-30T10:00:00Z',
  updatedAt: '2026-08-30T10:00:00Z',
};

const VERSION_1: SportsResultVersion = {
  versionId: 'ver-1',
  eventId: 'event-1',
  competitionId: 'comp-1',
  editionId: 'edition-1',
  versionNo: 1,
  status: 'CONFIRMADO',
  sourceReference: 'Súmula Oficial nº 01',
  recordedByIdentityId: 'admin-user',
  confirmedByIdentityId: 'admin-user',
  recordedAt: '2026-09-01T16:00:00Z',
  confirmedAt: '2026-09-01T16:05:00Z',
  baseLockVersion: 1,
  scores: [
    { slotNo: 1, displayLabel: 'Futsal Engenharia', score: 4, teamId: 'team-1', teamName: 'Futsal Engenharia' },
    { slotNo: 2, displayLabel: 'Futsal Medicina', score: 2, teamId: 'team-2', teamName: 'Futsal Medicina' },
  ],
};

describe('SportsEventResultPageComponent', () => {
  let fixture: ComponentFixture<SportsEventResultPageComponent>;
  let eventResultAdapter: Record<string, ReturnType<typeof vi.fn>>;
  let catalogAdapter: Record<string, ReturnType<typeof vi.fn>>;
  let organizationsAdapter: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(async () => {
    eventResultAdapter = {
      fetchEvents: vi.fn(() => of([EVENT_1])),
      fetchEventDetail: vi.fn(() => of(EVENT_1)),
      fetchResultVersions: vi.fn(() => of([VERSION_1])),
      createEvent: vi.fn(() => of(EVENT_1)),
      updateEventDraft: vi.fn(() => of(EVENT_1)),
      publishEvent: vi.fn(() => of(EVENT_1)),
      suspendEvent: vi.fn(() => of(EVENT_1)),
      postponeEvent: vi.fn(() => of(EVENT_1)),
      cancelEvent: vi.fn(() => of({ ...EVENT_1, status: 'CANCELADO', lockVersion: 2 })),
      informResult: vi.fn(() => of(VERSION_1)),
      reviewResult: vi.fn(() => of(VERSION_1)),
      confirmResult: vi.fn(() => of(VERSION_1)),
      correctResult: vi.fn(() => of(VERSION_1)),
      isConflict: vi.fn(() => false),
      isUnauthorized: vi.fn(() => false),
    };

    catalogAdapter = {
      fetchTree: vi.fn(() => of(TREE)),
    };

    organizationsAdapter = {
      fetchTeams: vi.fn(() => of(TEAMS)),
    };

    const mockScope = {
      competitions: signal([COMPETITION]),
      selectedCompetitionId: signal<string | null>('comp-1'),
      hasSelection: signal(true),
      isLoading: signal(false),
      isDenied: signal(false),
      isEmpty: signal(false),
      isError: signal(false),
      errorMessage: signal<string | null>(null),
      load: vi.fn().mockResolvedValue(undefined),
      selectCompetition: vi.fn(() => true),
    };

    await TestBed.configureTestingModule({
      imports: [SportsEventResultPageComponent],
      providers: [
        SportsEventResultStore,
        { provide: SportsEventResultApiAdapter, useValue: eventResultAdapter },
        { provide: SportsCatalogApiAdapter, useValue: catalogAdapter },
        { provide: SportsOrganizationsApiAdapter, useValue: organizationsAdapter },
        { provide: SportsScopeStore, useValue: mockScope },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SportsEventResultPageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    await vi.waitFor(() => expect(eventResultAdapter['fetchEvents']).toHaveBeenCalled(), { timeout: 1000 });
    fixture.detectChanges();
  });

  it('exibe a competição autorizada e carrega a lista de eventos com status textual+ícone', () => {
    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Olimpíada Universitária UFU');
    expect(root.textContent).toContain('Engenharia vs Medicina');
    expect(root.textContent).toContain('Publicado');
  });

  it('exibe o detalhe do evento selecionado e o placar oficial vigente', () => {
    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('VERSÃO OFICIAL VIGENTE');
    expect(root.textContent).toContain('4');
    expect(root.textContent).toContain('2');
    expect(root.textContent).toContain('Súmula Oficial nº 01');
  });

  it('exibe a linha do tempo de versões auditadas com atores e timestamps', () => {
    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Linha do tempo de versões auditadas');
    expect(root.textContent).toContain('v1');
    expect(root.textContent).toContain('admin-user');
  });

  it('abre diálogo destrutivo ao clicar em Cancelar evento com opção Manter evento', async () => {
    const component = fixture.componentInstance;
    component.openCancelModal();
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Cancelar evento definitivamente');
    expect(root.textContent).toContain('Ação destrutiva e irreversível');
    expect(root.textContent).toContain('Manter evento');

    component.closeModal();
    fixture.detectChanges();
    expect(root.textContent).not.toContain('Cancelar evento definitivamente');
  });

  it('exibe estado vazio quando a edição não possui eventos cadastrados', async () => {
    eventResultAdapter['fetchEvents'].mockReturnValue(of([]));
    await fixture.componentInstance.store.loadEdition('edition-1');
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Nenhum evento cadastrado');
    expect(root.textContent).toContain('Cadastre o primeiro evento escolhendo edição');
  });
});
