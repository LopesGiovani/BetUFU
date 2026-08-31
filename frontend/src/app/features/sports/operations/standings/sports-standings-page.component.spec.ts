import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { signal } from '@angular/core';
import { describe, expect, it, vi } from 'vitest';
import { SportsStandingsPageComponent } from './sports-standings-page.component';
import { SportsStandingsStore } from './sports-standings.store';
import { SportsStandingsSnapshot } from '../../../../api/generated/model/sports-standings-snapshot';

const mockDraftSnapshot: SportsStandingsSnapshot = {
  standingsId: 's1',
  competitionId: 'c1',
  editionId: 'e1',
  name: 'Grupo A - Futsal',
  slug: 'grupo-a-futsal',
  status: 'RASCUNHO',
  lockVersion: 0,
  competitionName: 'Olimpíada Universitária',
  editionName: '2026',
  phaseName: 'Fase de Grupos',
  columns: ['PTS', 'J', 'V', 'E', 'D', 'GP', 'GC', 'SG'],
  rows: [
    { position: 1, participantName: 'Engenharia Futsal', values: ['6', '2', '2', '0', '0', '8', '2', '6'] },
    { position: 2, participantName: 'Medicina Futsal', values: ['3', '2', '1', '0', '1', '5', '4', '1'] },
  ],
};

describe('SportsStandingsPageComponent', () => {
  it('carrega preview operacional pelo id e marca rascunho com aviso de nao calculo', async () => {
    const store = {
      isLoading: signal(false),
      isMutating: signal(false),
      isDenied: signal(false),
      isNotFound: signal(false),
      isError: signal(false),
      errorMessage: signal(null),
      conflictMessage: signal(null),
      feedbackMessage: signal(null),
      data: signal(mockDraftSnapshot),
      retry: vi.fn(),
      load: vi.fn(),
      update: vi.fn(),
      publish: vi.fn(),
      correct: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [SportsStandingsPageComponent],
      providers: [
        { provide: SportsStandingsStore, useValue: store },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 's1' } } } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(SportsStandingsPageComponent);
    fixture.detectChanges();

    expect(store.load).toHaveBeenCalledWith('s1');
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('O7 · Operação esportiva');
    expect(text).toContain('Grupo A - Futsal');
    expect(text).toContain('não calcula pontuação ou desempate');
    expect(text).toContain('Publicar classificação');
    expect(text).toContain('Pré-visualização operacional');
  });

  it('exibe formulario de correcao para classificacao publicada', async () => {
    const publishedSnapshot: SportsStandingsSnapshot = {
      ...mockDraftSnapshot,
      status: 'PUBLICADO',
      versionNo: 1,
      sourceReference: 'https://proae.ufu.br/boletim-01',
      lockVersion: 1,
    };

    const store = {
      isLoading: signal(false),
      isMutating: signal(false),
      isDenied: signal(false),
      isNotFound: signal(false),
      isError: signal(false),
      errorMessage: signal(null),
      conflictMessage: signal(null),
      feedbackMessage: signal(null),
      data: signal(publishedSnapshot),
      retry: vi.fn(),
      load: vi.fn(),
      update: vi.fn(),
      publish: vi.fn(),
      correct: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [SportsStandingsPageComponent],
      providers: [
        { provide: SportsStandingsStore, useValue: store },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 's1' } } } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(SportsStandingsPageComponent);
    fixture.detectChanges();

    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Corrigir versão publicada');
    expect(text).toContain('Motivo da correção');
    expect(text).toContain('Fonte substituta');
    expect(text).toContain('Publicar nova versão');
  });

  it('exibe estado de acesso negado (401/403)', async () => {
    const store = {
      isLoading: signal(false),
      isMutating: signal(false),
      isDenied: signal(true),
      isNotFound: signal(false),
      isError: signal(false),
      errorMessage: signal('Você não tem acesso a esta classificação operacional.'),
      conflictMessage: signal(null),
      feedbackMessage: signal(null),
      data: signal(null),
      retry: vi.fn(),
      load: vi.fn(),
      update: vi.fn(),
      publish: vi.fn(),
      correct: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [SportsStandingsPageComponent],
      providers: [
        { provide: SportsStandingsStore, useValue: store },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 's1' } } } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(SportsStandingsPageComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Acesso não autorizado');
  });
});
