import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { SportsPublicService } from '../../../api/generated/api/sports-public.service';
import { PublicSportsEventPage } from '../../../api/generated/model/public-sports-event-page';
import { PublicSportsEventSummary } from '../../../api/generated/model/public-sports-event-summary';
import { PublicAgendaApiAdapter } from './public-agenda-api.adapter';

describe('PublicAgendaApiAdapter', () => {
  let adapter: PublicAgendaApiAdapter;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        PublicAgendaApiAdapter,
        { provide: SportsPublicService, useValue: { listPublishedSportsEvents: vi.fn() } },
      ],
    });
    adapter = TestBed.inject(PublicAgendaApiAdapter);
  });

  function event(
    format: PublicSportsEventSummary['format'],
    overrides: Partial<PublicSportsEventSummary> = {},
  ): PublicSportsEventSummary {
    return {
      eventId: '11111111-1111-4111-8111-111111111111',
      competitionId: '22222222-2222-4222-8222-222222222222',
      editionId: '33333333-3333-4333-8333-333333333333',
      competitionName: 'Jogos UFU',
      editionName: '2026',
      title: 'Natacao - 100m livre',
      format,
      scheduledAt: '2026-09-01T18:00:00Z',
      zoneId: 'America/Sao_Paulo',
      venueState: 'LOCAL_DEFINIDO',
      slots: [
        { slotNo: 1, displayLabel: 'Atleta A' },
        { slotNo: 2, displayLabel: 'Atleta B' },
      ],
      venueName: 'Centro Esportivo UFU',
      venueSpaceName: 'Piscina',
      ...overrides,
    };
  }

  function page(item: PublicSportsEventSummary): PublicSportsEventPage {
    return { items: [item], page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false };
  }

  it('preserva o titulo de GENERIC e nao fabrica confronto a partir de dois slots', () => {
    const adapted = adapter.adapt(page(event('GENERIC')));
    const [view] = adapted.events;

    expect(view.eventName).toBe('Natacao - 100m livre');
    expect(view.formatLabel).toBe('Agenda b\u00e1sica');
    expect(view.venueLabel).toBe('Centro Esportivo UFU \u00b7 Piscina');
    expect(adapted.states).toEqual([{ id: 'LOCAL_DEFINIDO', label: 'Local definido' }]);
  });

  it('mantem a derivacao de participantes somente para HEAD_TO_HEAD', () => {
    const adapted = adapter.adapt(
      page(event('HEAD_TO_HEAD', {
        title: 'Final',
        slots: [
          { slotNo: 1, displayLabel: 'Engenharia' },
          { slotNo: 2, displayLabel: 'Medicina' },
        ],
      })),
    );
    const [view] = adapted.events;

    expect(view.eventName).toBe('Engenharia \u00d7 Medicina');
    expect(view.formatLabel).toBe('Confronto');
  });

  it('recusa formato que nao pertence ao enum publico', () => {
    expect(() => adapter.adapt(page(event('GENERIC', { format: 'UNKNOWN' as never })))).toThrow(
      /CONTRATO_AGENDA_PUBLICA_INVALIDO: formato invalido/,
    );
  });
});
