import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom, retry, timer } from 'rxjs';
import {
  EnrollmentAdminApiAdapter,
  ResolvedTargetAccount,
} from './enrollment-admin-api.adapter';

export type EnrollmentAdminStatus =
  | 'initial'
  | 'resolving'
  | 'empty'
  | 'resolved'
  | 'submitting'
  | 'success'
  | 'error';

export interface EnrollmentAdminFieldErrors {
  readonly targetIdentityId: string | null;
  readonly matricula: string | null;
  readonly reason: string | null;
}

export interface EnrollmentAdminState {
  readonly status: EnrollmentAdminStatus;
  readonly targetIdentityId: string;
  readonly resolvedTarget: ResolvedTargetAccount | null;
  readonly replacementEnrollment: string;
  readonly reason: string;
  readonly feedbackMessage: string | null;
  readonly errorMessage: string | null;
  readonly errorCorrelationId: string | null;
  readonly fieldErrors: EnrollmentAdminFieldErrors;
  readonly isResolving: boolean;
  readonly isSubmitting: boolean;
}

const UUID_REGEX = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

const INITIAL_FIELD_ERRORS: EnrollmentAdminFieldErrors = Object.freeze({
  targetIdentityId: null,
  matricula: null,
  reason: null,
});

const INITIAL_STATE: EnrollmentAdminState = Object.freeze({
  status: 'initial',
  targetIdentityId: '',
  resolvedTarget: null,
  replacementEnrollment: '',
  reason: '',
  feedbackMessage: null,
  errorMessage: null,
  errorCorrelationId: null,
  fieldErrors: INITIAL_FIELD_ERRORS,
  isResolving: false,
  isSubmitting: false,
});

@Injectable({
  providedIn: 'root',
})
export class EnrollmentAdminStore {
  private readonly adapter = inject(EnrollmentAdminApiAdapter);

  private readonly state = signal<EnrollmentAdminState>(INITIAL_STATE);
  private lifecycleGeneration = 0;

  // Seletores reativos públicos (Signals)
  readonly status = computed(() => this.state().status);
  readonly targetIdentityId = computed(() => this.state().targetIdentityId);
  readonly resolvedTarget = computed(() => this.state().resolvedTarget);
  readonly targetNickname = computed(() => this.state().resolvedTarget?.nickname ?? null);
  readonly replacementEnrollment = computed(() => this.state().replacementEnrollment);
  readonly reason = computed(() => this.state().reason);
  readonly feedbackMessage = computed(() => this.state().feedbackMessage);
  readonly errorMessage = computed(() => this.state().errorMessage);
  readonly errorCorrelationId = computed(() => this.state().errorCorrelationId);
  readonly fieldErrors = computed(() => this.state().fieldErrors);
  readonly isResolving = computed(() => this.state().isResolving);
  readonly isSubmitting = computed(() => this.state().isSubmitting);

  readonly isInitial = computed(() => this.state().status === 'initial');
  readonly isEmpty = computed(() => this.state().status === 'empty');
  readonly isResolved = computed(() => this.state().resolvedTarget !== null);
  readonly isSuccess = computed(() => this.state().status === 'success');
  readonly isError = computed(() => this.state().status === 'error');

  /**
   * Resolve o identificador público da conta informada manualmente pelo operador.
   * Consulta de leitura admite repetição automática (máx 2 retentativas, backoff 250/500ms).
   */
  async resolveTargetAccount(targetIdentityId: string): Promise<boolean> {
    const trimmedId = (targetIdentityId || '').trim();

    if (!trimmedId || !UUID_REGEX.test(trimmedId)) {
      this.state.update((s) => ({
        ...s,
        targetIdentityId: trimmedId,
        fieldErrors: {
          ...s.fieldErrors,
          targetIdentityId: 'Este identificador não tem o formato esperado. Copie-o do perfil da pessoa.',
        },
        errorMessage: null,
        errorCorrelationId: null,
        feedbackMessage: null,
      }));
      return false;
    }

    const generation = this.lifecycleGeneration;

    this.state.update((s) => ({
      ...s,
      status: 'resolving',
      targetIdentityId: trimmedId,
      isResolving: true,
      fieldErrors: {
        ...s.fieldErrors,
        targetIdentityId: null,
      },
      errorMessage: null,
      errorCorrelationId: null,
      feedbackMessage: null,
    }));

    try {
      const adapted = await firstValueFrom(
        this.adapter.resolveTarget(trimmedId).pipe(
          retry({
            count: 2,
            delay: (error: unknown, retryCount) => {
              if (
                error instanceof HttpErrorResponse &&
                (error.status === 401 || error.status === 403 || error.status === 404)
              ) {
                throw error;
              }
              return timer(retryCount === 1 ? 250 : 500);
            },
          }),
        ),
      );

      if (generation !== this.lifecycleGeneration) {
        return false;
      }

      if (!adapted) {
        this.state.update((s) => ({
          ...s,
          status: 'empty',
          resolvedTarget: null,
          isResolving: false,
          errorMessage: null,
          errorCorrelationId: null,
        }));
        return false;
      }

      this.state.update((s) => ({
        ...s,
        status: 'resolved',
        resolvedTarget: adapted,
        isResolving: false,
        errorMessage: null,
        errorCorrelationId: null,
      }));
      return true;
    } catch (error: unknown) {
      if (generation !== this.lifecycleGeneration) {
        return false;
      }

      if (error instanceof HttpErrorResponse && error.status === 404) {
        // 404 tratado como target-not-located (estado empty conforme UI-SPEC §307)
        this.state.update((s) => ({
          ...s,
          status: 'empty',
          resolvedTarget: null,
          isResolving: false,
          errorMessage: null,
          errorCorrelationId: null,
        }));
        return false;
      }

      const sanitized = this.sanitizeResolveError(error);
      this.state.update((s) => ({
        ...s,
        status: 'error',
        resolvedTarget: null,
        isResolving: false,
        errorMessage: sanitized.message,
        errorCorrelationId: sanitized.correlationId,
      }));
      return false;
    }
  }

  /**
   * Executa a mutação de correção de matrícula autorizada.
   * Nenhuma mutação repete automaticamente (D-16, UI-SPEC §Comportamento de formulário).
   * Impede duplo envio concorrente.
   */
  async correctEnrollment(
    targetIdentityId: string,
    matricula: string,
    reason: string,
  ): Promise<boolean> {
    const current = this.state();
    if (current.isSubmitting) {
      return false;
    }

    const generation = this.lifecycleGeneration;

    if (!current.resolvedTarget || current.resolvedTarget.targetIdentityId !== targetIdentityId) {
      this.state.update((s) => ({
        ...s,
        resolvedTarget: null,
        status: s.status === 'resolved' ? 'initial' : s.status,
        fieldErrors: {
          ...s.fieldErrors,
          targetIdentityId: 'Identificador divergente ou conta não localizada.',
        },
      }));
      return false;
    }

    const trimmedMatricula = (matricula || '').trim();
    const trimmedReason = (reason || '').trim();

    let matriculaError: string | null = null;
    if (!trimmedMatricula) {
      matriculaError = 'Informe o número da matrícula.';
    } else {
      const normalizedMatricula = trimmedMatricula.toUpperCase().replace(/[\s\-./]+/g, '');
      if (!/^[A-Z0-9]{5,20}$/.test(normalizedMatricula)) {
        matriculaError = 'Este número não corresponde ao formato de matrícula da UFU. Confira e digite novamente.';
      }
    }

    let reasonError: string | null = null;
    if (!trimmedReason) {
      reasonError = 'Informe o motivo da correção.';
    } else if (trimmedReason.length < 10) {
      reasonError = 'Descreva o motivo com mais detalhe. Ele precisa fazer sentido para quem ler a auditoria depois.';
    }

    if (matriculaError || reasonError) {
      this.state.update((s) => ({
        ...s,
        replacementEnrollment: matricula,
        reason,
        fieldErrors: {
          ...s.fieldErrors,
          matricula: matriculaError,
          reason: reasonError,
        },
      }));
      return false;
    }

    this.state.update((s) => ({
      ...s,
      status: 'submitting',
      isSubmitting: true,
      replacementEnrollment: matricula,
      reason,
      fieldErrors: {
        ...s.fieldErrors,
        matricula: null,
        reason: null,
      },
      errorMessage: null,
      errorCorrelationId: null,
      feedbackMessage: null,
    }));

    try {
      await firstValueFrom(
        this.adapter.correctEnrollment(targetIdentityId, matricula, reason),
      );

      if (generation !== this.lifecycleGeneration) {
        return false;
      }

      this.state.update((s) => ({
        ...s,
        status: 'success',
        isSubmitting: false,
        replacementEnrollment: '',
        reason: '',
        feedbackMessage: 'Matrícula corrigida. O motivo foi registrado na auditoria.',
        errorMessage: null,
        errorCorrelationId: null,
      }));
      return true;
    } catch (error: unknown) {
      if (generation !== this.lifecycleGeneration) {
        return false;
      }

      const sanitized = this.sanitizeCorrectionError(error);
      const invalidatesResolvedTarget =
        error instanceof HttpErrorResponse && (error.status === 403 || error.status === 404);

      let fieldErrors = { ...this.state().fieldErrors };
      if (error instanceof HttpErrorResponse && error.error && typeof error.error === 'object') {
        const errorBody = error.error as Record<string, unknown>;
        if (Array.isArray(errorBody['fieldErrors'])) {
          for (const violation of errorBody['fieldErrors'] as Array<{ field?: string; message?: string }>) {
            if (violation.field === 'matricula') {
              fieldErrors.matricula = violation.message ?? 'Matrícula inválida.';
            } else if (violation.field === 'reason') {
              fieldErrors.reason = violation.message ?? 'Motivo inválido.';
            } else if (violation.field === 'targetIdentityId') {
              fieldErrors.targetIdentityId = violation.message ?? 'Identificador inválido.';
            }
          }
        }
      }

      this.state.update((s) => ({
        ...s,
        status: 'error',
        isSubmitting: false,
        resolvedTarget: invalidatesResolvedTarget ? null : s.resolvedTarget,
        // D-16, D-32: o valor digitado sobrevive na memória do componente/store após recusa
        replacementEnrollment: matricula,
        reason,
        fieldErrors,
        errorMessage: sanitized.message,
        errorCorrelationId: sanitized.correlationId,
      }));
      return false;
    }
  }

  setTargetIdentityId(id: string): void {
    this.state.update((s) => {
      const isSameAsResolved =
        s.resolvedTarget !== null && s.resolvedTarget.targetIdentityId === id;
      return {
        ...s,
        targetIdentityId: id,
        resolvedTarget: isSameAsResolved ? s.resolvedTarget : null,
        status: isSameAsResolved
          ? s.status
          : s.status === 'resolved' || s.status === 'empty'
            ? 'initial'
            : s.status,
      };
    });
  }

  setReplacementEnrollment(matricula: string): void {
    this.state.update((s) => ({ ...s, replacementEnrollment: matricula }));
  }

  setReason(reason: string): void {
    this.state.update((s) => ({ ...s, reason }));
  }

  clearFeedback(): void {
    this.state.update((s) => ({ ...s, feedbackMessage: null }));
  }

  clearErrors(): void {
    this.state.update((s) => ({
      ...s,
      errorMessage: null,
      errorCorrelationId: null,
      fieldErrors: INITIAL_FIELD_ERRORS,
    }));
  }

  reset(): void {
    this.lifecycleGeneration += 1;
    this.state.set(INITIAL_STATE);
  }

  private sanitizeResolveError(error: unknown): { message: string; correlationId: string } {
    const correlationId = this.extractCorrelationId(error);

    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) {
        return { message: 'Sua sessão expirou. Entre novamente para continuar.', correlationId };
      }
      if (error.status === 403) {
        return { message: 'Você não tem acesso a esta área.', correlationId };
      }
    }

    const baseMessage = 'Algo não funcionou como esperado. Tente novamente.';
    const message =
      correlationId !== 'N/D'
        ? `${baseMessage} Código de referência: ${correlationId}.`
        : baseMessage;

    return { message, correlationId };
  }

  private sanitizeCorrectionError(error: unknown): { message: string; correlationId: string } {
    const correlationId = this.extractCorrelationId(error);
    const errorCode = this.extractErrorCode(error);

    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) {
        return { message: 'Sua sessão expirou. Entre novamente para continuar.', correlationId };
      }
      if (error.status === 403) {
        return { message: 'Você não tem acesso a esta área.', correlationId };
      }
      if (error.status === 404) {
        return {
          message: 'A conta informada não foi localizada. Localize-a novamente antes de corrigir a matrícula.',
          correlationId,
        };
      }
      if (error.status === 400 || error.status === 422) {
        return { message: 'Revise os campos destacados e tente novamente.', correlationId };
      }
      if (error.status === 409 || errorCode.includes('MATRICULA_DUPLICADA') || errorCode.includes('CONTA_JA_VINCULADA')) {
        // UI-SPEC §332: Nova matrícula já registrada em outra conta (S6)
        const msg =
          correlationId !== 'N/D'
            ? `A nova matrícula já está registrada em outra conta. A correção não foi aplicada. Código de referência: ${correlationId}.`
            : 'A nova matrícula já está registrada em outra conta. A correção não foi aplicada.';
        return { message: msg, correlationId };
      }
    }

    const baseMessage = 'Não foi possível corrigir a matrícula. Verifique a conexão e tente novamente.';
    const message =
      correlationId !== 'N/D'
        ? `${baseMessage} Código de referência: ${correlationId}.`
        : baseMessage;

    return { message, correlationId };
  }

  private extractCorrelationId(error: unknown): string {
    if (error instanceof HttpErrorResponse) {
      if (error.error && typeof error.error === 'object') {
        const errorBody = error.error as Record<string, unknown>;
        if (typeof errorBody['correlationId'] === 'string' && errorBody['correlationId'].trim() !== '') {
          return errorBody['correlationId'].trim();
        }
      }
      const headerVal = error.headers?.get('x-correlation-id') || error.headers?.get('correlationId');
      if (headerVal && headerVal.trim() !== '') {
        return headerVal.trim();
      }
    }
    return 'N/D';
  }

  private extractErrorCode(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.error && typeof error.error === 'object') {
      const errorBody = error.error as Record<string, unknown>;
      if (typeof errorBody['code'] === 'string') {
        return errorBody['code'];
      }
    }
    return '';
  }
}
