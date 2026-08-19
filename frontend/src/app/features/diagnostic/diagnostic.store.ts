import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom, retry, timer } from 'rxjs';
import { DiagnosticApiAdapter } from './diagnostic-api.adapter';
import { DiagnosticSnapshotViewModel, DiagnosticState } from './diagnostic.models';

const INITIAL_STATE: DiagnosticState = Object.freeze({
  status: 'initial',
  snapshot: null,
  previousSnapshot: null,
  errorMessage: null,
  errorCorrelationId: null,
  isRetrying: false,
});

@Injectable({
  providedIn: 'root',
})
export class DiagnosticStore {
  private readonly adapter = inject(DiagnosticApiAdapter);

  private readonly state = signal<DiagnosticState>(INITIAL_STATE);

  // Seletores públicos reativos (Signals)
  readonly status = computed(() => this.state().status);
  readonly snapshot = computed(() => this.state().snapshot);
  readonly previousSnapshot = computed(() => this.state().previousSnapshot);
  readonly errorMessage = computed(() => this.state().errorMessage);
  readonly errorCorrelationId = computed(() => this.state().errorCorrelationId);
  readonly isRetrying = computed(() => this.state().isRetrying);

  readonly isLoading = computed(() => this.state().status === 'loading');
  readonly isPopulated = computed(() => this.state().status === 'populated');
  readonly isPartial = computed(() => this.state().status === 'partial');
  readonly isEmpty = computed(() => this.state().status === 'empty');
  readonly isError = computed(() => this.state().status === 'error');

  /**
   * Retorna o snapshot atual ou o snapshot anterior durante recargas (D-04 e D-17).
   */
  readonly displaySnapshot = computed(() => {
    return this.state().snapshot ?? this.state().previousSnapshot;
  });

  /**
   * Consulta o diagnóstico seguro com retry limitado (máx 2, backoff 250/500ms) sem polling cíclico (D-16).
   */
  async loadDiagnostic(): Promise<void> {
    const currentState = this.state();

    // Preserva o snapshot anterior ao iniciar uma nova verificação (garantia 'loading' e 'partial')
    const activeSnapshot = currentState.snapshot ?? currentState.previousSnapshot;

    this.state.set({
      status: 'loading',
      snapshot: activeSnapshot,
      previousSnapshot: activeSnapshot,
      errorMessage: null,
      errorCorrelationId: null,
      isRetrying: false,
    });

    try {
      const adaptedSnapshot: DiagnosticSnapshotViewModel | null = await firstValueFrom(
        this.adapter.fetchDiagnostic().pipe(
          retry({
            count: 2,
            delay: (_error, retryCount) => {
              this.state.update((s) => ({ ...s, isRetrying: true }));
              return timer(retryCount === 1 ? 250 : 500);
            },
          }),
        ),
      );

      if (!adaptedSnapshot) {
        // Garantia 'empty': resposta sem componentes
        this.state.set({
          status: 'empty',
          snapshot: null,
          previousSnapshot: activeSnapshot,
          errorMessage: null,
          errorCorrelationId: null,
          isRetrying: false,
        });
      } else if (adaptedSnapshot.status === 'UP') {
        // Garantia 'populated': todos os componentes saudáveis
        this.state.set({
          status: 'populated',
          snapshot: adaptedSnapshot,
          previousSnapshot: adaptedSnapshot,
          errorMessage: null,
          errorCorrelationId: null,
          isRetrying: false,
        });
      } else {
        // Garantia 'partial': componentes degradados ou indisponíveis
        this.state.set({
          status: 'partial',
          snapshot: adaptedSnapshot,
          previousSnapshot: adaptedSnapshot,
          errorMessage: null,
          errorCorrelationId: null,
          isRetrying: false,
        });
      }
    } catch (error: unknown) {
      // Garantia 'error': falha de transporte ou contrato inválido (fail-closed)
      const sanitized = this.sanitizeError(error);

      this.state.set({
        status: 'error',
        snapshot: null,
        previousSnapshot: activeSnapshot,
        errorMessage: sanitized.message,
        errorCorrelationId: sanitized.correlationId,
        isRetrying: false,
      });
    }
  }

  private sanitizeError(error: unknown): { message: string; correlationId: string } {
    let message = 'Algo não funcionou como esperado. Tente novamente.';
    let correlationId = 'N/D';

    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) {
        message = 'Sua sessão não permite continuar. Entre novamente.';
      } else if (error.status === 403) {
        message = 'Você não tem acesso a este diagnóstico.';
      }

      if (error.error && typeof error.error === 'object') {
        const errorBody = error.error as Record<string, unknown>;
        if (typeof errorBody['correlationId'] === 'string') {
          correlationId = errorBody['correlationId'];
        }
      }
    } else if (error instanceof Error && error.message.startsWith('CONTRATO_')) {
      message = 'Dados de diagnóstico inválidos recebidos da plataforma.';
    }

    return { message, correlationId };
  }
}
