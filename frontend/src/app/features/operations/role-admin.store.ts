import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom, retry, timer } from 'rxjs';
import { OperationsApiAdapter } from './operations-api.adapter';
import { OperationalRole } from '../../api/generated/model/operational-role';
import { RoleAdminState } from './operations.models';

const UUID_REGEX = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

const INITIAL_STATE: RoleAdminState = Object.freeze({
  status: 'initial',
  targetAccount: null,
  searchQuery: '',
  isSearching: false,
  isMutating: false,
  mutatingRole: null,
  feedbackMessage: null,
  errorMessage: null,
  errorCorrelationId: null,
  fieldError: null,
  actorIdentityId: null,
});

@Injectable({
  providedIn: 'root',
})
export class RoleAdminStore {
  private readonly adapter = inject(OperationsApiAdapter);

  private readonly state = signal<RoleAdminState>(INITIAL_STATE);

  // Seletores reativos públicos (Signals)
  readonly status = computed(() => this.state().status);
  readonly targetAccount = computed(() => this.state().targetAccount);
  readonly searchQuery = computed(() => this.state().searchQuery);
  readonly isSearching = computed(() => this.state().isSearching);
  readonly isMutating = computed(() => this.state().isMutating);
  readonly mutatingRole = computed(() => this.state().mutatingRole);
  readonly feedbackMessage = computed(() => this.state().feedbackMessage);
  readonly errorMessage = computed(() => this.state().errorMessage);
  readonly errorCorrelationId = computed(() => this.state().errorCorrelationId);
  readonly fieldError = computed(() => this.state().fieldError);
  readonly actorIdentityId = computed(() => this.state().actorIdentityId);

  readonly isEmpty = computed(() => this.state().status === 'empty');
  readonly isPopulated = computed(() => this.state().status === 'populated');
  readonly isError = computed(() => this.state().status === 'error');

  readonly hasNoGrantedRoles = computed(() => {
    const acc = this.state().targetAccount;
    return acc !== null && !acc.hasAnyGrantedRole;
  });

  readonly roles = computed(() => this.state().targetAccount?.roles ?? []);
  readonly targetNickname = computed(() => this.state().targetAccount?.targetNickname ?? null);
  readonly targetIdentityId = computed(() => this.state().targetAccount?.targetIdentityId ?? null);

  /**
   * Atualiza o contexto do ator atual (identificador da identidade e flag de único administrador)
   * para que as guardas visuais D-48 sejam recalculadas no cliente.
   */
  setActorContext(actorIdentityId: string | null): void {
    const current = this.state();
    let updatedAccount = current.targetAccount;

    if (updatedAccount) {
      const hasActorContext = Boolean(actorIdentityId);
      const isSelfTarget = Boolean(
        actorIdentityId && actorIdentityId === updatedAccount.targetIdentityId,
      );
      const updatedRoles = updatedAccount.roles.map((r) => {
        if (r.role === OperationalRole.AdministradorGeral) {
          const selfGrantBlocked = !r.isVigente && (!hasActorContext || isSelfTarget);
          const selfGrantBlockedReason = selfGrantBlocked
            ? isSelfTarget
              ? 'Concessão a si mesmo bloqueada. Peça a outro administrador geral.'
              : 'Não foi possível confirmar sua identidade. Recarregue a página antes de conceder.'
            : null;
          return {
            ...r,
            selfGrantBlocked,
            selfGrantBlockedReason,
          };
        }
        return r;
      });

      updatedAccount = {
        ...updatedAccount,
        roles: Object.freeze(updatedRoles),
      };
    }

    this.state.update((s) => ({
      ...s,
      actorIdentityId,
      targetAccount: updatedAccount,
    }));
  }

  /**
   * Consulta os papéis de uma identidade alvo por seu UUID público (D-41, D-42).
   * Consulta de leitura permite repetição automática (máx 2 retentativas, backoff 250/500ms).
   */
  async searchAccount(targetIdentityId: string): Promise<boolean> {
    const trimmedId = (targetIdentityId || '').trim();

    if (!trimmedId || !UUID_REGEX.test(trimmedId)) {
      this.state.update((s) => ({
        ...s,
        searchQuery: trimmedId,
        fieldError: 'Este identificador não tem o formato esperado. Copie-o do perfil da pessoa.',
        errorMessage: null,
        errorCorrelationId: null,
        feedbackMessage: null,
      }));
      return false;
    }

    this.state.update((s) => ({
      ...s,
      status: 'searching',
      searchQuery: trimmedId,
      isSearching: true,
      fieldError: null,
      errorMessage: null,
      errorCorrelationId: null,
      feedbackMessage: null,
    }));

    try {
      const adapted = await firstValueFrom(
        this.adapter.getRoles(trimmedId, this.state().actorIdentityId).pipe(
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

      if (!adapted) {
        this.state.update((s) => ({
          ...s,
          status: 'empty',
          targetAccount: null,
          isSearching: false,
        }));
        return true;
      }

      this.state.update((s) => ({
        ...s,
        status: 'populated',
        targetAccount: adapted,
        isSearching: false,
      }));
      return true;
    } catch (error: unknown) {
      if (error instanceof HttpErrorResponse && error.status === 404) {
        // 404: Identidade não localizada no BFF (estado empty per UI-SPEC)
        this.state.update((s) => ({
          ...s,
          status: 'empty',
          targetAccount: null,
          isSearching: false,
          errorMessage: null,
          errorCorrelationId: null,
        }));
        return true;
      }

      const sanitized = this.sanitizeSearchError(error);
      this.state.update((s) => ({
        ...s,
        status: 'error',
        targetAccount: null,
        isSearching: false,
        errorMessage: sanitized.message,
        errorCorrelationId: sanitized.correlationId,
      }));
      return false;
    }
  }

  /**
   * Concede o papel de Administrador Geral à conta localizada.
   * Apenas ADMINISTRADOR_GERAL é acionável na UI na Fase 2 (D-48).
   * Nenhuma mutação repete automaticamente (D-16).
   */
  async grantRole(
    role: 'ADMINISTRADOR_GERAL' | 'ADMINISTRADOR_COMPETICAO' | 'MODERADOR',
  ): Promise<boolean> {
    if (role !== OperationalRole.AdministradorGeral) {
      return false;
    }

    const current = this.state();
    const globalRole = current.targetAccount?.roles.find(
      (item) => item.role === OperationalRole.AdministradorGeral,
    );
    if (!current.targetAccount || current.isMutating || globalRole?.selfGrantBlocked) {
      return false;
    }

    const targetId = current.targetAccount.targetIdentityId;
    const targetNickname = current.targetAccount.targetNickname || targetId;

    this.state.update((s) => ({
      ...s,
      isMutating: true,
      mutatingRole: role,
      errorMessage: null,
      errorCorrelationId: null,
      feedbackMessage: null,
    }));

    try {
      const adapted = await firstValueFrom(
        this.adapter.grantRole(
          targetId,
          OperationalRole.AdministradorGeral,
          current.actorIdentityId,
        ),
      );

      this.state.update((s) => ({
        ...s,
        status: 'populated',
        targetAccount: adapted,
        isMutating: false,
        mutatingRole: null,
        feedbackMessage: `Administrador geral concedido a ${targetNickname}. As sessões dessa pessoa foram encerradas.`,
        errorMessage: null,
        errorCorrelationId: null,
      }));
      return true;
    } catch (error: unknown) {
      const sanitized = this.sanitizeMutationError(error);
      this.state.update((s) => ({
        ...s,
        isMutating: false,
        mutatingRole: null,
        errorMessage: sanitized.message,
        errorCorrelationId: sanitized.correlationId,
      }));
      return false;
    }
  }

  /**
   * Revoga o papel de Administrador Geral da conta localizada.
   * Apenas ADMINISTRADOR_GERAL é acionável na UI na Fase 2 (D-48).
   * Nenhuma mutação repete automaticamente (D-16).
   */
  async revokeRole(
    role: 'ADMINISTRADOR_GERAL' | 'ADMINISTRADOR_COMPETICAO' | 'MODERADOR',
  ): Promise<boolean> {
    if (role !== OperationalRole.AdministradorGeral) {
      return false;
    }

    const current = this.state();
    if (!current.targetAccount || current.isMutating) {
      return false;
    }

    const targetId = current.targetAccount.targetIdentityId;
    const targetNickname = current.targetAccount.targetNickname || targetId;

    this.state.update((s) => ({
      ...s,
      isMutating: true,
      mutatingRole: role,
      errorMessage: null,
      errorCorrelationId: null,
      feedbackMessage: null,
    }));

    try {
      const adapted = await firstValueFrom(
        this.adapter.revokeRole(
          targetId,
          OperationalRole.AdministradorGeral,
          current.actorIdentityId,
        ),
      );

      this.state.update((s) => ({
        ...s,
        status: 'populated',
        targetAccount: adapted,
        isMutating: false,
        mutatingRole: null,
        feedbackMessage: `Administrador geral revogado de ${targetNickname}. As sessões dessa pessoa foram encerradas.`,
        errorMessage: null,
        errorCorrelationId: null,
      }));
      return true;
    } catch (error: unknown) {
      const sanitized = this.sanitizeMutationError(error);
      this.state.update((s) => ({
        ...s,
        isMutating: false,
        mutatingRole: null,
        errorMessage: sanitized.message,
        errorCorrelationId: sanitized.correlationId,
      }));
      return false;
    }
  }

  clearFeedback(): void {
    this.state.update((s) => ({ ...s, feedbackMessage: null }));
  }

  clearErrors(): void {
    this.state.update((s) => ({
      ...s,
      errorMessage: null,
      errorCorrelationId: null,
      fieldError: null,
    }));
  }

  reset(): void {
    this.state.set(INITIAL_STATE);
  }

  private sanitizeSearchError(error: unknown): { message: string; correlationId: string } {
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

  private sanitizeMutationError(error: unknown): { message: string; correlationId: string } {
    const correlationId = this.extractCorrelationId(error);
    const errorCode = this.extractErrorCode(error);

    if (error instanceof HttpErrorResponse) {
      if (error.status === 401) {
        return { message: 'Sua sessão expirou. Entre novamente para continuar.', correlationId };
      }
      if (error.status === 403) {
        // Guarda (a) - Autoconcessão bloqueada
        if (errorCode.includes('SELF_GRANT_BLOCKED') || errorCode.includes('SELF_GRANT')) {
          const msg =
            correlationId !== 'N/D'
              ? `Você não pode conceder papéis a si mesmo. Peça a outro administrador geral. Código de referência: ${correlationId}.`
              : 'Você não pode conceder papéis a si mesmo. Peça a outro administrador geral.';
          return { message: msg, correlationId };
        }
        return { message: 'Você não tem acesso a esta área.', correlationId };
      }
      if (error.status === 409) {
        // Guarda (b) - Último administrador geral
        if (
          errorCode.includes('LAST_ADMIN') ||
          errorCode.includes('LAST_ADMIN_REVOCATION_BLOCKED')
        ) {
          const msg =
            correlationId !== 'N/D'
              ? `Você é o único administrador geral. Conceda o papel a outra pessoa antes de revogar o seu. Código de referência: ${correlationId}.`
              : 'Você é o único administrador geral. Conceda o papel a outra pessoa antes de revogar o seu.';
          return { message: msg, correlationId };
        }
      }
    }

    const baseMessage =
      'Não foi possível alterar o papel. Nenhuma alteração foi aplicada. Tente novamente.';
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
        if (
          typeof errorBody['correlationId'] === 'string' &&
          errorBody['correlationId'].trim() !== ''
        ) {
          return errorBody['correlationId'].trim();
        }
      }
      const headerVal =
        error.headers?.get('x-correlation-id') || error.headers?.get('correlationId');
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
