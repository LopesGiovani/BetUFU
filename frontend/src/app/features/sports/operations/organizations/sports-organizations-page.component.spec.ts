import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SportsCatalogApiAdapter } from '../catalog/sports-catalog-api.adapter';
import { SportsScopeStore } from '../scope/sports-scope.store';
import { SportsOrganizationsApiAdapter } from './sports-organizations-api.adapter';
import { SportsOrganizationsPageComponent } from './sports-organizations-page.component';
import { SportsOrganizationsStore } from './sports-organizations.store';

const ORGANIZATIONS = [
  { organizationId: 'org-b', name: 'Volei UFU', slug: 'volei-ufu', status: 'PUBLICADO', lockVersion: 1 },
  { organizationId: 'org-a', name: 'Atletica UFU', slug: 'atletica-ufu', status: 'RASCUNHO', lockVersion: 0 },
] as const;

const TREE = {
  competition: { competitionId: 'comp-a', name: 'Jogos UFU', slug: 'jogos-ufu', status: 'ATIVA', lockVersion: 0 },
  sports: [{ sportId: 'sport-a', name: 'Futsal', slug: 'futsal', status: 'ATIVA', lockVersion: 0 }],
  editions: [{ editionId: 'edition-a', name: '2026', slug: '2026', startDate: null, endDate: null, lockVersion: 0 }],
  categories: [{ categoryId: 'category-a', editionId: 'edition-a', name: 'Livre', slug: 'livre', lockVersion: 0 }],
  phases: [],
  venues: [],
};

describe('SportsOrganizationsPageComponent (O3)', () => {
  let fixture: ComponentFixture<SportsOrganizationsPageComponent>;
  let component: SportsOrganizationsPageComponent;
  let scope: ReturnType<typeof createScopeMock>;
  let organizationAdapter: Record<string, ReturnType<typeof vi.fn>>;

  function createScopeMock() {
    return {
      competitions: signal([{ competitionId: 'comp-a', name: 'Jogos UFU', slug: 'jogos-ufu', status: 'ATIVA', lockVersion: 0 }]),
      selectedCompetitionId: signal<string | null>('comp-a'),
      hasSelection: signal(true),
      isDenied: signal(false),
      isEmpty: signal(false),
      isError: signal(false),
      errorMessage: signal<string | null>(null),
      load: vi.fn().mockResolvedValue(undefined),
      selectCompetition: vi.fn().mockReturnValue(true),
    };
  }

  beforeEach(async () => {
    scope = createScopeMock();
    organizationAdapter = {
      fetchOrganizations: vi.fn().mockReturnValue(of(ORGANIZATIONS)),
      fetchTeams: vi.fn().mockReturnValue(of([])),
      createOrganization: vi.fn().mockReturnValue(of(ORGANIZATIONS[1])),
      updateOrganization: vi.fn().mockReturnValue(of(ORGANIZATIONS[1])),
      publishOrganization: vi.fn().mockReturnValue(of({ ...ORGANIZATIONS[1], status: 'PUBLICADO' })),
      createTeam: vi.fn().mockReturnValue(of({})),
      updateTeam: vi.fn().mockReturnValue(of({})),
      publishTeam: vi.fn().mockReturnValue(of({})),
      isConflict: vi.fn().mockImplementation((error: unknown) => error instanceof HttpErrorResponse && error.status === 409),
    };

    await TestBed.configureTestingModule({
      imports: [SportsOrganizationsPageComponent],
      providers: [
        SportsOrganizationsStore,
        { provide: SportsScopeStore, useValue: scope },
        { provide: SportsOrganizationsApiAdapter, useValue: organizationAdapter },
        { provide: SportsCatalogApiAdapter, useValue: { fetchTree: vi.fn().mockReturnValue(of(TREE)) } },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SportsOrganizationsPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
  });

  it('mostra somente competicoes autorizadas por nome e nao exibe UUID no seletor', () => {
    const select = fixture.nativeElement.querySelector('#competition-select') as HTMLSelectElement;
    expect(select).not.toBeNull();
    expect(select.options[0].textContent?.trim()).toBe('Jogos UFU');
    expect(fixture.nativeElement.textContent).not.toContain('comp-a');
  });

  it('cria equipe com organizacao, edicao, modalidade e categoria selecionadas', async () => {
    component.openTeamForm();
    component.teamForm.setValue({
      organizationId: 'org-b', editionId: 'edition-a', sportId: 'sport-a', categoryId: 'category-a',
      name: 'Volei misto', slug: 'volei-misto',
    });
    await component.saveTeam();

    expect(organizationAdapter['createTeam']).toHaveBeenCalledWith(
      'edition-a', 'sport-a', 'category-a', 'org-b', 'Volei misto', 'volei-misto',
    );
  });

  it('apresenta pre-visualizacao com estado e versao antes da publicacao', () => {
    component.previewOrganization(ORGANIZATIONS[1]);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Pre-visualizacao nao publicada');
    expect(fixture.nativeElement.textContent).toContain('Rascunho');
    expect(fixture.nativeElement.textContent).toContain('v0');
  });

  it('preserva conflito 409 como mensagem recuperavel', async () => {
    organizationAdapter['createOrganization'].mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409 })),
    );
    component.openOrganizationForm();
    component.organizationForm.setValue({ name: 'Nova', slug: 'nova' });
    await component.saveOrganization();
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Este registro foi alterado por outra pessoa');
    expect(fixture.nativeElement.textContent).not.toContain('stack');
  });
});
