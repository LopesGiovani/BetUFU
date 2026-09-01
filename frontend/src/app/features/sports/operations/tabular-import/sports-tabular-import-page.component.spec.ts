import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SportsTabularImportPageComponent } from './sports-tabular-import-page.component';
import { SportsTabularImportStore } from './sports-tabular-import.store';
import { SportsTabularImportApiAdapter } from './sports-tabular-import-api.adapter';
import { SportsScopeStore } from '../scope/sports-scope.store';
import { SportsCatalogApiAdapter } from '../catalog/sports-catalog-api.adapter';

const SCOPE = {
  competitions: signal([
    {
      competitionId: 'competition-1',
      name: 'Olimpiada UFU',
      slug: 'olimpiada-ufu',
      status: 'ATIVA',
      lockVersion: 0,
    },
  ]),
  selectedCompetitionId: signal<string | null>('competition-1'),
  hasSelection: signal(true),
  isLoading: signal(false),
  isDenied: signal(false),
  isEmpty: signal(false),
  isError: signal(false),
  errorMessage: signal<string | null>(null),
  load: vi.fn().mockResolvedValue(undefined),
  selectCompetition: vi.fn(() => true),
};

const TREE = {
  competition: {
    competitionId: 'competition-1',
    name: 'Olimpiada UFU',
    slug: 'olimpiada-ufu',
    status: 'ATIVA',
    lockVersion: 0,
  },
  sports: [],
  editions: [
    {
      editionId: 'edition-1',
      competitionId: 'competition-1',
      name: '2026',
      slug: '2026',
      startDate: null,
      endDate: null,
      status: 'ATIVA',
      lockVersion: 0,
    },
  ],
  categories: [],
  phases: [],
  venues: [],
};

describe('SportsTabularImportPageComponent', () => {
  let fixture: ComponentFixture<SportsTabularImportPageComponent>;
  let store: Record<string, ReturnType<typeof signal> | ReturnType<typeof vi.fn>>;
  let importAdapter: Record<string, ReturnType<typeof vi.fn>>;

  beforeEach(async () => {
    store = {
      status: signal('initial'),
      fileName: signal<string | null>(null),
      format: signal<string | null>(null),
      preview: signal(null),
      result: signal(null),
      errorMessage: signal<string | null>(null),
      feedbackMessage: signal<string | null>(null),
      isLoading: signal(false),
      isDenied: signal(false),
      isIndeterminate: signal(false),
      hasFile: signal(false),
      canValidate: signal(false),
      canCommit: signal(false),
      selectFile: vi.fn(() => true),
      validate: vi.fn().mockResolvedValue(true),
      commit: vi.fn().mockResolvedValue(true),
      checkStatus: vi.fn().mockResolvedValue(true),
      reset: vi.fn(),
    };
    importAdapter = { detectFormat: vi.fn(() => 'CSV') };
    await TestBed.configureTestingModule({
      imports: [SportsTabularImportPageComponent],
      providers: [
        { provide: SportsScopeStore, useValue: SCOPE },
        { provide: SportsCatalogApiAdapter, useValue: { fetchTree: vi.fn(() => of(TREE)) } },
        { provide: SportsTabularImportStore, useValue: store },
        { provide: SportsTabularImportApiAdapter, useValue: importAdapter },
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(SportsTabularImportPageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    await vi.waitFor(
      () => expect(fixture.componentInstance.selectedEditionId()).toBe('edition-1'),
      {
        timeout: 1_000,
      },
    );
    fixture.detectChanges();
  });

  it('seleciona sem enviar automaticamente e exibe a edição autorizada', () => {
    const input = fixture.nativeElement.querySelector('input[type="file"]') as HTMLInputElement;
    const file = new File(['dados'], 'elenco.csv', { type: 'text/csv' });
    Object.defineProperty(input, 'files', { value: [file] });
    input.dispatchEvent(new Event('change'));
    fixture.detectChanges();

    expect(store['selectFile']).toHaveBeenCalledWith(file);
    expect(store['validate']).not.toHaveBeenCalled();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('2026');
  });

  it('não renderiza matrícula nem conteúdo bruto no workspace', () => {
    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).not.toContain('matricula');
    expect(root.textContent).not.toContain('12345678');
    expect(root.textContent).not.toContain('scopeRef');
    expect(root.querySelector('input[type="file"]')).not.toBeNull();
  });

  it('oferece templates para os dois formatos', () => {
    const buttons = Array.from(
      fixture.nativeElement.querySelectorAll('button'),
    ) as HTMLButtonElement[];
    expect(buttons.map((button) => button.textContent?.trim())).toEqual(
      expect.arrayContaining(['Baixar template CSV', 'Baixar template XLSX']),
    );
  });
});
