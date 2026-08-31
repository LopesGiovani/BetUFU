import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { signal } from '@angular/core';
import { describe, expect, it, vi } from 'vitest';
import { PublicStandingsPageComponent } from './public-standings-page.component';
import { PublicStandingsStore } from './public-standings.store';
import { SportsStandingsSnapshot } from '../../../api/generated/model/sports-standings-snapshot';

const mockSnapshot: SportsStandingsSnapshot = {
  standingsId: 's1',
  competitionId: 'c1',
  editionId: 'e1',
  name: 'Grupo A - Futsal',
  slug: 'grupo-a-futsal',
  status: 'PUBLICADO',
  versionNo: 1,
  sourceReference: 'https://proae.ufu.br/boletim-futsal-04',
  competitionName: 'Olimpíada Universitária',
  editionName: '2026',
  phaseName: 'Fase de Grupos',
  publishedAt: '2026-08-31T18:00:00Z',
  lockVersion: 1,
  columns: ['PTS', 'J', 'V', 'E', 'D', 'GP', 'GC', 'SG'],
  rows: [
    { position: 1, participantName: 'Engenharia Futsal', values: ['6', '2', '2', '0', '0', '8', '2', '6'] },
    { position: 2, participantName: 'Medicina Futsal', values: ['3', '2', '1', '0', '1', '5', '4', '1'] },
  ],
};

describe('PublicStandingsPageComponent', () => {
  it('carrega por id opaco e renderiza somente a publicacao oficial', async () => {
    const store = {
      isLoading: signal(false),
      isNotFound: signal(false),
      isError: signal(false),
      errorMessage: signal(null),
      data: signal(mockSnapshot),
      retry: vi.fn(),
      load: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [PublicStandingsPageComponent],
      providers: [
        { provide: PublicStandingsStore, useValue: store },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 's1' } } } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(PublicStandingsPageComponent);
    fixture.detectChanges();

    expect(store.load).toHaveBeenCalledWith('s1');
    const text = fixture.nativeElement.textContent;
    expect(text).toContain('Grupo A - Futsal');
    expect(text).toContain('Olimpíada Universitária');
    expect(text).toContain('Engenharia Futsal');
    expect(text).toContain('P8 · Arena pública');
    expect(text).not.toContain('Rascunho');
  });

  it('exibe estado de carregamento', async () => {
    const store = {
      isLoading: signal(true),
      isNotFound: signal(false),
      isError: signal(false),
      errorMessage: signal(null),
      data: signal(null),
      retry: vi.fn(),
      load: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [PublicStandingsPageComponent],
      providers: [
        { provide: PublicStandingsStore, useValue: store },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 's1' } } } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(PublicStandingsPageComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Carregando classificação');
  });

  it('exibe estado de nao encontrado (404)', async () => {
    const store = {
      isLoading: signal(false),
      isNotFound: signal(true),
      isError: signal(false),
      errorMessage: signal('Esta classificação ainda não possui publicação pública.'),
      data: signal(null),
      retry: vi.fn(),
      load: vi.fn(),
    };

    await TestBed.configureTestingModule({
      imports: [PublicStandingsPageComponent],
      providers: [
        { provide: PublicStandingsStore, useValue: store },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 's1' } } } },
      ],
    }).compileComponents();

    const fixture = TestBed.createComponent(PublicStandingsPageComponent);
    fixture.detectChanges();

    expect(fixture.nativeElement.textContent).toContain('Classificação não encontrada');
  });
});
