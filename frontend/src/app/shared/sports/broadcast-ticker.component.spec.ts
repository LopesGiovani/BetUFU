import { TestBed } from '@angular/core/testing';
import { describe, expect, it } from 'vitest';
import { PublicAgendaEventViewModel } from '../../features/sports/public-agenda/public-agenda-api.adapter';
import { BroadcastTickerComponent } from './broadcast-ticker.component';

const EVENT: PublicAgendaEventViewModel = {
  id: 'evt-1',
  competitionId: 'cmp-1',
  editionId: 'ed-1',
  competitionName: 'Jogos UFU',
  editionName: '2026',
  title: 'Engenharia contra Direito',
  eventName: 'Engenharia × Direito',
  scheduledAt: '2026-08-29T21:30:00Z',
  timestamp: 1,
  dateKey: '2026-08-29',
  dateLabel: 'sábado, 29 de agosto de 2026',
  shortDateLabel: '29 ago. 2026',
  dayLabel: '29',
  monthYearLabel: 'agosto de 2026',
  timeLabel: '18:30',
  zoneLabel: 'BRT',
  formatLabel: 'Confronto',
  venueLabel: 'Local a definir',
  contextLabel: 'Confronto · Jogos UFU · 2026',
  detailHref: '/eventos/evt-1',
};

describe('BroadcastTickerComponent', () => {
  it('repete somente a cópia visual e mantém o loop ao passar o ponteiro', async () => {
    await TestBed.configureTestingModule({
      imports: [BroadcastTickerComponent],
    }).compileComponents();
    const fixture = TestBed.createComponent(BroadcastTickerComponent);
    fixture.componentRef.setInput('events', [EVENT]);
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    const ticker = root.querySelector('.ticker') as HTMLElement;
    ticker.dispatchEvent(new PointerEvent('pointerenter'));
    fixture.detectChanges();

    expect(ticker.classList.contains('is-paused')).toBe(false);
    expect(root.querySelector('.ticker__track')).not.toBeNull();
    expect(root.querySelectorAll('a')).toHaveLength(1);
    expect(root.querySelector('a')?.getAttribute('href')).toBe('/eventos/evt-1');
    expect(root.querySelectorAll('[aria-hidden="true"]')).toHaveLength(1);
  });
});
