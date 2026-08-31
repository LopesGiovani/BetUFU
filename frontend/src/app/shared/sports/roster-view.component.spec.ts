import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SportsRosterPublication } from '../../api/generated/model/sports-roster-publication';
import { RosterViewComponent } from './roster-view.component';

const PUBLICATION: SportsRosterPublication = {
  publicationId: 'publication-a',
  rosterId: 'roster-a',
  teamId: 'team-a',
  versionNo: 1,
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
  reason: null,
  publishedAt: '2026-08-30T12:00:00Z',
  baseLockVersion: 1,
  members: [{ athleteId: 'athlete-a', athleteName: 'Ana Silva', athleteSlug: 'ana-silva', jerseyNumber: 10, positionName: 'Ala' }],
};

describe('RosterViewComponent', () => {
  let fixture: ComponentFixture<RosterViewComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [RosterViewComponent] }).compileComponents();
    fixture = TestBed.createComponent(RosterViewComponent);
  });

  it('renderiza snapshot publicado sem dados privados', () => {
    fixture.componentInstance.snapshot = PUBLICATION;
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Futsal Engenharia');
    expect(root.textContent).toContain('Ana Silva');
    expect(root.textContent).toContain('Versão 1');
    expect(root.textContent).not.toContain('matricula');
    expect(root.textContent).not.toContain('HMAC');
  });

  it('mostra estado honesto quando nao ha snapshot', () => {
    fixture.detectChanges();
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Nenhum elenco selecionado');
  });
});
