import { HttpErrorResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SportsAthleteSummary } from '../../../../api/generated/model/sports-athlete-summary';
import { SportsTeamSummary } from '../../../../api/generated/model/sports-team-summary';
import { SportsOrganizationsApiAdapter } from '../organizations/sports-organizations-api.adapter';
import { SportsScopeStore } from '../scope/sports-scope.store';
import { SportsAthletesApiAdapter } from './sports-athletes-api.adapter';
import { SportsAthletesPageComponent } from './sports-athletes-page.component';
import { SportsAthletesStore } from './sports-athletes.store';

const ATHLETES: readonly SportsAthleteSummary[] = [
  {
    athleteId: 'ath-1',
    name: 'Carlos Silva',
    slug: 'carlos-silva',
    status: 'RASCUNHO',
    lockVersion: 0,
  },
  {
    athleteId: 'ath-2',
    name: 'Beatriz Costa',
    slug: 'beatriz-costa',
    status: 'PUBLICADO',
    lockVersion: 1,
  },
];

const TEAMS: readonly SportsTeamSummary[] = [
  {
    teamId: 'team-a',
    organizationId: 'org-a',
    organizationName: 'Atletica UFU',
    competitionId: 'comp-a',
    editionId: 'edition-a',
    sportId: 'sport-a',
    sportName: 'Futsal',
    categoryId: 'category-a',
    categoryName: 'Livre',
    name: 'Futsal Masculino',
    slug: 'futsal-masculino',
    status: 'PUBLICADO',
    lockVersion: 0,
  },
];

describe('SportsAthletesPageComponent (O4)', () => {
  let fixture: ComponentFixture<SportsAthletesPageComponent>;
  let component: SportsAthletesPageComponent;
  let scope: ReturnType<typeof createScopeMock>;
  let athleteAdapter: Record<string, ReturnType<typeof vi.fn>>;
  let organizationAdapter: Record<string, ReturnType<typeof vi.fn>>;

  function createScopeMock() {
    return {
      competitions: signal([
        {
          competitionId: 'comp-a',
          name: 'Jogos UFU',
          slug: 'jogos-ufu',
          status: 'ATIVA',
          lockVersion: 0,
        },
      ]),
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
    athleteAdapter = {
      fetchAthletes: vi.fn().mockReturnValue(of(ATHLETES)),
      createAthlete: vi.fn().mockReturnValue(of(ATHLETES[0])),
      updateAthlete: vi.fn().mockReturnValue(of(ATHLETES[0])),
      publishAthlete: vi
        .fn()
        .mockReturnValue(of({ ...ATHLETES[0], status: 'PUBLICADO', lockVersion: 1 })),
      isConflict: vi
        .fn()
        .mockImplementation(
          (error: unknown) => error instanceof HttpErrorResponse && error.status === 409,
        ),
    };
    organizationAdapter = {
      fetchTeams: vi.fn().mockReturnValue(of(TEAMS)),
    };

    await TestBed.configureTestingModule({
      imports: [SportsAthletesPageComponent],
      providers: [
        SportsAthletesStore,
        { provide: SportsScopeStore, useValue: scope },
        { provide: SportsAthletesApiAdapter, useValue: athleteAdapter },
        { provide: SportsOrganizationsApiAdapter, useValue: organizationAdapter },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SportsAthletesPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
    await vi.waitFor(() => expect(component.store.status()).toBe('ready'));
    fixture.detectChanges();
  });

  it('mostra somente competicoes autorizadas por nome e nao exibe UUID no seletor', () => {
    const select = fixture.nativeElement.querySelector('#competition-select') as HTMLSelectElement;
    expect(select).not.toBeNull();
    expect(select.options[0].textContent?.trim()).toBe('Jogos UFU');
    expect(fixture.nativeElement.textContent).not.toContain('comp-a');
  });

  it('mostra lista de atletas cadastrados com status e versao', () => {
    expect(fixture.nativeElement.textContent).toContain('Carlos Silva');
    expect(fixture.nativeElement.textContent).toContain('Beatriz Costa');
    expect(fixture.nativeElement.textContent).toContain('v0');
    expect(fixture.nativeElement.textContent).toContain('v1');
  });

  it('cria atleta com equipe selecionada, nome, slug e matricula transitoria', async () => {
    component.openAthleteForm();
    component.athleteForm.setValue({
      teamId: 'team-a',
      name: 'Daniel Santos',
      slug: 'daniel-santos',
      matricula: '123456789',
    });
    await component.saveAthlete();

    expect(athleteAdapter['createAthlete']).toHaveBeenCalledWith(
      'team-a',
      'Daniel Santos',
      'daniel-santos',
      '123456789',
    );
  });

  it('limpa matricula imediatamente do formulario e do DOM apos submit com sucesso', async () => {
    component.openAthleteForm();
    component.athleteForm.setValue({
      teamId: 'team-a',
      name: 'Daniel Santos',
      slug: 'daniel-santos',
      matricula: '123456789',
    });
    await component.saveAthlete();
    fixture.detectChanges();

    expect(component.athleteForm.controls.matricula.value).toBe('');
    expect(component.activeForm()).toBeNull();
    expect(fixture.nativeElement.textContent).not.toContain('123456789');
  });

  it('limpa matricula imediatamente do formulario e do DOM mesmo quando submit falha com erro', async () => {
    athleteAdapter['createAthlete'].mockReturnValue(
      throwError(() => new Error('Falha no servidor')),
    );
    component.openAthleteForm();
    component.athleteForm.setValue({
      teamId: 'team-a',
      name: 'Daniel Santos',
      slug: 'daniel-santos',
      matricula: '987654321',
    });
    await component.saveAthlete();
    fixture.detectChanges();

    expect(component.athleteForm.controls.matricula.value).toBe('');
    expect(fixture.nativeElement.textContent).not.toContain('987654321');
  });

  it('limpa matricula imediatamente ao cancelar ou fechar o formulario', () => {
    component.openAthleteForm();
    component.athleteForm.patchValue({ matricula: '555444333' });
    component.closeForm();
    fixture.detectChanges();

    expect(component.athleteForm.controls.matricula.value).toBe('');
    expect(component.activeForm()).toBeNull();
  });

  it('nao retem matricula em Signals nem no storage do navegador', async () => {
    component.openAthleteForm();
    component.athleteForm.setValue({
      teamId: 'team-a',
      name: 'Daniel Santos',
      slug: 'daniel-santos',
      matricula: '111222333',
    });
    await component.saveAthlete();

    const athleteSummaries = component.store.athletes();
    for (const athlete of athleteSummaries) {
      expect((athlete as Record<string, unknown>)['matricula']).toBeUndefined();
      expect((athlete as Record<string, unknown>)['hmac']).toBeUndefined();
      expect((athlete as Record<string, unknown>)['fingerprint']).toBeUndefined();
    }
  });

  it('atualiza atleta existente com nome e slug sem matricula', async () => {
    component.editAthlete(ATHLETES[0]);
    component.athleteForm.controls.name.setValue('Carlos Silva Editado');
    component.athleteForm.controls.slug.setValue('carlos-silva-editado');
    await component.saveAthlete();

    expect(athleteAdapter['updateAthlete']).toHaveBeenCalledWith(
      'ath-1',
      0,
      'Carlos Silva Editado',
      'carlos-silva-editado',
    );
  });

  it('publica atleta com confirmacao e versao de bloqueio', async () => {
    vi.spyOn(window, 'confirm').mockReturnValue(true);
    await component.publishAthlete(ATHLETES[0]);

    expect(athleteAdapter['publishAthlete']).toHaveBeenCalledWith('ath-1', 0);
  });

  it('apresenta pre-visualizacao de atleta com estado e versao antes da publicacao', () => {
    component.previewAthlete(ATHLETES[0]);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Pre-visualizacao nao publicada');
    expect(fixture.nativeElement.textContent).toContain('Carlos Silva');
    expect(fixture.nativeElement.textContent).toContain('Atleta global');
    expect(fixture.nativeElement.textContent).toContain('Rascunho');
    expect(fixture.nativeElement.textContent).toContain('v0');
  });

  it('preserva conflito 409 como mensagem recuperavel', async () => {
    athleteAdapter['createAthlete'].mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409 })),
    );
    component.openAthleteForm();
    component.athleteForm.setValue({
      teamId: 'team-a',
      name: 'Conflito',
      slug: 'conflito',
      matricula: '',
    });
    await component.saveAthlete();
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain(
      'Este registro foi alterado por outra pessoa',
    );
    expect(fixture.nativeElement.textContent).not.toContain('stack');
  });
});
