import { Injectable, inject } from '@angular/core';
import { Observable, defer, map } from 'rxjs';
import { OddsPublicService } from '../../../api/generated/api/odds-public.service';
import { OddsMarketState } from '../../../api/generated/model/odds-market-state';
import { PublicOddsExplanation } from '../../../api/generated/model/public-odds-explanation';
import { PublicOddsHistory } from '../../../api/generated/model/public-odds-history';
import { PublicOddsView } from '../../../api/generated/model/public-odds-view';

export interface PublicMarketSelection {
  code: PublicMarketSelectionCode;
  label: string;
  odd: number;
}

export type PublicMarketSelectionCode = 'HOME' | 'DRAW' | 'AWAY';

export interface PublicMarketHistoryEntry {
  publicationId: string;
  odd: number;
  publishedAt: string;
  publishedAtLabel: string;
}

export interface PublicMarketSnapshot {
  marketId: string;
  marketVersionId: string;
  eventId: string;
  marketType: PublicOddsView.MarketTypeEnum;
  state: OddsMarketState;
  stateLabel: string;
  currentPublicationId: string;
  ruleVersion: string;
  closeDeadline: string;
  closeDeadlineLabel: string;
  publishedAt: string;
  publishedAtLabel: string;
  selections: readonly PublicMarketSelection[];
  history: readonly PublicMarketHistoryEntry[];
  explanation: PublicOddsExplanation;
  confidenceLabel: string;
  isLowConfidence: boolean;
}

@Injectable({ providedIn: 'root' })
export class PublicMarketApiAdapter {
  private readonly oddsPublicService = inject(OddsPublicService);

  fetchMarket(marketId: string): Observable<PublicMarketSnapshot> {
    return defer(() => this.oddsPublicService.getPublicOddsMarket({ marketId })).pipe(
      map((response) => this.adapt(response)),
    );
  }

  adapt(response: PublicOddsView | null | undefined): PublicMarketSnapshot {
    if (!response || typeof response !== 'object') {
      throw new Error('CONTRATO_ODDS_PUBLICAS_INVALIDO: resposta ausente.');
    }
    if (
      !this.nonEmpty(response.marketId) ||
      !this.nonEmpty(response.marketVersionId) ||
      !this.nonEmpty(response.eventId) ||
      response.marketType !== PublicOddsView.MarketTypeEnum.HeadToHead ||
      !this.validDate(response.closeDeadline)
    ) {
      throw new Error('CONTRATO_ODDS_PUBLICAS_INVALIDO: mercado publico incompleto.');
    }
    if (!Object.values(OddsMarketState).includes(response.state)) {
      throw new Error('CONTRATO_ODDS_PUBLICAS_INVALIDO: estado publico invalido.');
    }
    this.validateSummary(response.current);
    if (!Array.isArray(response.history)) {
      throw new Error('CONTRATO_ODDS_PUBLICAS_INVALIDO: historico ausente.');
    }
    this.validateExplanation(response.explanation);

    const selectionCodes = this.selectionCodes(response.current.publishedOdds.length);
    const selections = Object.freeze(
      response.current.publishedOdds.map((odd, index) => ({
        code: selectionCodes[index],
        label: this.selectionLabel(selectionCodes[index]),
        odd,
      })),
    );
    const history = Object.freeze(
      response.history
        .map((entry) => this.adaptHistory(entry))
        .sort((left, right) => right.publishedAt.localeCompare(left.publishedAt)),
    );

    return Object.freeze({
      marketId: response.marketId,
      marketVersionId: response.marketVersionId,
      eventId: response.eventId,
      marketType: response.marketType,
      state: response.state,
      stateLabel: this.stateLabel(response.state),
      currentPublicationId: response.current.publicationId,
      ruleVersion: response.explanation.algorithmVersion,
      closeDeadline: response.closeDeadline,
      closeDeadlineLabel: this.dateLabel(response.closeDeadline),
      publishedAt: response.current.publishedAt,
      publishedAtLabel: this.dateLabel(response.current.publishedAt),
      selections,
      history,
      explanation: Object.freeze({ ...response.explanation }),
      confidenceLabel: this.confidenceLabel(response.explanation.confidenceBand),
      isLowConfidence: response.explanation.confidenceBand === 'LOW',
    });
  }

  private validateSummary(summary: PublicOddsView['current']): void {
    if (!summary || !this.nonEmpty(summary.publicationId) || !Array.isArray(summary.publishedOdds)) {
      throw new Error('CONTRATO_ODDS_PUBLICAS_INVALIDO: resumo incompleto.');
    }
    if (
      ![2, 3].includes(summary.publishedOdds.length) ||
      summary.publishedOdds.some((odd) => !Number.isFinite(odd) || odd <= 1) ||
      !this.validDate(summary.publishedAt)
    ) {
      throw new Error('CONTRATO_ODDS_PUBLICAS_INVALIDO: odd publicada invalida.');
    }
  }

  private validateExplanation(explanation: PublicOddsExplanation): void {
    if (
      !explanation ||
      !this.nonEmpty(explanation.algorithmVersion) ||
      !this.nonEmpty(explanation.configurationVersion) ||
      !Number.isFinite(explanation.fairProbability) ||
      explanation.fairProbability < 0 ||
      explanation.fairProbability > 1 ||
      !Number.isFinite(explanation.margin) ||
      explanation.margin < 0 ||
      !Number.isFinite(explanation.confidence) ||
      explanation.confidence < 0 ||
      explanation.confidence > 1 ||
      !Number.isInteger(explanation.sampleSize) ||
      explanation.sampleSize < 0 ||
      explanation.roundingMode !== PublicOddsExplanation.RoundingModeEnum.HalfUp ||
      !Object.values(PublicOddsExplanation.ConfidenceBandEnum).includes(explanation.confidenceBand)
    ) {
      throw new Error('CONTRATO_ODDS_PUBLICAS_INVALIDO: explicacao incompleta.');
    }
  }

  private adaptHistory(entry: PublicOddsHistory): PublicMarketHistoryEntry {
    if (
      !this.nonEmpty(entry.publicationId) ||
      !Number.isFinite(entry.publishedOdds) ||
      entry.publishedOdds <= 1 ||
      !this.validDate(entry.publishedAt)
    ) {
      throw new Error('CONTRATO_ODDS_PUBLICAS_INVALIDO: historico invalido.');
    }
    return Object.freeze({
      publicationId: entry.publicationId,
      odd: entry.publishedOdds,
      publishedAt: entry.publishedAt,
      publishedAtLabel: this.dateLabel(entry.publishedAt),
    });
  }

  private selectionCodes(total: number): readonly PublicMarketSelectionCode[] {
    if (total === 2) return ['HOME', 'AWAY'];
    if (total === 3) return ['HOME', 'DRAW', 'AWAY'];
    throw new Error('CONTRATO_ODDS_PUBLICAS_INVALIDO: vetor de selecoes invalido.');
  }

  private selectionLabel(code: PublicMarketSelectionCode): string {
    return { HOME: 'Mandante', DRAW: 'Empate', AWAY: 'Visitante' }[code];
  }

  private stateLabel(state: OddsMarketState): string {
    return {
      DRAFT: 'Rascunho',
      OPEN: 'Aberto',
      SUSPENDED: 'Suspenso',
      CLOSED: 'Encerrado',
      LOCKED: 'Bloqueado',
      CANCELLED: 'Cancelado',
    }[state];
  }

  private confidenceLabel(band: PublicOddsExplanation.ConfidenceBandEnum): string {
    return { LOW: 'Confianca baixa', MEDIUM: 'Confianca media', HIGH: 'Confianca alta' }[band];
  }

  private dateLabel(value: string): string {
    return new Intl.DateTimeFormat('pt-BR', {
      dateStyle: 'medium',
      timeStyle: 'short',
      timeZone: 'America/Sao_Paulo',
    }).format(new Date(value));
  }

  private validDate(value: string): boolean {
    return this.nonEmpty(value) && !Number.isNaN(new Date(value).getTime());
  }

  private nonEmpty(value: unknown): value is string {
    return typeof value === 'string' && value.trim().length > 0;
  }
}
