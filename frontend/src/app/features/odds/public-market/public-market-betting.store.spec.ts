import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { OddsMarketState } from '../../../api/generated/model/odds-market-state';
import { PublicOddsExplanation } from '../../../api/generated/model/public-odds-explanation';
import { PublicOddsView } from '../../../api/generated/model/public-odds-view';
import { SessionStore } from '../../../core/session/session.store';
import { AppClock } from '../../../core/time/app-clock';
import { EnrollmentStore } from '../../enrollment/enrollment.store';
import { PublicMarketApiAdapter, PublicMarketSnapshot } from './public-market-api.adapter';
import { BettingTicket, PublicMarketBettingApiAdapter } from './public-market-betting-api.adapter';
import { PublicMarketBettingStore } from './public-market-betting.store';

const MARKET_ID = '11111111-1111-4111-8111-111111111111';
const VERSION_1 = '22222222-2222-4222-8222-222222222222';
const PUBLICATION_1 = '33333333-3333-4333-8333-333333333333';
const VERSION_2 = '55555555-5555-4555-8555-555555555555';
const PUBLICATION_2 = '66666666-6666-4666-8666-666666666666';

function acceptedTicket(overrides: Partial<BettingTicket> = {}): BettingTicket {
  return {
    predictionId: '44444444-4444-4444-8444-444444444444',
    marketId: MARKET_ID,
    marketVersionId: VERSION_1,
    publicationId: PUBLICATION_1,
    selection: 'HOME',
    stake: 10,
    acceptedOdds: 1.82,
    potentialReturn: 18.2,
    netProfit: 8.2,
    ruleVersion: 'elo-v1',
    closeDeadline: '2026-09-15T12:00:00Z',
    state: 'PENDING',
    acceptedAt: '2026-09-14T12:01:00Z',
    ...overrides,
  };
}

function refreshedSnapshot(overrides: Partial<PublicMarketSnapshot> = {}): PublicMarketSnapshot {
  return {
    marketId: MARKET_ID,
    marketVersionId: VERSION_2,
    eventId: '77777777-7777-4777-8777-777777777777',
    marketType: PublicOddsView.MarketTypeEnum.HeadToHead,
    currentPublicationId: PUBLICATION_2,
    ruleVersion: 'elo-v2',
    closeDeadline: '2026-09-15T12:00:00Z',
    closeDeadlineLabel: '15 de set. de 2026, 09:00',
    state: OddsMarketState.Open,
    stateLabel: 'Aberto',
    publishedAt: '2026-09-14T12:01:00Z',
    publishedAtLabel: '14 de set. de 2026, 09:01',
    selections: [
      { code: 'HOME', label: 'Mandante', odd: 1.9 },
      { code: 'AWAY', label: 'Visitante', odd: 1.98 },
    ],
    history: [],
    explanation: {
      algorithmVersion: 'elo-v2',
      configurationVersion: 'cfg-v2',
      fairProbability: 0.54,
      margin: 0.05,
      roundingMode: PublicOddsExplanation.RoundingModeEnum.HalfUp,
      confidence: 0.8,
      confidenceBand: PublicOddsExplanation.ConfidenceBandEnum.High,
      sampleSize: 18,
    },
    confidenceLabel: 'Confianca alta',
    isLowConfidence: false,
    ...overrides,
  };
}

describe('PublicMarketBettingStore', () => {
  function setup(
    options: {
      authenticated?: boolean;
      registered?: boolean;
      now?: string;
      deadline?: string;
      marketState?: OddsMarketState;
    } = {},
  ) {
    const authenticated = signal(options.authenticated ?? true);
    const registered = signal(options.registered ?? true);
    const enrollmentError = signal(false);
    const publicSnapshot = signal<PublicMarketSnapshot | null>(null);
    let now = Date.parse(options.now ?? '2026-09-15T11:00:00Z');
    let deadlineTask: (() => void) | null = null;
    const clock = {
      now: vi.fn(() => now),
      scheduleAt: vi.fn((_instant: number, task: () => void) => {
        deadlineTask = task;
        return vi.fn();
      }),
    };
    const api = {
      getBalance: vi.fn().mockResolvedValue({
        balance: 1000,
        scale: 2,
        minimumStake: 10,
        rulesVersion: 'credits-v1',
      }),
      getOwnTicket: vi.fn().mockResolvedValue(null),
      submit: vi.fn().mockResolvedValue(acceptedTicket()),
    };
    const session = {
      loadSession: vi.fn().mockResolvedValue(undefined),
      isAuthenticated: authenticated,
    };
    const enrollment = {
      loadEnrollment: vi.fn().mockResolvedValue(undefined),
      isRegistered: registered,
      isError: enrollmentError,
    };
    const publicMarket = { fetchMarket: vi.fn(() => of(publicSnapshot()!)) };

    TestBed.configureTestingModule({
      providers: [
        PublicMarketBettingStore,
        { provide: PublicMarketBettingApiAdapter, useValue: api },
        { provide: SessionStore, useValue: session },
        { provide: EnrollmentStore, useValue: enrollment },
        { provide: PublicMarketApiAdapter, useValue: publicMarket },
        { provide: AppClock, useValue: clock },
      ],
    });
    const store = TestBed.inject(PublicMarketBettingStore);
    store.configure(MARKET_ID, {
      marketVersionId: VERSION_1,
      publicationId: PUBLICATION_1,
      ruleVersion: 'elo-v1',
      closeDeadline: options.deadline ?? '2026-09-15T12:00:00Z',
      state: options.marketState ?? OddsMarketState.Open,
      selections: [
        { code: 'HOME', label: 'Mandante', odd: 1.82 },
        { code: 'AWAY', label: 'Visitante', odd: 2.05 },
      ],
    });
    return {
      store,
      api,
      session,
      enrollment,
      publicMarket,
      publicSnapshot,
      advanceTo(instant: string): void {
        now = Date.parse(instant);
        deadlineTask?.();
      },
    };
  }

  it('aplica deadline no instante exato antes de sessao e elegibilidade', async () => {
    const before = setup({ authenticated: false, now: '2026-09-15T11:59:59.999Z' });
    expect(before.store.status()).toBe('visitor');
    await before.store.load();
    expect(before.store.status()).toBe('visitor');

    TestBed.resetTestingModule();
    const atDeadline = setup({ authenticated: false, now: '2026-09-15T12:00:00Z' });
    expect(atDeadline.store.status()).toBe('closed');
    await atDeadline.store.load();
    expect(atDeadline.store.status()).toBe('closed');
    expect(atDeadline.enrollment.loadEnrollment).not.toHaveBeenCalled();
    expect(atDeadline.api.getBalance).not.toHaveBeenCalled();

    TestBed.resetTestingModule();
    const after = setup({ authenticated: false, now: '2026-09-15T12:00:00.001Z' });
    expect(after.store.status()).toBe('closed');
  });

  it('faz indisponibilidade do mercado prevalecer sobre login e matricula', async () => {
    const suspended = setup({
      authenticated: false,
      registered: false,
      marketState: OddsMarketState.Suspended,
    });

    expect(suspended.store.status()).toBe('suspended');
    await suspended.store.load();

    expect(suspended.store.status()).toBe('suspended');
    expect(suspended.enrollment.loadEnrollment).not.toHaveBeenCalled();
    expect(suspended.api.getBalance).not.toHaveBeenCalled();

    TestBed.resetTestingModule();
    const locked = setup({ marketState: OddsMarketState.Locked });
    expect(locked.store.status()).toBe('closed');
  });

  it('fecha de forma reativa quando o prazo passa com a pagina aberta', async () => {
    const { store, advanceTo } = setup();
    await store.load();
    expect(store.status()).toBe('eligible');

    advanceTo('2026-09-15T12:00:00Z');

    expect(store.status()).toBe('closed');
    expect(store.canSubmit()).toBe(false);
  });

  it('mantem comprovante aceito visivel quando o prazo passa', async () => {
    const { store, api, advanceTo } = setup();
    api.getOwnTicket.mockResolvedValue(acceptedTicket());
    await store.load();
    expect(store.status()).toBe('success');

    advanceTo('2026-09-15T12:00:00Z');

    expect(store.status()).toBe('success');
    expect(store.ownTicket()?.predictionId).toBe('44444444-4444-4444-8444-444444444444');
  });

  it('recupera comprovante proprio mesmo ao recarregar mercado ja encerrado', async () => {
    const { store, api, enrollment } = setup({ now: '2026-09-15T12:00:00Z' });
    api.getOwnTicket.mockResolvedValue(acceptedTicket());

    await store.load();

    expect(store.status()).toBe('success');
    expect(store.ownTicket()?.predictionId).toBe('44444444-4444-4444-8444-444444444444');
    expect(enrollment.loadEnrollment).not.toHaveBeenCalled();
    expect(api.getBalance).not.toHaveBeenCalled();
  });

  it('orienta visitante e conta sem vinculo sem consultar saldo', async () => {
    const visitor = setup({ authenticated: false });
    await visitor.store.load();
    expect(visitor.store.status()).toBe('visitor');
    expect(visitor.api.getBalance).not.toHaveBeenCalled();

    TestBed.resetTestingModule();
    const noEnrollment = setup({ registered: false });
    await noEnrollment.store.load();
    expect(noEnrollment.store.status()).toBe('no-enrollment');
    expect(noEnrollment.api.getBalance).not.toHaveBeenCalled();
  });

  it('carrega saldo/ticket proprio e preserva o comprovante apos nova leitura', async () => {
    const { store, api } = setup();
    api.getOwnTicket.mockResolvedValue(acceptedTicket());

    await store.load();

    expect(store.status()).toBe('success');
    expect(store.ownTicket()?.predictionId).toBe('44444444-4444-4444-8444-444444444444');
    expect(api.getOwnTicket).toHaveBeenCalledWith(MARKET_ID);
  });

  it('envia codigo HOME e snapshots reais sem exigir confirmacao inicial', async () => {
    const { store, api } = setup();
    await store.load();

    await expect(store.submit('HOME', 10, false)).resolves.toBe('success');

    expect(api.submit).toHaveBeenCalledTimes(1);
    expect(api.submit.mock.calls[0][0]).toEqual({
      marketId: MARKET_ID,
      marketVersionId: VERSION_1,
      publicationId: PUBLICATION_1,
      ruleVersion: 'elo-v1',
      selection: 'HOME',
      stake: 10,
    });
    expect(api.submit.mock.calls[0][1]).toEqual(expect.any(String));
  });

  it('aceita duas casas decimais sem erro binario e rejeita precisao excedente', async () => {
    const valid = setup();
    await valid.store.load();

    await expect(valid.store.submit('HOME', 10.13, false)).resolves.toBe('success');
    expect(valid.api.submit).toHaveBeenCalledWith(
      expect.objectContaining({ stake: 10.13 }),
      expect.any(String),
    );

    TestBed.resetTestingModule();
    const invalid = setup();
    await invalid.store.load();

    await expect(invalid.store.submit('HOME', 10.001, false)).resolves.toBe('ignored');
    expect(invalid.api.submit).not.toHaveBeenCalled();
  });

  it('faz GET seguro no stale, limpa a autoridade antiga e exige novo gesto antes do POST', async () => {
    const { store, api, publicMarket, publicSnapshot } = setup();
    await store.load();
    api.submit
      .mockRejectedValueOnce(
        new HttpErrorResponse({
          status: 409,
          error: { code: 'BETUFU-409-STALE_VERSION' },
        }),
      )
      .mockResolvedValueOnce(
        acceptedTicket({
          marketVersionId: VERSION_2,
          publicationId: PUBLICATION_2,
          acceptedOdds: 1.9,
          ruleVersion: 'elo-v2',
        }),
      );
    publicSnapshot.set(refreshedSnapshot());

    await expect(store.submit('HOME', 10, false)).resolves.toBe('stale');
    expect(publicMarket.fetchMarket).toHaveBeenCalledWith(MARKET_ID);
    expect(store.marketSnapshot()?.marketVersionId).toBe(VERSION_2);
    expect(store.requiresReconfirmation()).toBe(true);
    expect(api.submit).toHaveBeenCalledTimes(1);

    await expect(store.submit('HOME', 10, false)).resolves.toBe('ignored');
    expect(api.submit).toHaveBeenCalledTimes(1);

    await expect(store.submit('HOME', 10, true)).resolves.toBe('success');
    expect(api.submit).toHaveBeenCalledTimes(2);
    expect(api.submit.mock.calls[1][0]).toMatchObject({
      marketVersionId: VERSION_2,
      publicationId: PUBLICATION_2,
      ruleVersion: 'elo-v2',
      selection: 'HOME',
    });
    expect(api.submit.mock.calls[1][1]).not.toBe(api.submit.mock.calls[0][1]);
  });

  it('sai de loading quando o GET de refresh stale falha', async () => {
    const { store, api, publicMarket } = setup();
    await store.load();
    api.submit.mockRejectedValueOnce(
      new HttpErrorResponse({
        status: 409,
        error: { code: 'BETUFU-409-STALE_VERSION' },
      }),
    );
    publicMarket.fetchMarket.mockReturnValueOnce(
      throwError(() => new HttpErrorResponse({ status: 404 })),
    );

    await expect(store.submit('HOME', 10, false)).resolves.toBe('error');

    expect(store.status()).toBe('error');
    expect(store.errorMessage()).toBe('Nao foi possivel atualizar as odds agora. Tente novamente.');
    expect(store.canSubmit()).toBe(false);
  });

  it('rejeita refresh stale que retorna outro mercado sem permitir novo POST', async () => {
    const { store, api, publicSnapshot } = setup();
    await store.load();
    api.submit.mockRejectedValueOnce(
      new HttpErrorResponse({
        status: 409,
        error: { code: 'BETUFU-409-STALE_VERSION' },
      }),
    );
    publicSnapshot.set(refreshedSnapshot({ marketId: '88888888-8888-4888-8888-888888888888' }));

    await expect(store.submit('HOME', 10, false)).resolves.toBe('error');

    expect(store.status()).toBe('error');
    expect(store.requiresReconfirmation()).toBe(false);
    expect(store.marketSnapshot()?.marketVersionId).toBe(VERSION_1);

    await expect(store.submit('HOME', 10, true)).resolves.toBe('ignored');
    expect(api.submit).toHaveBeenCalledTimes(1);
  });

  it('adota mercado suspenso retornado pelo refresh stale sem pedir reconfirmacao', async () => {
    const { store, api, publicSnapshot } = setup();
    await store.load();
    api.submit.mockRejectedValueOnce(
      new HttpErrorResponse({
        status: 409,
        error: { code: 'BETUFU-409-STALE_VERSION' },
      }),
    );
    publicSnapshot.set(
      refreshedSnapshot({
        state: OddsMarketState.Suspended,
        stateLabel: 'Suspenso',
      }),
    );

    await expect(store.submit('HOME', 10, false)).resolves.toBe('error');

    expect(store.status()).toBe('suspended');
    expect(store.requiresReconfirmation()).toBe(false);
    expect(store.canSubmit()).toBe(false);
    expect(api.submit).toHaveBeenCalledTimes(1);
  });

  it('adota o estado indisponivel retornado pelo refresh stale', async () => {
    const { store, api, publicSnapshot } = setup();
    await store.load();
    api.submit.mockRejectedValueOnce(
      new HttpErrorResponse({
        status: 409,
        error: { code: 'BETUFU-409-STALE_VERSION' },
      }),
    );
    publicSnapshot.set(
      refreshedSnapshot({
        state: OddsMarketState.Closed,
        stateLabel: 'Encerrado',
      }),
    );

    await expect(store.submit('HOME', 10, false)).resolves.toBe('error');

    expect(store.status()).toBe('closed');
    expect(store.requiresReconfirmation()).toBe(false);
    expect(store.canSubmit()).toBe(false);
  });

  it('mapeia bloqueio do servidor sem revelar papel ou capability', async () => {
    const { store, api } = setup();
    api.getBalance.mockRejectedValue(new HttpErrorResponse({ status: 403 }));

    await store.load();

    expect(store.status()).toBe('blocked');
    expect(store.errorMessage()).toBeNull();
  });
});
