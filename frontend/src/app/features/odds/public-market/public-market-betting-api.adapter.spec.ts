import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { describe, expect, it, vi } from 'vitest';
import { CreditLedgerService } from '../../../api/generated/api/credit-ledger.service';
import { PredictionService } from '../../../api/generated/api/prediction.service';
import { PredictionSubmitRequest } from '../../../api/generated/model/prediction-submit-request';
import { PredictionTicketResponse } from '../../../api/generated/model/prediction-ticket-response';
import { PublicMarketBettingApiAdapter } from './public-market-betting-api.adapter';

const MARKET_ID = '11111111-1111-4111-8111-111111111111';
const MARKET_VERSION_ID = '22222222-2222-4222-8222-222222222222';
const PUBLICATION_ID = '33333333-3333-4333-8333-333333333333';

function ticket(): PredictionTicketResponse {
  return {
    predictionId: '44444444-4444-4444-8444-444444444444',
    marketId: MARKET_ID,
    marketVersionId: MARKET_VERSION_ID,
    publicationId: PUBLICATION_ID,
    selection: PredictionTicketResponse.SelectionEnum.Home,
    stake: 10,
    acceptedOdds: 1.82,
    potentialReturn: 18.2,
    netProfit: 8.2,
    ruleVersion: 'elo-davidson-v2',
    closeDeadline: '2026-09-15T12:00:00Z',
    state: 'PENDING',
    acceptedAt: '2026-09-14T12:01:00Z',
  };
}

describe('PublicMarketBettingApiAdapter', () => {
  function setup() {
    const ledger = { getMyCreditBalance: vi.fn() };
    const prediction = {
      getMyPredictionForMarket: vi.fn(),
      submitMyPrediction: vi.fn(),
    };
    TestBed.configureTestingModule({
      providers: [
        PublicMarketBettingApiAdapter,
        { provide: CreditLedgerService, useValue: ledger },
        { provide: PredictionService, useValue: prediction },
      ],
    });
    return {
      adapter: TestBed.inject(PublicMarketBettingApiAdapter),
      ledger,
      prediction,
    };
  }

  it('usa os servicos OpenAPI gerados para saldo e ticket proprio', async () => {
    const { adapter, ledger, prediction } = setup();
    ledger.getMyCreditBalance.mockReturnValue(
      of({ balance: 1000, scale: 2, minimumStake: 10, rulesVersion: 'credits-v1' }),
    );
    prediction.getMyPredictionForMarket.mockReturnValue(of(ticket()));

    await expect(adapter.getBalance()).resolves.toEqual({
      balance: 1000,
      scale: 2,
      minimumStake: 10,
      rulesVersion: 'credits-v1',
    });
    await expect(adapter.getOwnTicket(MARKET_ID)).resolves.toEqual(ticket());
    expect(prediction.getMyPredictionForMarket).toHaveBeenCalledWith({ marketId: MARKET_ID });
  });

  it('trata somente o 404 do GET proprio como ausencia de ticket', async () => {
    const { adapter, prediction } = setup();
    prediction.getMyPredictionForMarket.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404 })),
    );

    await expect(adapter.getOwnTicket(MARKET_ID)).resolves.toBeNull();

    prediction.getMyPredictionForMarket.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 403 })),
    );
    await expect(adapter.getOwnTicket(MARKET_ID)).rejects.toMatchObject({ status: 403 });
  });

  it('entrega body e Idempotency-Key ao metodo gerado sem HttpClient manual', async () => {
    const { adapter, prediction } = setup();
    prediction.submitMyPrediction.mockReturnValue(of(ticket()));
    const request = {
      marketId: MARKET_ID,
      marketVersionId: MARKET_VERSION_ID,
      publicationId: PUBLICATION_ID,
      ruleVersion: 'elo-davidson-v2',
      selection: PredictionSubmitRequest.SelectionEnum.Home,
      stake: 10,
    };

    await expect(adapter.submit(request, 'idem-opaque')).resolves.toEqual(ticket());
    expect(prediction.submitMyPrediction).toHaveBeenCalledWith({
      idempotencyKey: 'idem-opaque',
      predictionSubmitRequest: request,
    });
  });

  it('falha fechado para saldo ou ticket fora do contrato', async () => {
    const { adapter, ledger, prediction } = setup();
    ledger.getMyCreditBalance.mockReturnValue(
      of({ balance: -1, scale: 2, minimumStake: 10, rulesVersion: 'credits-v1' }),
    );
    prediction.getMyPredictionForMarket.mockReturnValue(of({ ...ticket(), selection: 'OTHER' }));

    await expect(adapter.getBalance()).rejects.toThrow('CONTRATO_CREDITOS_INVALIDO');
    await expect(adapter.getOwnTicket(MARKET_ID)).rejects.toThrow('CONTRATO_PALPITE_INVALIDO');
  });
});
