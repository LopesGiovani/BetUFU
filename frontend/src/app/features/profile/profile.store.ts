import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom, retry, timer } from 'rxjs';
import { ProfileApiAdapter } from './profile-api.adapter';
import { ProfileState, ProfileViewModel } from './profile.models';

const INITIAL_STATE: ProfileState = Object.freeze({
  status: 'initial',
  profile: null,
  previousProfile: null,
  errorMessage: null,
  errorCorrelationId: null,
  isSaving: false,
  isRetrying: false,
});

@Injectable({
  providedIn: 'root',
})
export class ProfileStore {
  private readonly adapter = inject(ProfileApiAdapter);

  private readonly state = signal<ProfileState>(INITIAL_STATE);

  // Seletores reativos públicos (Signals)
  readonly status = computed(() => this.state().status);
  readonly profile = computed(() => this.state().profile);
  readonly previousProfile = computed(() => this.state().previousProfile);
  readonly errorMessage = computed(() => this.state().errorMessage);
  readonly errorCorrelationId = computed(() => this.state().errorCorrelationId);
  readonly isSaving = computed(() => this.state().isSaving);
  readonly isRetrying = computed(() => this.state().isRetrying);

  readonly isLoading = computed(() => this.state().status === 'loading');
  readonly isPopulated = computed(() => this.state().status === 'populated');
  readonly isEmpty = computed(() => this.state().status === 'empty');
  readonly isError = computed(() => this.state().status === 'error');

  /**
   * Retorna o perfil atual ou o anterior durante recargas (D-04 e D-17).
   */
  readonly displayProfile = computed(() => {
    return this.state().profile ?? this.state().previousProfile;
  });

  readonly nickname = computed(() => this.displayProfile()?.nickname ?? null);
  readonly identityId = computed(() => this.displayProfile()?.identityId ?? null);
  readonly hasProfile = computed(() => this.state().profile !== null);

  /**
   * Consulta o perfil próprio no BFF com retry limitado na leitura (máx 2, backoff 250/500ms) (D-16).
   */
  async loadProfile(): Promise<void> {
    const currentState = this.state();
    const activeProfile = currentState.profile ?? currentState.previousProfile;

    this.state.set({
      status: 'loading',
      profile: activeProfile,
      previousProfile: activeProfile,
      errorMessage: null,
      errorCorrelationId: null,
      isSaving: false,
      isRetrying: false,
    });

    try {
      const adapted = await firstValueFrom(
        this.adapter.fetchProfile().pipe(
          retry({
            count: 2,
            delay: (_error, retryCount) => {
              this.state.update((s) => ({ ...s, isRetrying: true }));
              return timer(retryCount === 1 ? 250 : 500);
            },
          }),
        ),
      );

      if (!adapted) {
        // Garantia 'empty': resposta nula
        this.state.set({
          status: 'empty',
          profile: null,
          previousProfile: activeProfile,
          errorMessage: null,
          errorCorrelationId: null,
          isSaving: false,
          isRetrying: false,
        });
      } else {
        // Garantia 'populated': perfil carregado com sucesso
        this.state.set({
          status: 'populated',
          profile: adapted,
          previousProfile: adapted,
          errorMessage: null,
          errorCorrelationId: null,
          isSaving: false,
          isRetrying: false,
        });
      }
    } catch (error: unknown) {
      if (error instanceof HttpErrorResponse && error.status === 404) {
        // 404 do BFF significa que a identidade autenticada ainda nao tem perfil criado (garantia empty D-39)
        this.state.set({
          status: 'empty',
          profile: null,
          previousProfile: activeProfile,
          errorMessage: null,
          errorCorrelationId: null,
          isSaving: false,
          isRetrying: false,
        });
        return;
      }

      const sanitized = this.sanitizeError(error);
      this.state.set({
        status: 'error',
        profile: null,
        previousProfile: activeProfile,
        errorMessage: sanitized.message,
        errorCorrelationId: sanitized.correlationId,
        isSaving: false,
        isRetrying: false,
      });
    }
  }

  /**
   * Salva o apelido no BFF. Nenhuma retentativa automática em mutação (02-UI-SPEC.md §Comportamento de formulário).
   */
  async saveNickname(nickname: string): Promise<boolean> {
    const currentState = this.state();
    const activeProfile = currentState.profile ?? currentState.previousProfile;

    this.state.set({
      ...currentState,
      isSaving: true,
      errorMessage: null,
      errorCorrelationId: null,
    });

    try {
      const adapted = await firstValueFrom(this.adapter.updateProfile(nickname));

      this.state.set({
        status: 'populated',
        profile: adapted,
        previousProfile: adapted,
        errorMessage: null,
        errorCorrelationId: null,
        isSaving: false,
        isRetrying: false,
      });
      return true;
    } catch (error: unknown) {
      const sanitized = this.sanitizeSaveError(error);

      this.state.set({
        status: 'error',
        profile: null,
        previousProfile: activeProfile,
        errorMessage: sanitized.message,
        errorCorrelationId: sanitized.correlationId,
        isSaving: false,
        isRetrying: false,
      });
      return false;
    }
  }

  private sanitizeError(error: unknown): { message: string; correlationId: string } {
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
      return { message: 'Dados de perfil inválidos recebidos da plataforma.', correlationId };
    }

    const message =
      correlationId !== 'N/D'
        ? `Algo não funcionou como esperado. Tente novamente. Código de referência: ${correlationId}.`
        : 'Algo não funcionou como esperado. Tente novamente.';

    return { message, correlationId };
  }

  private sanitizeSaveError(error: unknown): { message: string; correlationId: string } {
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
      return { message: 'Dados de perfil inválidos recebidos da plataforma.', correlationId };
    }

    const message =
      correlationId !== 'N/D'
        ? `Não foi possível salvar seu perfil. Verifique a conexão e tente novamente. Código de referência: ${correlationId}.`
        : 'Não foi possível salvar seu perfil. Verifique a conexão e tente novamente.';

    return { message, correlationId };
  }
}
