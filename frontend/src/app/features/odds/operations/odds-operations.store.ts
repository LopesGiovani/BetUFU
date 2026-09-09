import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { OddsMarketLifecycleRequest } from '../../../api/generated/model/odds-market-lifecycle-request';
import { OddsOverrideProposalRequest } from '../../../api/generated/model/odds-override-proposal-request';
import { OddsOverrideApprovalRequest } from '../../../api/generated/model/odds-override-approval-request';
import {
  OddsOperationsApiAdapter,
  OddsOperationsSnapshot,
  OddsOverrideSnapshot,
} from './odds-operations-api.adapter';

type Status = 'initial' | 'loading' | 'populated' | 'empty' | 'error' | 'stale';
interface State {
  status: Status;
  marketId: string | null;
  snapshot: OddsOperationsSnapshot | null;
  proposal: OddsOverrideSnapshot | null;
  errorMessage: string | null;
  feedbackMessage: string | null;
  isMutating: boolean;
}

const INITIAL: State = {
  status: 'initial',
  marketId: null,
  snapshot: null,
  proposal: null,
  errorMessage: null,
  feedbackMessage: null,
  isMutating: false,
};

@Injectable({ providedIn: 'root' })
export class OddsOperationsStore {
  private readonly adapter = inject(OddsOperationsApiAdapter);
  private readonly state = signal<State>(INITIAL);
  private sequence = 0;

  readonly status = computed(() => this.state().status);
  readonly marketId = computed(() => this.state().marketId);
  readonly snapshot = computed(() => this.state().snapshot);
  readonly proposal = computed(() => this.state().proposal);
  readonly errorMessage = computed(() => this.state().errorMessage);
  readonly feedbackMessage = computed(() => this.state().feedbackMessage);
  readonly isMutating = computed(() => this.state().isMutating);
  readonly isLoading = computed(() => this.status() === 'loading');
  readonly isEmpty = computed(() => this.status() === 'empty');
  readonly isError = computed(() => this.status() === 'error');
  readonly isStale = computed(() => this.status() === 'stale');

  async loadMarket(marketId: string): Promise<void> {
    const id = marketId.trim();
    const request = ++this.sequence;
    if (!id) {
      this.state.set({
        ...INITIAL,
        status: 'empty',
        marketId: null,
        errorMessage: 'Mercado nao encontrado.',
      });
      return;
    }
    const previous = this.state().snapshot;
    this.state.set({
      ...this.state(),
      status: 'loading',
      marketId: id,
      errorMessage: null,
      feedbackMessage: null,
    });
    try {
      const snapshot = await firstValueFrom(this.adapter.fetchMarket(id));
      if (request !== this.sequence) return;
      this.state.set({
        ...this.state(),
        status: 'populated',
        marketId: id,
        snapshot,
        errorMessage: null,
      });
    } catch (error: unknown) {
      if (request !== this.sequence) return;
      this.state.set({
        ...this.state(),
        status: previous ? 'stale' : 'error',
        snapshot: previous,
        errorMessage: this.adapter.errorMessage(error),
      });
    }
  }

  async transition(action: OddsMarketLifecycleRequest.ActionEnum, reason?: string): Promise<void> {
    const snapshot = this.snapshot();
    if (!snapshot || snapshot.isReadOnly || this.isMutating()) return;
    this.state.update((state) => ({
      ...state,
      isMutating: true,
      errorMessage: null,
      feedbackMessage: null,
    }));
    try {
      const next = await firstValueFrom(
        this.adapter.transition(snapshot.marketId, action, snapshot.lockVersion, reason),
      );
      this.state.update((state) => ({
        ...state,
        status: 'populated',
        snapshot: next,
        isMutating: false,
        feedbackMessage:
          action === 'SUSPEND_MARKET'
            ? 'Mercado suspenso para verificacao. A versao anterior permanece registrada.'
            : 'Lifecycle atualizado.',
      }));
    } catch (error: unknown) {
      this.state.update((state) => ({
        ...state,
        isMutating: false,
        errorMessage: this.adapter.errorMessage(error),
      }));
    }
  }

  async propose(request: OddsOverrideProposalRequest, idempotencyKey: string): Promise<void> {
    const snapshot = this.snapshot();
    if (!snapshot || snapshot.state !== 'SUSPENDED' || this.isMutating()) return;
    this.state.update((state) => ({
      ...state,
      isMutating: true,
      errorMessage: null,
      feedbackMessage: null,
    }));
    try {
      const proposal = await firstValueFrom(
        this.adapter.proposeOverride(snapshot.marketId, request, idempotencyKey),
      );
      this.state.update((state) => ({
        ...state,
        proposal,
        isMutating: false,
        feedbackMessage:
          'Aguardando segundo administrador do mesmo escopo. A pessoa que propos nao pode aprovar.',
      }));
    } catch (error: unknown) {
      this.state.update((state) => ({
        ...state,
        isMutating: false,
        errorMessage: this.adapter.errorMessage(error),
      }));
    }
  }

  async approve(request: OddsOverrideApprovalRequest, idempotencyKey: string): Promise<void> {
    const snapshot = this.snapshot();
    const proposal = this.proposal();
    if (!snapshot || !proposal || this.isMutating()) return;
    this.state.update((state) => ({
      ...state,
      isMutating: true,
      errorMessage: null,
      feedbackMessage: null,
    }));
    try {
      const approved = await firstValueFrom(
        this.adapter.approveOverride(
          snapshot.marketId,
          proposal.overrideId,
          request,
          idempotencyKey,
        ),
      );
      this.state.update((state) => ({
        ...state,
        proposal: approved,
        isMutating: false,
        feedbackMessage: 'Override aprovado. A nova versao imutavel foi publicada.',
      }));
    } catch (error: unknown) {
      this.state.update((state) => ({
        ...state,
        isMutating: false,
        errorMessage: this.adapter.errorMessage(error),
      }));
    }
  }
}
