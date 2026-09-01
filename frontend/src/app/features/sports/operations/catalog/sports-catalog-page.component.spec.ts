import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SportsCatalogApiAdapter } from './sports-catalog-api.adapter';
import { SportsCatalogPageComponent } from './sports-catalog-page.component';
import { SportsScopeStore } from '../scope/sports-scope.store';

const scope = {
  competitions: [
    {
      competitionId: 'comp-a',
      name: 'Olimpíada UFU',
      slug: 'olimpiada-ufu',
      status: 'ATIVA' as const,
      lockVersion: 0,
      sportId: null,
      sportName: null,
    },
  ],
  bootstrapAllowed: false,
};
const tree = {
  competition: scope.competitions[0],
  sports: [
    {
      sportId: 'sport-a',
      name: 'Basquete',
      slug: 'basquete',
      status: 'ATIVA' as const,
      lockVersion: 2,
    },
  ],
  editions: [],
  categories: [],
  phases: [],
  venues: [],
};

describe('SportsCatalogPageComponent', () => {
  let adapter: {
    fetchScope: ReturnType<typeof vi.fn>;
    fetchCapabilities: ReturnType<typeof vi.fn>;
    fetchTree: ReturnType<typeof vi.fn>;
    createCompetition: ReturnType<typeof vi.fn>;
    createSport: ReturnType<typeof vi.fn>;
    createEdition: ReturnType<typeof vi.fn>;
    createCategory: ReturnType<typeof vi.fn>;
    createPhase: ReturnType<typeof vi.fn>;
    createVenue: ReturnType<typeof vi.fn>;
    createVenueSpace: ReturnType<typeof vi.fn>;
    updateSport: ReturnType<typeof vi.fn>;
    updateEdition: ReturnType<typeof vi.fn>;
    updateCategory: ReturnType<typeof vi.fn>;
    updatePhase: ReturnType<typeof vi.fn>;
    updateVenue: ReturnType<typeof vi.fn>;
    updateVenueSpace: ReturnType<typeof vi.fn>;
    isConflict: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    adapter = {
      fetchScope: vi.fn(() => of(scope)),
      fetchCapabilities: vi.fn(() => of(['MANAGE_SPORTS_CATALOG'])),
      fetchTree: vi.fn(() => of(tree)),
      createCompetition: vi.fn(),
      createSport: vi.fn(),
      createEdition: vi.fn(),
      createCategory: vi.fn(),
      createPhase: vi.fn(),
      createVenue: vi.fn(),
      createVenueSpace: vi.fn(),
      updateSport: vi.fn(),
      updateEdition: vi.fn(),
      updateCategory: vi.fn(),
      updatePhase: vi.fn(),
      updateVenue: vi.fn(),
      updateVenueSpace: vi.fn(),
      isConflict: vi.fn(() => false),
    };
  });

  async function render() {
    await TestBed.configureTestingModule({
      imports: [SportsCatalogPageComponent],
      providers: [SportsScopeStore, { provide: SportsCatalogApiAdapter, useValue: adapter }],
    }).compileComponents();
    const fixture = TestBed.createComponent(SportsCatalogPageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    await vi.waitFor(() => expect(adapter.fetchTree).toHaveBeenCalled(), { timeout: 1_000 });
    fixture.detectChanges();
    return fixture;
  }

  it('renderiza o escopo por nome e o catálogo vindo da árvore real', async () => {
    const fixture = await render();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Olimpíada UFU');
    expect(root.textContent).toContain('Basquete');
    expect(root.querySelector('input[type="text"]')).toBeNull();
    expect(root.textContent).not.toContain('scopeRef');
  });

  it('não exibe bootstrap quando o escopo tem competição', async () => {
    const fixture = await render();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain(
      'Cadastrar a primeira competição',
    );
  });

  it('abre o formulário de novo esporte sem campo de UUID', async () => {
    const fixture = await render();
    fixture.componentInstance.openForm('sport');
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('input[name="competitionId"]')).toBeNull();
    expect(root.textContent).toContain('Esporte');
  });
});
