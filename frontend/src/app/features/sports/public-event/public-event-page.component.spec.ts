import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { signal } from '@angular/core';
import { describe, expect, it, vi } from 'vitest';
import { PublicEventDetail } from '../../../api/generated/model/public-event-detail';
import { PublicEventPageComponent } from './public-event-page.component';
import { SportsPublicViewStore } from '../public-view/sports-public-view.store';

describe('PublicEventPageComponent', () => {
  it('carrega evento por deep link e mostra fonte sem resultado fictício', async () => {
    const store = {
      isLoading: signal(false),
      isNotFound: signal(false),
      isError: signal(false),
      errorMessage: signal(null),
      data: signal<PublicEventDetail>({
        eventId: 'e1',
        marketId: 'm1',
        competitionId: 'c1',
        editionId: 'ed1',
        versionNo: 1,
        competitionName: 'Jogos',
        editionName: '2026',
        title: 'Final',
        format: 'HEAD_TO_HEAD',
        scheduledAt: '2026-08-31T20:00:00Z',
        zoneId: 'America/Sao_Paulo',
        venueState: 'LOCAL_A_DEFINIR',
        sourceReference: 'súmula 1',
        publishedAt: '2026-08-31T10:00:00Z',
        slots: [
          { slotNo: 1, displayLabel: 'A' },
          { slotNo: 2, displayLabel: 'B' },
        ],
        sourceHistory: [],
      }),
      retry: vi.fn(),
      load: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [PublicEventPageComponent],
      providers: [
        { provide: SportsPublicViewStore, useValue: store },
        { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'e1' } } } },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(PublicEventPageComponent);
    fixture.detectChanges();
    expect(store.load).toHaveBeenCalledWith('event', 'e1');
    expect(fixture.nativeElement.textContent).toContain('A × B');
    expect(fixture.nativeElement.textContent).toContain('Resultado ainda não publicado');
    const oddsLink = fixture.nativeElement.querySelector('a[href="/odds/mercados/m1"]');
    expect(oddsLink?.textContent).toContain('Ver odds da partida');

    store.data.update((event) => ({ ...event, marketId: null }));
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('a[href^="/odds/mercados/"]')).toBeNull();
  });
});
