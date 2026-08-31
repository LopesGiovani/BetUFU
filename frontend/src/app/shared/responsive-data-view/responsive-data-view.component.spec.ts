import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { ResponsiveDataViewComponent } from './responsive-data-view.component';
import { SportsStandingsSnapshot } from '../../api/generated/model/sports-standings-snapshot';

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
    { position: 3, participantName: 'Computação Futsal', values: ['0', '2', '0', '0', '2', '1', '8', '-7'] },
  ],
};

describe('ResponsiveDataViewComponent', () => {
  it('renderiza tabela semantica de classificacao com thead, tbody e colunas declaradas', async () => {
    await TestBed.configureTestingModule({ imports: [ResponsiveDataViewComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ResponsiveDataViewComponent);
    fixture.componentRef.setInput('snapshot', mockSnapshot);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Grupo A - Futsal');
    expect(root.textContent).toContain('Olimpíada Universitária');
    expect(root.textContent).toContain('Versão 1');
    expect(root.textContent).toContain('Publicado');

    const table = root.querySelector('table');
    expect(table).not.toBeNull();
    const caption = root.querySelector('caption');
    expect(caption?.textContent).toContain('Classificação oficial de Grupo A - Futsal');

    const thList = Array.from(root.querySelectorAll('th')).map((th) => th.textContent?.trim());
    expect(thList).toEqual(['Posição', 'Participante', 'PTS', 'J', 'V', 'E', 'D', 'GP', 'GC', 'SG']);

    const rows = Array.from(root.querySelectorAll('tbody tr'));
    expect(rows).toHaveLength(3);
    expect(rows[0].textContent).toContain('1º');
    expect(rows[0].textContent).toContain('Engenharia Futsal');
    expect(rows[0].textContent).toContain('6');
  });

  it('renderiza estrutura de cartoes rotulados para mobile com todas as metricas', async () => {
    await TestBed.configureTestingModule({ imports: [ResponsiveDataViewComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ResponsiveDataViewComponent);
    fixture.componentRef.setInput('snapshot', mockSnapshot);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    const cards = Array.from(root.querySelectorAll('.data-card'));
    expect(cards).toHaveLength(3);

    expect(cards[0].querySelector('.data-card__position')?.textContent).toContain('1º');
    expect(cards[0].querySelector('.data-card__participant')?.textContent).toContain('Engenharia Futsal');

    const dts = Array.from(cards[0].querySelectorAll('dt')).map((dt) => dt.textContent?.trim());
    expect(dts).toEqual(['PTS', 'J', 'V', 'E', 'D', 'GP', 'GC', 'SG']);
  });

  it('exibe aviso de que a classificacao e transcrita e nao calcula desempate', async () => {
    await TestBed.configureTestingModule({ imports: [ResponsiveDataViewComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ResponsiveDataViewComponent);
    fixture.componentRef.setInput('snapshot', mockSnapshot);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Classificação transcrita do boletim oficial');
    expect(root.textContent).toContain('O sistema não calcula pontos ou critérios de desempate');
    expect(root.querySelector('a.source-link')?.getAttribute('href')).toBe('https://proae.ufu.br/boletim-futsal-04');
  });

  it('exibe nota de preview quando em modo preview', async () => {
    await TestBed.configureTestingModule({ imports: [ResponsiveDataViewComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ResponsiveDataViewComponent);
    fixture.componentRef.setInput('snapshot', { ...mockSnapshot, status: 'RASCUNHO' });
    fixture.componentRef.setInput('preview', true);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Pré-visualização operacional');
    expect(root.textContent).toContain('Pré-visualização não publicada');
    expect(root.textContent).toContain('Rascunho');
  });

  it('renderiza estado vazio quando snapshot for nulo', async () => {
    await TestBed.configureTestingModule({ imports: [ResponsiveDataViewComponent] }).compileComponents();
    const fixture = TestBed.createComponent(ResponsiveDataViewComponent);
    fixture.componentRef.setInput('snapshot', null);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Classificação sem dados');
  });
});
