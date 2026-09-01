import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom, retry, timer } from 'rxjs';
import { OperationsApiAdapter } from './operations-api.adapter';
import { OperationsPanelSnapshotViewModel, OperationsPanelState } from './operations.models';

const INITIAL_STATE: OperationsPanelState = Object.freeze({
  status: 'initial',
  snapshot: null,
  previousSnapshot: null,
  errorMessage: null,
  errorCorrelationId: null,
  isRetrying: false,
  isStale: false,
});

@Injectable({
  providedIn: 'root',
})
export class OperationsPanelStore {
  private readonly adapter = inject(OperationsApiAdapter);

  private readonly state = signal<OperationsPanelState>(INITIAL_STATE);

  // Seletores reativos públicos (Signals)
  readonly status = computed(() => this.state().status);
  readonly snapshot = computed(() => this.state().snapshot);
  readonly previousSnapshot = computed(() => this.state().previousSnapshot);
  readonly errorMessage = computed(() => this.state().errorMessage);
  readonly errorCorrelationId = computed(() => this.state().errorCorrelationId);
  readonly isRetrying = computed(() => this.state().isRetrying);
  readonly isStale = computed(() => this.state().isStale);

  readonly isLoading = computed(() => this.state().status === 'loading');
  readonly isPopulated = computed(() => this.state().status === 'populated');
  readonly isPartial = computed(() => this.state().status === 'partial');
  readonly isEmpty = computed(() => this.state().status === 'empty');
  readonly isError = computed(() => this.state().status === 'error');

  /**
   * Retorna o snapshot atual ou o anterior durante recargas para preservação resiliente de dados (D-04, D-17).
   */
  readonly displaySnapshot = computed(() => {
    return this.state().snapshot ?? this.state().previousSnapshot;
  });

  /**
   * Indica se a resposta obtida não possui nenhum bloco autorizado no escopo do papel (ex: Moderador).
   */
  readonly hasNoScope = computed(() => {
    const s = this.displaySnapshot();
    return s !== null && !s.hasAnyBlock;
  });

  /**
   * Indica se os blocos autorizados não possuem nenhuma pendência aberta e nenhum bloco falhou.
   */
  readonly hasNoPending = computed(() => {
    const s = this.displaySnapshot();
    return s !== null && s.hasAnyBlock && !s.hasAnyPending && s.unavailableBlocks.length === 0;
  });

  /**
   * Consulta o painel operacional com retry automático limitado (máximo 2 tentativas, backoff 250/500ms) sem polling (OPER-06, D-16).
   * 401 e 403 NÃO realizam retry e limpam todo snapshot anterior antes do estado de erro.
   */
  async loadPanel(): Promise<void> {
    const currentState = this.state();
    const activeSnapshot = currentState.snapshot ?? currentState.previousSnapshot;

    this.state.set({
      status: 'loading',
      snapshot: activeSnapshot,
      previousSnapshot: activeSnapshot,
      errorMessage: null,
      errorCorrelationId: null,
      isRetrying: false,
      isStale: false,
    });

    try {
      const adaptedSnapshot: OperationsPanelSnapshotViewModel | null = await firstValueFrom(
        this.adapter.fetchOperationalPanel().pipe(
          retry({
            count: 2,
            delay: (error: unknown, retryCount) => {
              if (
                error instanceof HttpErrorResponse &&
                (error.status === 401 || error.status === 403)
              ) {
                throw error;
              }
              this.state.update((s) => ({ ...s, isRetrying: true }));
              return timer(retryCount === 1 ? 250 : 500);
            },
          }),
        ),
      );

      if (!adaptedSnapshot) {
        // Garantia 'empty': resposta nula
        this.state.set({
          status: 'empty',
          snapshot: null,
          previousSnapshot: activeSnapshot,
          errorMessage: null,
          errorCorrelationId: null,
          isRetrying: false,
          isStale: false,
        });
      } else if (!adaptedSnapshot.hasAnyBlock) {
        // Garantia 'empty': escopo sem blocos autorizados (ex. Moderador)
        this.state.set({
          status: 'empty',
          snapshot: adaptedSnapshot,
          previousSnapshot: adaptedSnapshot,
          errorMessage: null,
          errorCorrelationId: null,
          isRetrying: false,
          isStale: false,
        });
      } else if (adaptedSnapshot.unavailableBlocks.length > 0) {
        // Garantia 'partial': ao menos um bloco autorizado falhou isoladamente
        this.state.set({
          status: 'partial',
          snapshot: adaptedSnapshot,
          previousSnapshot: adaptedSnapshot,
          errorMessage: null,
          errorCorrelationId: null,
          isRetrying: false,
          isStale: false,
        });
      } else {
        // Garantia 'populated': blocos operacionais carregados com sucesso
        this.state.set({
          status: 'populated',
          snapshot: adaptedSnapshot,
          previousSnapshot: adaptedSnapshot,
          errorMessage: null,
          errorCorrelationId: null,
          isRetrying: false,
          isStale: false,
        });
      }
    } catch (error: unknown) {
      if (error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403)) {
        // Garantia 401/403: limpa snapshot atual e anterior antes do estado de erro (zero cartões no DOM)
        const sanitized = this.sanitizeError(error, false);
        this.state.set({
          status: 'error',
          snapshot: null,
          previousSnapshot: null,
          errorMessage: sanitized.message,
          errorCorrelationId: sanitized.correlationId,
          isRetrying: false,
          isStale: false,
        });
      } else {
        // Garantia falhas transitórias: preservam dados anteriores rotulados
        const hasPreviousData = activeSnapshot !== null;
        const sanitized = this.sanitizeError(error, hasPreviousData);
        this.state.set({
          status: 'error',
          snapshot: null,
          previousSnapshot: activeSnapshot,
          errorMessage: sanitized.message,
          errorCorrelationId: sanitized.correlationId,
          isRetrying: false,
          isStale: hasPreviousData,
        });
      }
    }
  }

  private sanitizeError(
    error: unknown,
    hasPreviousData: boolean,
  ): { message: string; correlationId: string } {
    let correlationId = 'N/D';

    if (error instanceof HttpErrorResponse) {
      if (error.error && typeof error.error === 'object') {
        const errorBody = error.error as Record<string, unknown>;
        if (
          typeof errorBody['correlationId'] === 'string' &&
          errorBody['correlationId'].trim() !== ''
        ) {
          correlationId = errorBody['correlationId'].trim();
        }
      }

      if (error.status === 401) {
        return { message: 'Sua sessão expirou. Entre novamente para continuar.', correlationId };
      }
      if (error.status === 403) {
        return { message: 'Você não tem acesso a esta área.', correlationId };
      }
    } else if (error instanceof Error && error.message.startsWith('CONTRATO_')) {
      return {
        message: 'Dados de painel operacional inválidos recebidos da plataforma.',
        correlationId,
      };
    }

    let message = hasPreviousData
      ? 'Não foi possível atualizar o painel. Verifique a conexão e tente novamente.'
      : 'Não foi possível carregar o painel. Tente novamente.';

    if (correlationId !== 'N/D') {
      message = `${message} Código de referência: ${correlationId}.`;
    }

    return { message, correlationId };
  }
}
