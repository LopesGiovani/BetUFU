import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { OddsMarketState } from '../../../api/generated/model/odds-market-state';
import { SessionStore } from '../../../core/session/session.store';
import { EnrollmentStore } from '../../enrollment/enrollment.store';
import { PublicMarketApiAdapter, PublicMarketSelection } from './public-market-api.adapter';
import {
  BettingBalance,
  BettingSelection,
  BettingTicket,
  PublicMarketBettingApiAdapter,
} from './public-market-betting-api.adapter';

export type BettingState =
  | 'visitor'
  | 'no-enrollment'
  | 'loading'
  | 'eligible'
  | 'insufficient'
  | 'blocked'
  | 'suspended'
  | 'closed'
  | 'stale'
  | 'success'
  | 'error';

export interface BettingMarketSnapshot {
  marketVersionId: string;
  publicationId: string;
  ruleVersion: string;
  closeDeadline: string;
  state: OddsMarketState;
  selections: readonly PublicMarketSelection[];
}

export type BettingSubmitOutcome = 'success' | 'stale' | 'ignored' | 'error';

@Injectable({ providedIn: 'root' })
export class PublicMarketBettingStore {
  private readonly api = inject(PublicMarketBettingApiAdapter);
  private readonly session = inject(SessionStore);
  private readonly enrollment = inject(EnrollmentStore);
  private readonly publicMarket = inject(PublicMarketApiAdapter);
  private readonly state = signal<BettingState>('visitor');
  private readonly balance = signal<BettingBalance | null>(null);
  private readonly ticket = signal<BettingTicket | null>(null);
  private readonly message = signal<string | null>(null);
  private readonly market = signal<BettingMarketSnapshot | null>(null);
  private marketId = '';
  private requestSequence = 0;
  private activeLoadMarketId: string | null = null;
  private loadedMarketId: string | null = null;

  readonly status = computed(() => this.state());
  readonly balanceValue = computed(() => this.balance());
  readonly ownTicket = computed(() => this.ticket());
  readonly errorMessage = computed(() => this.message());
  readonly marketSnapshot = computed(() => this.market());
  readonly selections = computed(() => this.market()?.selections ?? []);
  readonly canSubmit = computed(
    () => ['eligible', 'stale'].includes(this.state()) && this.balance() !== null,
  );
  readonly requiresReconfirmation = computed(() => this.state() === 'stale');

  configure(marketId: string, snapshot: BettingMarketSnapshot): void {
    const changedMarket = this.marketId !== '' && this.marketId !== marketId;
    this.marketId = marketId;
    this.market.set(Object.freeze({ ...snapshot, selections: Object.freeze([...snapshot.selections]) }));
    if (changedMarket) {
      this.balance.set(null);
      this.ticket.set(null);
      this.message.set(null);
      this.state.set('loading');
    }
  }

  setVisitor(): void {
    this.state.set('visitor');
  }

  setNoEnrollment(): void {
    this.state.set('no-enrollment');
  }

  setBlocked(): void {
    this.state.set('blocked');
  }

  shouldLoad(marketId: string): boolean {
    return this.activeLoadMarketId !== marketId && this.loadedMarketId !== marketId;
  }

  async load(): Promise<void> {
    const requestId = ++this.requestSequence;
    const loadingMarketId = this.marketId;
    this.activeLoadMarketId = loadingMarketId;
    this.state.set('loading');
    this.message.set(null);
    try {
      await this.session.loadSession();
      if (requestId !== this.requestSequence) return;
      if (!this.session.isAuthenticated()) {
        this.state.set('visitor');
        return;
      }

      await this.enrollment.loadEnrollment();
      if (requestId !== this.requestSequence) return;
      if (!this.enrollment.isRegistered()) {
        this.state.set(this.enrollment.isError() ? 'error' : 'no-enrollment');
        this.message.set(
          this.enrollment.isError() ? 'Nao foi possivel confirmar seu vinculo agora.' : null,
        );
        return;
      }

      const [balance, ticket] = await Promise.all([
        this.api.getBalance(),
        this.api.getOwnTicket(this.marketId),
      ]);
      if (requestId !== this.requestSequence) return;
      this.balance.set(balance);
      this.ticket.set(ticket);
      this.state.set(ticket ? 'success' : this.availableState(balance));
    } catch (error: unknown) {
      if (requestId !== this.requestSequence) return;
      this.applyError(error, 'Nao foi possivel carregar os creditos agora.');
    } finally {
      if (requestId === this.requestSequence) {
        this.activeLoadMarketId = null;
        this.loadedMarketId = loadingMarketId;
      }
    }
  }

  async submit(
    selection: BettingSelection,
    stake: number,
    confirmed: boolean,
  ): Promise<BettingSubmitOutcome> {
    const snapshot = this.market();
    if (
      !snapshot ||
      !this.canSubmit() ||
      (this.requiresReconfirmation() && !confirmed) ||
      !this.validStake(stake) ||
      !snapshot.selections.some((item) => item.code === selection)
    ) {
      return 'ignored';
    }

    this.state.set('loading');
    const key = globalThis.crypto?.randomUUID?.() ?? `${Date.now()}-${Math.random()}`;
    try {
      const accepted = await this.api.submit(
        {
          marketId: this.marketId,
          marketVersionId: snapshot.marketVersionId,
          publicationId: snapshot.publicationId,
          ruleVersion: snapshot.ruleVersion,
          selection,
          stake,
        },
        key,
      );
      this.ticket.set(accepted);
      this.state.set('success');
      this.message.set(null);
      return 'success';
    } catch (error: unknown) {
      if (this.isStaleConflict(error)) {
        let refreshed: boolean;
        try {
          refreshed = await this.refreshPublicSnapshot();
        } catch (refreshError: unknown) {
          this.applyError(
            refreshError,
            'Nao foi possivel atualizar as odds agora. Tente novamente.',
          );
          return 'error';
        }
        if (!refreshed) {
          this.state.set('error');
          this.message.set('Nao foi possivel atualizar as odds agora. Tente novamente.');
          return 'error';
        }
        const balance = this.balance();
        if (!balance) {
          this.state.set('error');
          this.message.set('Nao foi possivel atualizar as odds agora. Tente novamente.');
          return 'error';
        }
        const refreshedState = this.availableState(balance);
        if (refreshedState !== 'eligible') {
          this.state.set(refreshedState);
          this.message.set(null);
          return 'error';
        }
        this.state.set('stale');
        this.message.set('A odd mudou antes do envio. Confira e confirme novamente.');
        return 'stale';
      }
      this.applyError(error, this.submissionErrorMessage(error));
      return 'error';
    }
  }

  private availableState(balance: BettingBalance): BettingState {
    const market = this.market();
    if (market?.state === OddsMarketState.Suspended) return 'suspended';
    if (market && market.state !== OddsMarketState.Open) return 'closed';
    return balance.balance < balance.minimumStake ? 'insufficient' : 'eligible';
  }

  private validStake(stake: number): boolean {
    const currentBalance = this.balance();
    if (!currentBalance || !Number.isFinite(stake)) return false;
    return (
      Number(stake.toFixed(2)) === stake &&
      stake >= currentBalance.minimumStake &&
      stake <= currentBalance.balance
    );
  }

  private isStaleConflict(error: unknown): boolean {
    if (!(error instanceof HttpErrorResponse) || error.status !== 409) return false;
    const code = this.problemCode(error);
    return code === null || code === 'BETUFU-409-STALE_VERSION';
  }

  private problemCode(error: HttpErrorResponse): string | null {
    if (!error.error || typeof error.error !== 'object') return null;
    const code = (error.error as Record<string, unknown>)['code'];
    return typeof code === 'string' ? code : null;
  }

  private async refreshPublicSnapshot(): Promise<boolean> {
    const refreshed = await firstValueFrom(this.publicMarket.fetchMarket(this.marketId));
    if (!refreshed || refreshed.marketId !== this.marketId) return false;
    this.market.set(
      Object.freeze({
        marketVersionId: refreshed.marketVersionId,
        publicationId: refreshed.currentPublicationId,
        ruleVersion: refreshed.ruleVersion,
        closeDeadline: refreshed.closeDeadline,
        state: refreshed.state,
        selections: Object.freeze([...refreshed.selections]),
      }),
    );
    return true;
  }

  private applyError(error: unknown, fallback: string): void {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      this.state.set('visitor');
      this.message.set('Entre novamente para continuar.');
      return;
    }
    if (error instanceof HttpErrorResponse && error.status === 403) {
      this.state.set('blocked');
      this.message.set(null);
      return;
    }
    this.state.set('error');
    this.message.set(fallback);
  }

  private submissionErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 409) {
      return 'Nao foi possivel confirmar este palpite. Atualize a leitura e tente novamente.';
    }
    return 'Nao foi possivel enviar o palpite agora. Nenhuma alteracao foi confirmada.';
  }
}
