import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom, retry, timer } from 'rxjs';
import { EnrollmentApiAdapter } from './enrollment-api.adapter';
import { EnrollmentState, EnrollmentViewModel } from './enrollment.models';

const INITIAL_STATE: EnrollmentState = Object.freeze({
  status: 'initial',
  enrollment: null,
  previousEnrollment: null,
  errorMessage: null,
  errorCorrelationId: null,
  isSaving: false,
  isRetrying: false,
});

@Injectable({
  providedIn: 'root',
})
export class EnrollmentStore {
  private readonly adapter = inject(EnrollmentApiAdapter);

  private readonly state = signal<EnrollmentState>(INITIAL_STATE);

  // Seletores reativos públicos (Signals)
  readonly status = computed(() => this.state().status);
  readonly enrollment = computed(() => this.state().enrollment);
  readonly previousEnrollment = computed(() => this.state().previousEnrollment);
  readonly errorMessage = computed(() => this.state().errorMessage);
  readonly errorCorrelationId = computed(() => this.state().errorCorrelationId);
  readonly isSaving = computed(() => this.state().isSaving);
  readonly isRetrying = computed(() => this.state().isRetrying);

  readonly isLoading = computed(() => this.state().status === 'loading');
  readonly isPopulated = computed(() => this.state().status === 'populated');
  readonly isEmpty = computed(() => this.state().status === 'empty');
  readonly isError = computed(() => this.state().status === 'error');

  /**
   * Retorna o vínculo de matrícula atual ou o anterior durante recargas (D-04 e D-17).
   */
  readonly displayEnrollment = computed(() => {
    return this.state().enrollment ?? this.state().previousEnrollment;
  });

  /**
   * Indica se a matrícula está no estado ativo/registrado no sistema.
   */
  readonly isRegistered = computed(() => {
    const active = this.displayEnrollment();
    return active !== null && (active.status === 'ATIVO' || active.status === 'REGISTRADA');
  });

  /**
   * Consulta o estado da matrícula própria no BFF com retry limitado na leitura (máx 2, backoff 250/500ms) (D-16).
   */
  async loadEnrollment(): Promise<void> {
    const currentState = this.state();
    const activeEnrollment = currentState.enrollment ?? currentState.previousEnrollment;

    this.state.set({
      status: 'loading',
      enrollment: activeEnrollment,
      previousEnrollment: activeEnrollment,
      errorMessage: null,
      errorCorrelationId: null,
      isSaving: false,
      isRetrying: false,
    });

    try {
      const adapted = await firstValueFrom(
        this.adapter.fetchEnrollment().pipe(
          retry({
            count: 2,
            delay: (_error, retryCount) => {
              this.state.update((s) => ({ ...s, isRetrying: true }));
              return timer(retryCount === 1 ? 250 : 500);
            },
          }),
        ),
      );

      if (!adapted || adapted.status === 'NAO_REGISTRADA') {
        // Garantia 'empty': resposta nula ou NAO_REGISTRADA
        this.state.set({
          status: 'empty',
          enrollment: adapted,
          previousEnrollment: activeEnrollment,
          errorMessage: null,
          errorCorrelationId: null,
          isSaving: false,
          isRetrying: false,
        });
      } else {
        // Garantia 'populated': matrícula registrada com sucesso
        this.state.set({
          status: 'populated',
          enrollment: adapted,
          previousEnrollment: adapted,
          errorMessage: null,
          errorCorrelationId: null,
          isSaving: false,
          isRetrying: false,
        });
      }
    } catch (error: unknown) {
      if (error instanceof HttpErrorResponse && error.status === 404) {
        this.state.set({
          status: 'empty',
          enrollment: null,
          previousEnrollment: activeEnrollment,
          errorMessage: null,
          errorCorrelationId: null,
          isSaving: false,
          isRetrying: false,
        });
        return;
      }

      const sanitized = this.sanitizeLoadError(error);
      this.state.set({
        status: 'error',
        enrollment: null,
        previousEnrollment: activeEnrollment,
        errorMessage: sanitized.message,
        errorCorrelationId: sanitized.correlationId,
        isSaving: false,
        isRetrying: false,
      });
    }
  }

  /**
   * Registra a matrícula no BFF. Nenhuma retentativa automática em mutação (02-UI-SPEC.md §Comportamento de formulário).
   */
  async registerMatricula(matricula: string): Promise<boolean> {
    const currentState = this.state();
    const activeEnrollment = currentState.enrollment ?? currentState.previousEnrollment;

    this.state.set({
      ...currentState,
      isSaving: true,
      errorMessage: null,
      errorCorrelationId: null,
    });

    try {
      const adapted = await firstValueFrom(this.adapter.registerEnrollment(matricula));

      this.state.set({
        status: 'populated',
        enrollment: adapted,
        previousEnrollment: adapted,
        errorMessage: null,
        errorCorrelationId: null,
        isSaving: false,
        isRetrying: false,
      });
      return true;
    } catch (error: unknown) {
      const sanitized = this.sanitizeRegisterError(error);

      this.state.set({
        status: 'error',
        enrollment: null,
        previousEnrollment: activeEnrollment,
        errorMessage: sanitized.message,
        errorCorrelationId: sanitized.correlationId,
        isSaving: false,
        isRetrying: false,
      });
      return false;
    }
  }

  private sanitizeLoadError(error: unknown): { message: string; correlationId: string } {
    let correlationId = 'N/D';

    if (error instanceof HttpErrorResponse) {
      if (error.error && typeof error.error === 'object') {
        const errorBody = error.error as Record<string, unknown>;
        if (typeof errorBody['correlationId'] === 'string' && errorBody['correlationId'].trim() !== '') {
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
      return { message: 'Dados de matrícula inválidos recebidos da plataforma.', correlationId };
    }

    const message =
      correlationId !== 'N/D'
        ? `Algo não funcionou como esperado. Tente novamente. Código de referência: ${correlationId}.`
        : 'Algo não funcionou como esperado. Tente novamente.';

    return { message, correlationId };
  }

  private sanitizeRegisterError(error: unknown): { message: string; correlationId: string } {
    let correlationId = 'N/D';
    let code: string | null = null;

    if (error instanceof HttpErrorResponse) {
      if (error.error && typeof error.error === 'object') {
        const errorBody = error.error as Record<string, unknown>;
        if (typeof errorBody['correlationId'] === 'string' && errorBody['correlationId'].trim() !== '') {
          correlationId = errorBody['correlationId'].trim();
        }
        if (typeof errorBody['code'] === 'string' && errorBody['code'].trim() !== '') {
          code = errorBody['code'].trim();
        }
      }

      if (error.status === 401) {
        return { message: 'Sua sessão expirou. Entre novamente para continuar.', correlationId };
      }
      if (error.status === 403) {
        return { message: 'Você não tem acesso a esta área.', correlationId };
      }

      if (code === 'BETUFU-409-MATRICULA_DUPLICADA') {
        const message =
          correlationId !== 'N/D'
            ? `Esta matrícula já está registrada em outra conta BETUFU. Se ela é sua, fale com um administrador geral para pedir a correção. Código de referência: ${correlationId}.`
            : 'Esta matrícula já está registrada em outra conta BETUFU. Se ela é sua, fale com um administrador geral para pedir a correção.';
        return { message, correlationId };
      }

      if (code === 'BETUFU-409-CONTA_JA_VINCULADA') {
        const message =
          correlationId !== 'N/D'
            ? `Sua conta já tem uma matrícula registrada. Só um administrador geral pode alterá-la. Código de referência: ${correlationId}.`
            : 'Sua conta já tem uma matrícula registrada. Só um administrador geral pode alterá-la.';
        return { message, correlationId };
      }

      if (code === 'BETUFU-429-TOO_MANY_ATTEMPTS' || error.status === 429) {
        const message =
          correlationId !== 'N/D'
            ? `Muitas tentativas de registro. Aguarde alguns minutos e tente novamente. Código de referência: ${correlationId}.`
            : 'Muitas tentativas de registro. Aguarde alguns minutos e tente novamente.';
        return { message, correlationId };
      }
    } else if (error instanceof Error && error.message.startsWith('CONTRATO_')) {
      return { message: 'Dados de matrícula inválidos recebidos da plataforma.', correlationId };
    }

    const message =
      correlationId !== 'N/D'
        ? `Não foi possível registrar a matrícula. Verifique a conexão e tente novamente. Nada foi alterado na sua conta. Código de referência: ${correlationId}.`
        : 'Não foi possível registrar a matrícula. Verifique a conexão e tente novamente. Nada foi alterado na sua conta.';

    return { message, correlationId };
  }
}
