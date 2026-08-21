import { Injectable, inject } from '@angular/core';
import { Observable, defer, map } from 'rxjs';
import { ProfileService } from '../../api/generated/api/profile.service';
import { CurrentProfileResponse } from '../../api/generated/model/current-profile-response';
import { ProfileViewModel } from './profile.models';

@Injectable({
  providedIn: 'root',
})
export class ProfileApiAdapter {
  private readonly profileService = inject(ProfileService);

  /**
   * Consulta a projeção pública do perfil da identidade autenticada atual no BFF.
   */
  fetchProfile(): Observable<ProfileViewModel | null> {
    return defer(() => this.profileService.getMyProfile()).pipe(
      map((response) => this.adapt(response)),
    );
  }

  /**
   * Cria ou atualiza o apelido do perfil próprio através do serviço gerado pelo contrato OpenAPI.
   * Não importa nem consome cliente HTTP de transporte diretamente (02-PATTERNS.md & 02-05-PLAN.md).
   */
  updateProfile(nickname: string): Observable<ProfileViewModel> {
    const trimmedNickname = nickname.trim();
    return defer(() =>
      this.profileService.putMyProfile({
        profileUpdateRequest: {
          nickname: trimmedNickname,
        },
      }),
    ).pipe(
      map((response) => {
        const adapted = this.adapt(response);
        if (!adapted) {
          throw new Error('CONTRATO_PERFIL_INVALIDO: resposta de atualizacao de perfil vazia');
        }
        return adapted;
      }),
    );
  }

  /**
   * Adapta a resposta OpenAPI para o ViewModel tipado, aplicando validação estrita fail-closed.
   */
  adapt(response: CurrentProfileResponse | null | undefined): ProfileViewModel | null {
    if (!response) {
      return null;
    }

    if (
      typeof response.identityId !== 'string' ||
      response.identityId.trim() === '' ||
      typeof response.nickname !== 'string' ||
      response.nickname.trim() === ''
    ) {
      throw new Error(
        'CONTRATO_PERFIL_INVALIDO: campos obrigatorios identityId ou nickname ausentes ou invalidos',
      );
    }

    return {
      identityId: response.identityId,
      nickname: response.nickname,
      nicknameUpdatedAt: response.nicknameUpdatedAt ?? null,
      matriculaStatus:
        typeof response.matriculaStatus === 'string' && response.matriculaStatus.trim() !== ''
          ? response.matriculaStatus
          : 'NAO_REGISTRADA',
    };
  }
}
