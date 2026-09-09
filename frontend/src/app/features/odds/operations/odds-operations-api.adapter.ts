import { Injectable, inject } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Observable, defer, map } from 'rxjs';
import { OddsMarketService } from '../../../api/generated/api/odds-market.service';
import { OddsOverrideService } from '../../../api/generated/api/odds-override.service';
import { OddsMarketLifecycleRequest } from '../../../api/generated/model/odds-market-lifecycle-request';
import { OddsMarketState } from '../../../api/generated/model/odds-market-state';
import { OddsMarketView } from '../../../api/generated/model/odds-market-view';
import { OddsOverrideProposalRequest } from '../../../api/generated/model/odds-override-proposal-request';
import { OddsOverrideApprovalRequest } from '../../../api/generated/model/odds-override-approval-request';

export interface OddsOperationsSnapshot {
  readonly marketId: string;
  readonly eventId: string;
  readonly state: OddsMarketState;
  readonly stateLabel: string;
  readonly currentVersionId: string;
  readonly versionNo: number;
  readonly lockVersion: number;
  readonly closeDeadline: string | null;
  readonly updatedAt: string;
  readonly isReadOnly: boolean;
}

export interface OddsOverrideSnapshot {
  readonly overrideId: string;
  readonly marketId: string;
  readonly calculationId: string;
  readonly decision: string;
  readonly previousOdds: number;
  readonly proposedOdds: number;
  readonly delta: number;
  readonly createdAt: string;
}

export type OddsLifecycleAction = OddsMarketLifecycleRequest.ActionEnum;

@Injectable({ providedIn: 'root' })
export class OddsOperationsApiAdapter {
  private readonly marketService = inject(OddsMarketService);
  private readonly overrideService = inject(OddsOverrideService);

  fetchMarket(marketId: string): Observable<OddsOperationsSnapshot> {
    return defer(() => this.marketService.getOddsMarket({ marketId })).pipe(
      map((response) => this.adaptMarket(response)),
    );
  }

  transition(
    marketId: string,
    action: OddsLifecycleAction,
    expectedLockVersion: number,
    reason?: string,
  ): Observable<OddsOperationsSnapshot> {
    const request: OddsMarketLifecycleRequest = { action, expectedLockVersion, reason };
    return defer(() =>
      this.marketService.transitionOddsMarketLifecycle({
        marketId,
        expectedLockVersion,
        oddsMarketLifecycleRequest: request,
      }),
    ).pipe(map((response) => this.adaptMarket(response)));
  }

  proposeOverride(
    marketId: string,
    request: OddsOverrideProposalRequest,
    idempotencyKey: string,
  ): Observable<OddsOverrideSnapshot> {
    return defer(() =>
      this.overrideService.proposeOddsOverride({
        marketId,
        idempotencyKey,
        oddsOverrideProposalRequest: request,
      }),
    ).pipe(map((response) => this.adaptOverride(response)));
  }

  approveOverride(
    marketId: string,
    proposalId: string,
    request: OddsOverrideApprovalRequest,
    idempotencyKey: string,
  ): Observable<OddsOverrideSnapshot> {
    return defer(() =>
      this.overrideService.approveOddsOverride({
        marketId,
        proposalId,
        idempotencyKey,
        oddsOverrideApprovalRequest: request,
      }),
    ).pipe(map((response) => this.adaptOverride(response)));
  }

  adaptMarket(response: OddsMarketView | null | undefined): OddsOperationsSnapshot {
    if (!response || !response.marketId || !response.eventId || !response.currentVersionId) {
      throw new Error('CONTRATO_ODDS_MARKET_INVALID');
    }
    if (!Object.values(OddsMarketState).includes(response.state)) {
      throw new Error('CONTRATO_ODDS_MARKET_STATE_INVALID');
    }
    return Object.freeze({
      marketId: response.marketId,
      eventId: response.eventId,
      state: response.state,
      stateLabel: this.stateLabel(response.state),
      currentVersionId: response.currentVersionId,
      versionNo: response.versionNo,
      lockVersion: response.lockVersion,
      closeDeadline: response.closeDeadline ?? null,
      updatedAt: response.updatedAt,
      isReadOnly:
        response.state === OddsMarketState.Closed || response.state === OddsMarketState.Locked,
    });
  }

  adaptOverride(
    response:
      | {
          overrideId?: string;
          marketId?: string;
          calculationId?: string;
          decision?: string;
          previousOdds?: number;
          proposedOdds?: number;
          delta?: number;
          createdAt?: string;
        }
      | null
      | undefined,
  ): OddsOverrideSnapshot {
    if (
      !response?.overrideId ||
      !response.marketId ||
      !response.calculationId ||
      !response.decision
    ) {
      throw new Error('CONTRATO_ODDS_OVERRIDE_INVALID');
    }
    const previousOdds = response.previousOdds;
    const proposedOdds = response.proposedOdds;
    const delta = response.delta;
    if (
      typeof previousOdds !== 'number' ||
      !Number.isFinite(previousOdds) ||
      typeof proposedOdds !== 'number' ||
      !Number.isFinite(proposedOdds) ||
      typeof delta !== 'number' ||
      !Number.isFinite(delta)
    )
      throw new Error('CONTRATO_ODDS_OVERRIDE_INVALID');
    return Object.freeze({
      overrideId: response.overrideId,
      marketId: response.marketId,
      calculationId: response.calculationId,
      decision: response.decision,
      previousOdds,
      proposedOdds,
      delta,
      createdAt: response.createdAt ?? '',
    });
  }

  errorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.status === 401)
        return 'Sessao necessaria. Sua sessao expirou antes desta operacao. Nenhuma alteracao foi aplicada.';
      if (error.status === 403)
        return 'Operacao nao autorizada. Sua sessao nao permite operar este mercado. Nenhuma alteracao foi aplicada.';
      if (error.status === 409)
        return 'Versao concorrente detectada. Compare a versao local com a atual antes de continuar.';
    }
    if (error instanceof Error && error.message.startsWith('CONTRATO_')) {
      return 'Resposta do servidor invalida para esta operacao.';
    }
    return 'Servico indisponivel. Nao foi possivel consultar o servidor agora.';
  }

  private stateLabel(state: OddsMarketState): string {
    return {
      DRAFT: 'Rascunho',
      OPEN: 'Aberto',
      SUSPENDED: 'Suspenso',
      CLOSED: 'CLOSED',
      LOCKED: 'LOCKED',
      CANCELLED: 'Cancelado',
    }[state];
  }
}
