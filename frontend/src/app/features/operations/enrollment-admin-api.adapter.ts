import { Injectable, inject } from '@angular/core';
import { Observable, defer, map } from 'rxjs';
import { RoleService } from '../../api/generated/api/role.service';
import { EnrollmentService } from '../../api/generated/api/enrollment.service';
import { RoleGrantResponse } from '../../api/generated/model/role-grant-response';
import { EnrollmentCorrectionResponse } from '../../api/generated/model/enrollment-correction-response';

const D44_NICKNAME_REGEX = /^[A-Za-z0-9_-]{3,20}$/;

export interface ResolvedTargetAccount {
  readonly targetIdentityId: string;
  readonly nickname: string;
}

export interface EnrollmentCorrectionResult {
  readonly targetIdentityId: string;
  readonly status: string;
  readonly correctedAt: string;
  readonly previousBindingReleased: boolean;
}

@Injectable({
  providedIn: 'root',
})
export class EnrollmentAdminApiAdapter {
  private readonly roleService = inject(RoleService);
  private readonly enrollmentService = inject(EnrollmentService);

  /**
   * Resolve o identificador público da conta através de RoleService.getRoles.
   * Conforme especificado, a conta é considerada resolvida SOMENTE quando:
   * 1. response.identityId for estritamente igual ao UUID requisitado.
   * 2. response.nickname for uma string que JÁ case com a gramática D-44 (3 a 20 chars [A-Za-z0-9_-]).
   * Não efetua trim, case-fold ou qualquer normalização para transformar apelido inválido em válido.
   * Descarta imediatamente response.roles sem repassar, logar ou armazenar metadados de papel.
   */
  resolveTarget(targetIdentityId: string): Observable<ResolvedTargetAccount | null> {
    return defer(() => this.roleService.getRoles({ targetIdentityId })).pipe(
      map((response) => this.adaptTargetResolution(response, targetIdentityId)),
    );
  }

  /**
   * Executa a mutação de correção de matrícula através de EnrollmentService.correctEnrollment.
   * Passa os parâmetros exatos do contrato gerado e projeta a resposta sem acrescentar estado de autorização.
   */
  correctEnrollment(
    targetIdentityId: string,
    matricula: string,
    reason: string,
  ): Observable<EnrollmentCorrectionResult> {
    return defer(() =>
      this.enrollmentService.correctEnrollment({
        enrollmentCorrectionRequest: {
          targetIdentityId,
          matricula,
          reason,
        },
      }),
    ).pipe(map((response) => this.adaptCorrectionResponse(response, targetIdentityId)));
  }

  private adaptTargetResolution(
    response: RoleGrantResponse | null | undefined,
    requestedTargetIdentityId: string,
  ): ResolvedTargetAccount | null {
    if (!response) {
      return null;
    }

    // 1. targetIdentityId deve coincidir exatamente com o requisitado
    if (response.identityId !== requestedTargetIdentityId) {
      return null;
    }

    // 2. nickname deve ser string não-nula que JÁ case com a regex D-44 estrita (sem trim/fold)
    const nickname = response.nickname;
    if (typeof nickname !== 'string' || !D44_NICKNAME_REGEX.test(nickname)) {
      return null;
    }

    // 3. Descarte imediato de response.roles (nunca entra no retorno)
    return Object.freeze({
      targetIdentityId: response.identityId,
      nickname,
    });
  }

  private adaptCorrectionResponse(
    response: EnrollmentCorrectionResponse | null | undefined,
    requestedTargetIdentityId: string,
  ): EnrollmentCorrectionResult {
    if (!response || typeof response !== 'object') {
      throw new Error('CONTRATO_CORRECAO_INVALIDO: resposta de correcao nula ou invalida');
    }

    if (response.targetIdentityId !== requestedTargetIdentityId) {
      throw new Error(
        'CONTRATO_CORRECAO_INVALIDO: targetIdentityId retornado diverge do solicitado',
      );
    }

    if (typeof response.status !== 'string' || response.status.trim() === '') {
      throw new Error('CONTRATO_CORRECAO_INVALIDO: status ausente ou invalido');
    }

    if (
      typeof response.correctedAt !== 'string' ||
      response.correctedAt.trim() === '' ||
      isNaN(Date.parse(response.correctedAt))
    ) {
      throw new Error('CONTRATO_CORRECAO_INVALIDO: correctedAt ausente ou instante invalido');
    }

    if (typeof response.previousBindingReleased !== 'boolean') {
      throw new Error('CONTRATO_CORRECAO_INVALIDO: previousBindingReleased nao e booleano');
    }

    return Object.freeze({
      targetIdentityId: response.targetIdentityId,
      status: response.status,
      correctedAt: response.correctedAt,
      previousBindingReleased: response.previousBindingReleased,
    });
  }
}
