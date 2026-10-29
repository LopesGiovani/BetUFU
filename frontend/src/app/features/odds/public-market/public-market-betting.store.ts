import { HttpErrorResponse } from '@angular/common/http';
import { DestroyRef, Injectable, computed, effect, inject, signal, untracked } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { OddsMarketState } from '../../../api/generated/model/odds-market-state';
import { SessionStore } from '../../../core/session/session.store';
import { AppClock } from '../../../core/time/app-clock';
import { EnrollmentStore } from '../../enrollment/enrollment.store';
import type { PauseAvailability } from '../../operations/emergency-pause-api.adapter';
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
  private readonly clock = inject(AppClock);
  private readonly destroyRef = inject(DestroyRef);
  private readonly sessionEpoch = signal(this.session.revision());
  private sessionAuthenticated = this.session.isAuthenticated();
  private readonly currentSession = computed(
    () => this.session.isAuthenticated() && this.session.revision() === this.sessionEpoch(),
  );
  private readonly state = signal<BettingState>('visitor');
  private readonly balance = signal<BettingBalance | null>(null);
  private readonly ticket = signal<BettingTicket | null>(null);
  private readonly message = signal<string | null>(null);
  private readonly market = signal<BettingMarketSnapshot | null>(null);
  readonly availability = signal<PauseAvailability>('unknown');
  private marketId = '';
  private requestSequence = 0;
  private activeLoadMarketId: string | null = null;
  private loadedMarketId: string | null = null;
  private cancelDeadlineTransition: (() => void) | null = null;

  readonly status = computed(() => this.currentSession() ? this.state() : (this.marketUnavailableState() ?? 'visitor'));
  readonly balanceValue = computed(() => this.currentSession() ? this.balance() : null);
  readonly ownTicket = computed(() => this.currentSession() ? this.ticket() : null);
  readonly errorMessage = computed(() => this.currentSession() ? this.message() : null);
  readonly marketSnapshot = computed(() => this.market());
  readonly selections = computed(() => this.market()?.selections ?? []);
  readonly canSubmit = computed(
    () => this.currentSession() && this.availability() === 'available' && ['eligible', 'stale'].includes(this.state()) && this.balance() !== null,
  );
  readonly requiresReconfirmation = computed(() => this.state() === 'stale');

  constructor() {
    this.destroyRef.onDestroy(() => this.cancelDeadlineTransition?.());
    effect(() => {
      const revision = this.session.revision();
      const authenticated = this.session.isAuthenticated();
      untracked(() => {
        if (revision === this.sessionEpoch() && authenticated === this.sessionAuthenticated) return;
        this.clearPrivateState();
        this.sessionEpoch.set(revision);
        this.sessionAuthenticated = authenticated;
        this.availability.set('unknown');
        if (authenticated && this.marketId) void this.load();
      });
    });
  }

  setAvailability(value: PauseAvailability): void {
    this.availability.set(value);
  }

  configure(marketId: string, snapshot: BettingMarketSnapshot): void {
    const changedMarket = this.marketId !== '' && this.marketId !== marketId;
    this.marketId = marketId;
    this.market.set(
      Object.freeze({ ...snapshot, selections: Object.freeze([...snapshot.selections]) }),
    );
    this.scheduleDeadline();
    if (changedMarket) {
      this.clearPrivateState();
      this.state.set('loading');
    }
    const unavailable = this.marketUnavailableState();
    if (!this.ticket() && unavailable) this.state.set(unavailable);
  }

  setVisitor(): void {
    this.state.set(this.marketUnavailableState() ?? 'visitor');
  }

  setNoEnrollment(): void {
    this.state.set(this.marketUnavailableState() ?? 'no-enrollment');
  }

  setBlocked(): void {
    this.state.set(this.marketUnavailableState() ?? 'blocked');
  }

  shouldLoad(marketId: string): boolean {
    return this.activeLoadMarketId !== marketId && this.loadedMarketId !== marketId;
  }

  async load(): Promise<void> {
    if (this.session.isChecking()) return;
    const requestId = ++this.requestSequence;
    const loadingMarketId = this.marketId;
    this.activeLoadMarketId = loadingMarketId;
    this.state.set(this.marketUnavailableState() ?? 'loading');
    this.message.set(null);
    try {
      if (!this.session.isAuthenticated()) {
        this.setVisitor();
        return;
      }
      await this.loadAuthenticatedState(requestId);
    } catch (error: unknown) {
      if (!this.isCurrentRequest(requestId)) return;
      const unavailable = this.marketUnavailableState();
      if (unavailable) {
        this.state.set(unavailable);
        this.message.set(null);
        return;
      }
      this.applyError(error, 'Nao foi possivel carregar os creditos agora.');
    } finally {
      if (this.isCurrentRequest(requestId)) {
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
    if (this.preventUnavailableSubmission()) return 'ignored';
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
    const requestId = ++this.requestSequence;
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
      if (!this.isCurrentRequest(requestId)) return 'ignored';
      this.ticket.set(accepted);
      this.state.set('success');
      this.message.set(null);
      return 'success';
    } catch (error: unknown) {
      if (!this.isCurrentRequest(requestId)) return 'ignored';
      if (this.isStaleConflict(error)) {
        let refreshed: boolean;
        try {
          refreshed = await this.refreshPublicSnapshot(requestId);
        } catch (refreshError: unknown) {
          if (!this.isCurrentRequest(requestId)) return 'ignored';
          this.applyError(
            refreshError,
            'Nao foi possivel atualizar as odds agora. Tente novamente.',
          );
          return 'error';
        }
        if (!this.isCurrentRequest(requestId)) return 'ignored';
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
    const unavailable = this.marketUnavailableState();
    if (unavailable) return unavailable;
    return balance.balance < balance.minimumStake ? 'insufficient' : 'eligible';
  }

  private marketUnavailableState(): 'suspended' | 'closed' | null {
    const market = this.market();
    if (!market) return null;
    if (market.state === OddsMarketState.Suspended) return 'suspended';
    if (market.state !== OddsMarketState.Open) return 'closed';
    const deadline = Date.parse(market.closeDeadline);
    return !Number.isFinite(deadline) || deadline <= this.clock.now() ? 'closed' : null;
  }

  private scheduleDeadline(): void {
    this.cancelDeadlineTransition?.();
    this.cancelDeadlineTransition = null;
    const market = this.market();
    if (!market || market.state !== OddsMarketState.Open) return;
    const deadline = Date.parse(market.closeDeadline);
    if (!Number.isFinite(deadline) || deadline <= this.clock.now()) return;
    const scheduledMarketId = this.marketId;
    const scheduledDeadline = market.closeDeadline;
    this.cancelDeadlineTransition = this.clock.scheduleAt(deadline, () => {
      const current = this.market();
      if (
        this.marketId !== scheduledMarketId ||
        current?.closeDeadline !== scheduledDeadline ||
        this.ticket()
      ) {
        return;
      }
      this.state.set('closed');
      this.message.set(null);
    });
  }

  private async loadOwnTicketForUnavailableMarket(requestId: number): Promise<void> {
    const ticket = await this.api.getOwnTicket(this.marketId);
    if (!this.isCurrentRequest(requestId)) return;
    this.ticket.set(ticket);
    this.balance.set(null);
    this.message.set(null);
    this.state.set(ticket ? 'success' : (this.marketUnavailableState() ?? 'closed'));
  }

  private async loadAuthenticatedState(requestId: number): Promise<void> {
    if (this.marketUnavailableState()) {
      await this.loadOwnTicketForUnavailableMarket(requestId);
      return;
    }

    await this.enrollment.loadEnrollment();
    if (!this.isCurrentRequest(requestId)) return;
    if (this.marketUnavailableState()) {
      await this.loadOwnTicketForUnavailableMarket(requestId);
      return;
    }
    if (!this.enrollment.isRegistered()) {
      if (this.enrollment.isError()) this.state.set('error');
      else this.setNoEnrollment();
      this.message.set(
        this.enrollment.isError() ? 'Nao foi possivel confirmar seu vinculo agora.' : null,
      );
      return;
    }

    const [balance, ticket] = await Promise.all([
      this.api.getBalance(),
      this.api.getOwnTicket(this.marketId),
    ]);
    if (!this.isCurrentRequest(requestId)) return;
    this.balance.set(balance);
    this.ticket.set(ticket);
    this.state.set(ticket ? 'success' : this.availableState(balance));
  }

  private preventUnavailableSubmission(): boolean {
    const unavailable = this.marketUnavailableState();
    if (!unavailable || this.ticket()) return false;
    this.state.set(unavailable);
    this.message.set(null);
    return true;
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

  private async refreshPublicSnapshot(requestId: number): Promise<boolean> {
    const refreshed = await firstValueFrom(this.publicMarket.fetchMarket(this.marketId));
    if (!this.isCurrentRequest(requestId)) return false;
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
    this.scheduleDeadline();
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

  private isCurrentRequest(requestId: number): boolean {
    return requestId === this.requestSequence && this.currentSession();
  }

  private clearPrivateState(): void {
    this.requestSequence++;
    this.activeLoadMarketId = null;
    this.loadedMarketId = null;
    this.balance.set(null);
    this.ticket.set(null);
    this.message.set(null);
    this.state.set(this.marketUnavailableState() ?? 'visitor');
  }

  private submissionErrorMessage(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 409) {
      return 'Nao foi possivel confirmar este palpite. Atualize a leitura e tente novamente.';
    }
    return 'Nao foi possivel enviar o palpite agora. Nenhuma alteracao foi confirmada.';
  }
}
