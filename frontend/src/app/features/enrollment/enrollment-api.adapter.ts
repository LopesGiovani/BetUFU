import { Injectable, inject } from '@angular/core';
import { Observable, defer, map } from 'rxjs';
import { EnrollmentService } from '../../api/generated/api/enrollment.service';
import { EnrollmentStatusResponse } from '../../api/generated/model/enrollment-status-response';
import { EnrollmentViewModel } from './enrollment.models';

@Injectable({
  providedIn: 'root',
})
export class EnrollmentApiAdapter {
  private readonly enrollmentService = inject(EnrollmentService);

  /**
   * Consulta o estado do vínculo de matrícula da identidade autenticada atual no BFF.
   */
  fetchEnrollment(): Observable<EnrollmentViewModel | null> {
    return defer(() => this.enrollmentService.getEnrollment()).pipe(
      map((response) => this.adapt(response)),
    );
  }

  /**
   * Registra a matrícula institucional através do serviço gerado pelo contrato OpenAPI.
   * Não importa nem consome cliente HTTP de transporte diretamente (02-PATTERNS.md & 02-13-PLAN.md).
   */
  registerEnrollment(matricula: string): Observable<EnrollmentViewModel> {
    const trimmedMatricula = matricula.trim();
    return defer(() =>
      this.enrollmentService.putEnrollment({
        enrollmentRegistrationRequest: {
          matricula: trimmedMatricula,
        },
      }),
    ).pipe(
      map((response) => {
        const adapted = this.adapt(response);
        if (!adapted) {
          throw new Error('CONTRATO_MATRICULA_INVALIDO: resposta de registro de matricula vazia');
        }
        return adapted;
      }),
    );
  }

  /**
   * Adapta a resposta OpenAPI para o ViewModel tipado, aplicando validação estrita fail-closed.
   */
  adapt(response: EnrollmentStatusResponse | null | undefined): EnrollmentViewModel | null {
    if (!response) {
      return null;
    }

    if (typeof response.status !== 'string' || response.status.trim() === '') {
      throw new Error(
        'CONTRATO_MATRICULA_INVALIDO: campo obrigatorio status ausente ou invalido',
      );
    }

    return {
      status: response.status,
      createdAt: response.createdAt ?? null,
    };
  }
}
