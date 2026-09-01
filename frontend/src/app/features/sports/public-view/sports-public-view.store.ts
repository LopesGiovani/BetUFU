import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom, retry, timer } from 'rxjs';
import {
  PublicDetail,
  PublicDetailKind,
  SportsPublicViewApiAdapter,
} from './sports-public-view-api.adapter';

export type SportsPublicViewStatus =
  'initial' | 'loading' | 'populated' | 'empty' | 'not-found' | 'error';

interface SportsPublicViewState {
  status: SportsPublicViewStatus;
  kind: PublicDetailKind | null;
  id: string | null;
  data: PublicDetail | null;
  errorMessage: string | null;
  errorCorrelationId: string | null;
  isRetrying: boolean;
}

const INITIAL_STATE: SportsPublicViewState = Object.freeze({
  status: 'initial',
  kind: null,
  id: null,
  data: null,
  errorMessage: null,
  errorCorrelationId: null,
  isRetrying: false,
});

@Injectable({ providedIn: 'root' })
export class SportsPublicViewStore {
  private readonly adapter = inject(SportsPublicViewApiAdapter);
  private readonly state = signal<SportsPublicViewState>(INITIAL_STATE);
  private requestSequence = 0;

  readonly status = computed(() => this.state().status);
  readonly kind = computed(() => this.state().kind);
  readonly id = computed(() => this.state().id);
  readonly data = computed(() => this.state().data);
  readonly errorMessage = computed(() => this.state().errorMessage);
  readonly errorCorrelationId = computed(() => this.state().errorCorrelationId);
  readonly isRetrying = computed(() => this.state().isRetrying);
  readonly isLoading = computed(() => this.status() === 'loading');
  readonly isPopulated = computed(() => this.status() === 'populated');
  readonly isEmpty = computed(() => this.status() === 'empty');
  readonly isNotFound = computed(() => this.status() === 'not-found');
  readonly isError = computed(() => this.status() === 'error');

  async load(kind: PublicDetailKind, id: string): Promise<void> {
    const normalizedId = id.trim();
    const requestId = ++this.requestSequence;
    this.state.set({
      status: 'loading',
      kind,
      id: normalizedId || null,
      data: null,
      errorMessage: null,
      errorCorrelationId: null,
      isRetrying: false,
    });

    try {
      const data = await firstValueFrom(
        this.adapter.fetch(kind, normalizedId).pipe(
          retry({
            count: 2,
            delay: (error: unknown, retryCount) => {
              if (error instanceof HttpErrorResponse && error.status === 404) {
                throw error;
              }
              if (requestId === this.requestSequence) {
                this.state.update((state) => ({ ...state, isRetrying: true }));
              }
              return timer(retryCount === 1 ? 250 : 500);
            },
          }),
        ),
      );
      if (requestId !== this.requestSequence) {
        return;
      }
      this.state.set({
        status: data ? 'populated' : 'empty',
        kind,
        id: normalizedId,
        data: data ?? null,
        errorMessage: null,
        errorCorrelationId: null,
        isRetrying: false,
      });
    } catch (error: unknown) {
      if (requestId !== this.requestSequence) {
        return;
      }
      const correlationId = this.correlationId(error);
      if (error instanceof HttpErrorResponse && error.status === 404) {
        this.state.set({
          status: 'not-found',
          kind,
          id: normalizedId,
          data: null,
          errorMessage: 'Este conteúdo público não está disponível.',
          errorCorrelationId: correlationId,
          isRetrying: false,
        });
        return;
      }
      this.state.set({
        status: 'error',
        kind,
        id: normalizedId,
        data: null,
        errorMessage:
          error instanceof Error && error.message.startsWith('CONTRATO_')
            ? 'Os dados públicos recebidos não correspondem ao contrato esperado.'
            : 'Não foi possível carregar este conteúdo público. Tente novamente.',
        errorCorrelationId: correlationId,
        isRetrying: false,
      });
    }
  }

  async retry(): Promise<void> {
    const current = this.state();
    if (current.kind && current.id) {
      await this.load(current.kind, current.id);
    }
  }

  private correlationId(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.error && typeof error.error === 'object') {
      const value = (error.error as Record<string, unknown>)['correlationId'];
      if (typeof value === 'string' && value.trim()) {
        return value.trim();
      }
    }
    return 'N/D';
  }
}
