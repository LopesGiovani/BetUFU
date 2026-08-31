import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { BracketViewComponent } from './bracket-view.component';
import { SportsBracketSourceType } from '../../api/generated/model/sports-bracket-source-type';

const bracket = {
  bracketId: 'b1', competitionId: 'c1', editionId: 'e1', name: 'Futsal', slug: 'futsal', status: 'PUBLICADO', lockVersion: 1,
  rounds: [
    { roundNo: 1, roundName: 'Semifinais', matches: [{ matchId: 'm1', roundNo: 1, matchNo: 1, roundName: 'Semifinais', isThirdPlace: false, status: 'CONFIRMADO', slots: [{ slotNo: 1, sourceType: SportsBracketSourceType.Direct, displayLabel: 'Engenharia', score: 3, isWinner: true }, { slotNo: 2, sourceType: SportsBracketSourceType.Direct, displayLabel: 'Medicina', score: 1 }] }] },
    { roundNo: 2, roundName: 'Final', matches: [{ matchId: 'm2', roundNo: 2, matchNo: 1, roundName: 'Final', isThirdPlace: false, status: 'PENDENTE', slots: [{ slotNo: 1, sourceType: SportsBracketSourceType.PredecessorWinner, displayLabel: 'Vencedor de semifinal' }, { slotNo: 2, sourceType: SportsBracketSourceType.Bye, displayLabel: 'A definir' }] }] },
  ],
};

describe('BracketViewComponent', () => {
  it('renderiza rodadas, bye e dependência sem criar sorteio', async () => {
    await TestBed.configureTestingModule({ imports: [BracketViewComponent] }).compileComponents();
    const fixture = TestBed.createComponent(BracketViewComponent);
    fixture.componentRef.setInput('bracket', bracket);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Semifinais');
    expect(root.textContent).toContain('Vencedor de semifinal');
    expect(root.textContent).toContain('Bye');
    expect(root.querySelectorAll('.round-switcher button')).toHaveLength(2);
    expect(root.querySelector('label[for="bracket-round-select"]')?.textContent).toContain('Rodada do chaveamento');
    expect(root.textContent).not.toMatch(/sortear|sorteio/u);
  });

  it('permite trocar a rodada sem arrasto obrigatório no mobile', async () => {
    await TestBed.configureTestingModule({ imports: [BracketViewComponent] }).compileComponents();
    const fixture = TestBed.createComponent(BracketViewComponent);
    fixture.componentRef.setInput('bracket', bracket);
    fixture.detectChanges();
    (fixture.nativeElement.querySelectorAll('[role="tab"]')[1] as HTMLButtonElement).click();
    fixture.detectChanges();
    expect(fixture.componentInstance.selectedRound()).toBe(1);
    expect(fixture.nativeElement.querySelectorAll('[role="tab"]')[1].getAttribute('aria-selected')).toBe('true');
  });
});
