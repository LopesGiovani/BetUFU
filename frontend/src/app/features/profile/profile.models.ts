/**
 * Modelos de View Model e Estado para o Perfil BETUFU.
 * Alinhado rigorosamente a 02-UI-SPEC.md, PROF-01, PROF-02, D-14, D-16, D-17, D-39, D-43 e D-44.
 */

export type ProfileStateStatus =
  | 'initial'
  | 'loading'
  | 'empty'
  | 'populated'
  | 'partial'
  | 'error';

export interface ProfileViewModel {
  readonly identityId: string;
  readonly nickname: string;
  readonly nicknameUpdatedAt: string | null;
  readonly matriculaStatus: string;
  readonly favoriteOrganizationId: string | null;
}

export interface ProfileState {
  readonly status: ProfileStateStatus;
  readonly profile: ProfileViewModel | null;
  readonly previousProfile: ProfileViewModel | null;
  readonly errorMessage: string | null;
  readonly errorCorrelationId: string | null;
  readonly isSaving: boolean;
  readonly isRetrying: boolean;
}
