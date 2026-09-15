import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { CreditLedgerService } from '../../../api/generated/api/credit-ledger.service';
import { PredictionService } from '../../../api/generated/api/prediction.service';
import { CreditBalanceResponse } from '../../../api/generated/model/credit-balance-response';
import { PredictionSubmitRequest } from '../../../api/generated/model/prediction-submit-request';
import { PredictionTicketResponse } from '../../../api/generated/model/prediction-ticket-response';

export type BettingSelection = PredictionSubmitRequest.SelectionEnum;

export interface BettingBalance {
  balance: number;
  scale: number;
  minimumStake: number;
  rulesVersion: string;
}

export interface BettingTicket {
  predictionId: string;
  marketId: string;
  marketVersionId: string;
  publicationId: string;
  selection: BettingSelection;
  stake: number;
  acceptedOdds: number;
  potentialReturn: number;
  netProfit: number;
  ruleVersion: string;
  closeDeadline: string;
  state: string;
  acceptedAt: string;
}

export interface BettingSubmission {
  marketId: string;
  marketVersionId: string;
  publicationId: string;
  ruleVersion: string;
  selection: BettingSelection;
  stake: number;
}

@Injectable({ providedIn: 'root' })
export class PublicMarketBettingApiAdapter {
  private readonly creditLedgerService = inject(CreditLedgerService);
  private readonly predictionService = inject(PredictionService);

  async getBalance(): Promise<BettingBalance> {
    return this.adaptBalance(await firstValueFrom(this.creditLedgerService.getMyCreditBalance()));
  }

  async getOwnTicket(marketId: string): Promise<BettingTicket | null> {
    try {
      const response = await firstValueFrom(
        this.predictionService.getMyPredictionForMarket({ marketId }),
      );
      return this.adaptTicket(response);
    } catch (error: unknown) {
      if (error instanceof HttpErrorResponse && error.status === 404) return null;
      throw error;
    }
  }

  async submit(request: BettingSubmission, idempotencyKey: string): Promise<BettingTicket> {
    const response = await firstValueFrom(
      this.predictionService.submitMyPrediction({
        idempotencyKey,
        predictionSubmitRequest: request,
      }),
    );
    return this.adaptTicket(response);
  }

  private adaptBalance(response: CreditBalanceResponse): BettingBalance {
    if (
      !response ||
      !Number.isFinite(response.balance) ||
      response.balance < 0 ||
      response.scale !== 2 ||
      !Number.isFinite(response.minimumStake) ||
      response.minimumStake <= 0 ||
      !this.nonEmpty(response.rulesVersion)
    ) {
      throw new Error('CONTRATO_CREDITOS_INVALIDO');
    }
    return Object.freeze({ ...response });
  }

  private adaptTicket(response: PredictionTicketResponse): BettingTicket {
    if (
      !response ||
      !this.nonEmpty(response.predictionId) ||
      !this.nonEmpty(response.marketId) ||
      !this.nonEmpty(response.marketVersionId) ||
      !this.nonEmpty(response.publicationId) ||
      !Object.values(PredictionTicketResponse.SelectionEnum).includes(response.selection) ||
      !Number.isFinite(response.stake) ||
      response.stake <= 0 ||
      !Number.isFinite(response.acceptedOdds) ||
      response.acceptedOdds <= 1 ||
      !Number.isFinite(response.potentialReturn) ||
      !Number.isFinite(response.netProfit) ||
      !this.nonEmpty(response.ruleVersion) ||
      !this.validDate(response.closeDeadline) ||
      !this.nonEmpty(response.state) ||
      !this.validDate(response.acceptedAt)
    ) {
      throw new Error('CONTRATO_PALPITE_INVALIDO');
    }
    return Object.freeze({ ...response });
  }

  private validDate(value: string): boolean {
    return this.nonEmpty(value) && !Number.isNaN(new Date(value).getTime());
  }

  private nonEmpty(value: unknown): value is string {
    return typeof value === 'string' && value.trim().length > 0;
  }
}
