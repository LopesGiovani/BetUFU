import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SportsEventDetail } from '../../../../api/generated/model/sports-event-detail';
import { SportsResultVersion } from '../../../../api/generated/model/sports-result-version';
import { SportsEventResultApiAdapter } from './sports-event-result-api.adapter';
import { SportsEventResultStore } from './sports-event-result.store';

const EVENT_A: SportsEventDetail = {
  eventId: 'event-a',
  competitionId: 'comp-a',
  competitionName: 'Olimpíada UFU',
  editionId: 'edition-a',
  editionName: '2026',
  title: 'Engenharia vs Medicina',
  format: 'HEAD_TO_HEAD',
  status: 'RASCUNHO',
  scheduledAt: '2026-09-01T14:00:00Z',
  zoneId: 'America/Sao_Paulo',
  venueState: 'LOCAL_DEFINIDO',
  sourceReference: 'Boletim Oficial #01',
  lockVersion: 1,
  slots: [
    { slotNo: 1, displayLabel: 'Engenharia', teamId: 'team-eng', teamName: 'Futsal Engenharia' },
    { slotNo: 2, displayLabel: 'Medicina', teamId: 'team-med', teamName: 'Futsal Medicina' },
  ],
  createdAt: '2026-08-30T10:00:00Z',
  updatedAt: '2026-08-30T10:00:00Z',
};

const VERSION_1: SportsResultVersion = {
  versionId: 'ver-1',
  eventId: 'event-a',
  competitionId: 'comp-a',
  editionId: 'edition-a',
  versionNo: 1,
  status: 'CONFIRMADO',
  sourceReference: 'Boletim Oficial #02',
  recordedByIdentityId: 'admin-1',
  confirmedByIdentityId: 'admin-1',
  recordedAt: '2026-09-01T16:00:00Z',
  confirmedAt: '2026-09-01T16:05:00Z',
  baseLockVersion: 1,
  scores: [
    { slotNo: 1, displayLabel: 'Engenharia', score: 3, teamId: 'team-eng', teamName: 'Futsal Engenharia' },
    { slotNo: 2, displayLabel: 'Medicina', score: 2, teamId: 'team-med', teamName: 'Futsal Medicina' },
  ],
};

describe('SportsEventResultStore', () => {
  let adapter: Record<string, ReturnType<typeof vi.fn>>;
  let store: SportsEventResultStore;

  beforeEach(() => {
    adapter = {
      fetchEvents: vi.fn(() => of([EVENT_A])),
      fetchEventDetail: vi.fn(() => of(EVENT_A)),
      fetchResultVersions: vi.fn(() => of([VERSION_1])),
      createEvent: vi.fn(() => of(EVENT_A)),
      updateEventDraft: vi.fn(() => of(EVENT_A)),
      publishEvent: vi.fn(() => of({ ...EVENT_A, status: 'PUBLICADO', lockVersion: 2 })),
      suspendEvent: vi.fn(() => of({ ...EVENT_A, status: 'SUSPENSO', lockVersion: 2 })),
      postponeEvent: vi.fn(() => of({ ...EVENT_A, status: 'ADIADO', lockVersion: 2 })),
      cancelEvent: vi.fn(() => of({ ...EVENT_A, status: 'CANCELADO', lockVersion: 2 })),
      informResult: vi.fn(() => of(VERSION_1)),
      reviewResult: vi.fn(() => of(VERSION_1)),
      confirmResult: vi.fn(() => of(VERSION_1)),
      correctResult: vi.fn(() => of(VERSION_1)),
      isConflict: vi.fn((error: unknown) => error instanceof HttpErrorResponse && error.status === 409),
      isUnauthorized: vi.fn(
        (error: unknown) => error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403),
      ),
    };

    TestBed.configureTestingModule({
      providers: [
        SportsEventResultStore,
        { provide: SportsEventResultApiAdapter, useValue: adapter },
      ],
    });
    store = TestBed.inject(SportsEventResultStore);
  });

  it('carrega os eventos da edição e detalha o primeiro evento selecionado', async () => {
    await store.loadEdition('edition-a');

    expect(store.status()).toBe('ready');
    expect(store.events().length).toBe(1);
    expect(store.selectedEvent()?.eventId).toBe('event-a');
    expect(store.resultVersions().length).toBe(1);
    expect(store.confirmedVersion()?.versionId).toBe('ver-1');
  });

  it('trata edição vazia com status empty', async () => {
    adapter['fetchEvents'].mockReturnValue(of([]));
    await store.loadEdition('edition-empty');

    expect(store.status()).toBe('empty');
    expect(store.events().length).toBe(0);
    expect(store.selectedEvent()).toBeNull();
  });

  it('preserva o estado local e exibe mensagem amigável em caso de conflito HTTP 409', async () => {
    await store.loadEdition('edition-a');
    adapter['publishEvent'].mockReturnValue(throwError(() => new HttpErrorResponse({ status: 409 })));

    const result = await store.publishEvent();

    expect(result).toBe(false);
    expect(store.selectedEvent()?.eventId).toBe('event-a');
    expect(store.conflictMessage()).toContain('alterado por outra pessoa');
    expect(store.isMutating()).toBe(false);

    await store.reloadAfterConflict();
    expect(store.conflictMessage()).toBeNull();
  });

  it('limpa estado privado e define status denied em caso de HTTP 401/403', async () => {
    await store.loadEdition('edition-a');
    store.previewSnapshot(EVENT_A);
    adapter['publishEvent'].mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));

    const result = await store.publishEvent();

    expect(result).toBe(false);
    expect(store.status()).toBe('denied');
    expect(store.selectedEvent()).toBeNull();
    expect(store.resultVersions().length).toBe(0);
    expect(store.preview()).toBeNull();
    expect(store.selectedEditionId()).toBeNull();
    expect(store.errorMessage()).toContain('Seu acesso a estes eventos e resultados foi encerrado');
  });

  it('executa transições de ciclo de vida de eventos e resultados com sucesso', async () => {
    await store.loadEdition('edition-a');

    expect(await store.publishEvent()).toBe(true);
    expect(store.feedbackMessage()).toBe('Evento publicado na agenda esportiva.');

    expect(await store.suspendEvent('Motivo de chuva')).toBe(true);
    expect(store.feedbackMessage()).toBe('Evento suspenso temporariamente.');

    expect(await store.postponeEvent('2026-09-02T14:00:00Z', 'America/Sao_Paulo', 'Reagendado')).toBe(true);
    expect(store.feedbackMessage()).toBe('Evento adiado e reagendado.');

    expect(await store.cancelEvent('Cancelamento definitivo')).toBe(true);
    expect(store.feedbackMessage()).toBe('Evento cancelado de forma irreversível.');

    expect(await store.informResult('Boletim Oficial #01', [{ slotNo: 1, score: 2 }, { slotNo: 2, score: 1 }])).toBe(
      true,
    );
    expect(store.feedbackMessage()).toBe('Rascunho de resultado registrado (INFORMADO).');

    expect(await store.reviewResult('Boletim Oficial #01', [{ slotNo: 1, score: 2 }, { slotNo: 2, score: 1 }])).toBe(
      true,
    );
    expect(store.feedbackMessage()).toBe('Resultado enviado para revisão (AGUARDANDO_REVISAO).');

    expect(await store.confirmResult('Boletim Oficial #01', [{ slotNo: 1, score: 2 }, { slotNo: 2, score: 1 }])).toBe(
      true,
    );
    expect(store.feedbackMessage()).toBe('Resultado confirmado com fonte oficial (CONFIRMADO).');

    expect(
      await store.correctResult('Erro na súmula', 'Boletim Retificado #02', [
        { slotNo: 1, score: 3 },
        { slotNo: 2, score: 1 },
      ]),
    ).toBe(true);
    expect(store.feedbackMessage()).toBe('Correção de resultado publicada como nova versão auditada (CORRIGIDO).');
  });
});
