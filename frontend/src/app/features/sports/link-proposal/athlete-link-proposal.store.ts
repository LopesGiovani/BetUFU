import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom, retry, timer } from 'rxjs';
import { AthleteLinkDecisionRequest } from '../../../api/generated/model/athlete-link-decision-request';
import { AthleteLinkProposal } from '../../../api/generated/model/athlete-link-proposal';
import { AthleteLinkProposalApiAdapter } from './athlete-link-proposal-api.adapter';

export type AthleteLinkProposalStatus = 'initial' | 'loading' | 'empty' | 'ready' | 'error';

interface ProposalState {
  status: AthleteLinkProposalStatus;
  proposal: AthleteLinkProposal | null;
  errorMessage: string | null;
  feedbackMessage: string | null;
  isMutating: boolean;
  isRetrying: boolean;
}

const INITIAL_STATE: ProposalState = Object.freeze({
  status: 'initial',
  proposal: null,
  errorMessage: null,
  feedbackMessage: null,
  isMutating: false,
  isRetrying: false,
});

@Injectable({ providedIn: 'root' })
export class AthleteLinkProposalStore {
  private readonly adapter = inject(AthleteLinkProposalApiAdapter);
  private readonly state = signal<ProposalState>(INITIAL_STATE);
  private requestSequence = 0;

  readonly status = computed(() => this.state().status);
  readonly proposal = computed(() => this.state().proposal);
  readonly errorMessage = computed(() => this.state().errorMessage);
  readonly feedbackMessage = computed(() => this.state().feedbackMessage);
  readonly isMutating = computed(() => this.state().isMutating);
  readonly isRetrying = computed(() => this.state().isRetrying);
  readonly isLoading = computed(() => this.status() === 'loading');
  readonly isEmpty = computed(() => this.status() === 'empty');
  readonly isReady = computed(() => this.status() === 'ready');
  readonly isError = computed(() => this.status() === 'error');

  async load(): Promise<void> {
    const requestId = ++this.requestSequence;
    this.state.update((current) => ({
      ...current,
      status: 'loading',
      errorMessage: null,
      feedbackMessage: null,
      isRetrying: false,
    }));

    try {
      const proposal = await firstValueFrom(this.adapter.fetchProposal().pipe(
        retry({
          count: 2,
          delay: (error: unknown, retryCount) => {
            if (this.adapter.isUnauthorized(error) || this.adapter.isNotFound(error)) throw error;
            this.state.update((current) => ({ ...current, isRetrying: true }));
            return timer(retryCount === 1 ? 250 : 500);
          },
        }),
      ));
      if (requestId !== this.requestSequence) return;
      this.state.set({
        status: 'ready',
        proposal,
        errorMessage: null,
        feedbackMessage: null,
        isMutating: false,
        isRetrying: false,
      });
    } catch (error: unknown) {
      if (requestId !== this.requestSequence) return;
      if (this.adapter.isNotFound(error)) {
        this.state.set({ ...INITIAL_STATE, status: 'empty' });
        return;
      }
      this.state.set({
        ...INITIAL_STATE,
        status: 'error',
        errorMessage: this.sanitizeError(error),
      });
    }
  }

  async accept(): Promise<boolean> {
    return this.decide(AthleteLinkDecisionRequest.DecisionEnum.Accept, 'Vínculo com atleta aceito.');
  }

  async reject(): Promise<boolean> {
    return this.decide(AthleteLinkDecisionRequest.DecisionEnum.Reject, 'Proposta de vínculo rejeitada.');
  }

  async contest(): Promise<boolean> {
    return this.decide(AthleteLinkDecisionRequest.DecisionEnum.Contest, 'Contestação enviada para revisão.');
  }

  reset(): void {
    this.requestSequence += 1;
    this.state.set(INITIAL_STATE);
  }

  private async decide(
    decision: AthleteLinkDecisionRequest.DecisionEnum,
    successMessage: string,
  ): Promise<boolean> {
    const proposal = this.proposal();
    if (!proposal || this.isMutating()) return false;

    this.state.update((current) => ({
      ...current,
      isMutating: true,
      errorMessage: null,
      feedbackMessage: null,
    }));
    try {
      const updated = await firstValueFrom(this.adapter.decide(proposal.proposalId, decision));
      this.state.update((current) => ({
        ...current,
        status: 'ready',
        proposal: updated,
        feedbackMessage: successMessage,
        isMutating: false,
      }));
      return true;
    } catch (error: unknown) {
      if (this.adapter.isUnauthorized(error)) {
        this.state.set({
          ...INITIAL_STATE,
          status: 'error',
          errorMessage: this.sanitizeError(error),
        });
      } else {
        this.state.update((current) => ({
          ...current,
          isMutating: false,
          errorMessage: this.adapter.isConflict(error)
            ? 'Este registro foi alterado por outra pessoa. Recarregue para comparar a versão atual antes de tentar novamente.'
            : this.sanitizeError(error),
        }));
      }
      return false;
    }
  }

  private sanitizeError(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401) {
      return 'Sua sessão expirou. Entre novamente para continuar.';
    }
    if (error instanceof HttpErrorResponse && error.status === 403) {
      return 'Você não tem acesso a esta área.';
    }
    if (error instanceof Error && error.message.startsWith('CONTRATO_')) {
      return 'Dados de vínculo inválidos recebidos da plataforma.';
    }
    return 'Não foi possível concluir o vínculo agora. Tente novamente.';
  }
}
