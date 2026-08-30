import { Injectable, inject } from '@angular/core';
import { Observable, defer, map, of } from 'rxjs';
import { ProfileService } from '../../api/generated/api/profile.service';
import { SportsOrganizationService } from '../../api/generated/api/sports-organization.service';
import { CurrentProfileResponse } from '../../api/generated/model/current-profile-response';
import { SportsOrganizationSummary } from '../../api/generated/model/sports-organization-summary';
import { ProfileViewModel } from './profile.models';

@Injectable({
  providedIn: 'root',
})
export class ProfileApiAdapter {
  private readonly profileService = inject(ProfileService);
  private readonly organizationService = inject(SportsOrganizationService, { optional: true });

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

  fetchFavoriteOrganizations(): Observable<readonly SportsOrganizationSummary[]> {
    if (!this.organizationService) return of([]);
    return defer(() => this.organizationService!.listSportsOrganizations()).pipe(
      map((response) => response
        .filter((organization) => organization.status === 'PUBLICADO')
        .sort((left, right) => left.name.localeCompare(right.name, 'pt-BR'))),
    );
  }

  updateFavoriteOrganization(organizationId: string | null): Observable<ProfileViewModel> {
    return defer(() => this.profileService.putMyFavoriteOrganization({
      favoriteOrganizationRequest: { organizationId },
    })).pipe(
      map((response) => {
        const adapted = this.adapt(response);
        if (!adapted) throw new Error('CONTRATO_PERFIL_INVALIDO: resposta de favorito vazia');
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
      response.nickname.trim() === '' ||
      (response.favoriteOrganizationId !== null && typeof response.favoriteOrganizationId !== 'string')
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
      favoriteOrganizationId: response.favoriteOrganizationId ?? null,
    };
  }
}
