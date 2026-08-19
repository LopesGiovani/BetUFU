import { Injectable, inject } from '@angular/core';
import { Observable, defer, map } from 'rxjs';
import { DiagnosticService } from '../../api/generated/api/diagnostic.service';
import { DiagnosticResponse } from '../../api/generated/model/diagnostic-response';
import { DiagnosticComponent } from '../../api/generated/model/diagnostic-component';
import {
  CANONICAL_COMPONENT_NAMES,
  CanonicalComponentName,
  ComponentHealthStatus,
  DiagnosticCardViewModel,
  DiagnosticSnapshotViewModel,
} from './diagnostic.models';

@Injectable({
  providedIn: 'root',
})
export class DiagnosticApiAdapter {
  private readonly diagnosticService = inject(DiagnosticService);

  /**
   * Consulta o endpoint de diagnóstico do BFF e adapta o payload para o ViewModel estrito.
   */
  fetchDiagnostic(): Observable<DiagnosticSnapshotViewModel | null> {
    return defer(() => this.diagnosticService.getDiagnostic()).pipe(
      map((response) => this.adapt(response)),
    );
  }

  /**
   * Adapta o payload OpenAPI para o ViewModel, aplicando validação estrita zero-one-many e ordem canônica.
   */
  adapt(response: DiagnosticResponse | null | undefined): DiagnosticSnapshotViewModel | null {
    if (!response || !response.components || response.components.length === 0) {
      return null;
    }

    const components = response.components;

    // Regra zero-one-many (01-UI-SPEC:181): somente zero (empty) ou exatamente seis cartões (populated).
    // Qualquer contagem entre 1 e 5 ou maior que 6 falha fechada.
    if (components.length !== CANONICAL_COMPONENT_NAMES.length) {
      throw new Error(
        `CONTRATO_COMPONENTES_INVALIDO: esperado exatamente ${CANONICAL_COMPONENT_NAMES.length} componentes, recebido ${components.length}`,
      );
    }

    const adaptedCards: DiagnosticCardViewModel[] = [];

    for (let i = 0; i < CANONICAL_COMPONENT_NAMES.length; i++) {
      const expectedName = CANONICAL_COMPONENT_NAMES[i];
      const rawComponent = components[i];

      if (!rawComponent || rawComponent.name !== expectedName) {
        throw new Error(
          `ORDEM_CANONICA_INVALIDA: posicao ${i} esperava '${expectedName}', mas recebeu '${rawComponent?.name ?? 'indefinido'}'`,
        );
      }

      adaptedCards.push(this.adaptCard(rawComponent, expectedName));
    }

    return {
      status: this.normalizeStatus(response.status),
      summary: response.summary ?? 'Resumo do diagnóstico da plataforma',
      correlationId: response.correlationId ?? 'N/D',
      timestamp: response.timestamp ?? new Date().toISOString(),
      components: Object.freeze(adaptedCards),
      environment: response.environment ?? null,
      version: response.version ?? null,
    };
  }

  private adaptCard(
    raw: DiagnosticComponent,
    canonicalName: CanonicalComponentName,
  ): DiagnosticCardViewModel {
    const status = this.normalizeStatus(raw.status);

    let statusLabel: string;
    let statusDescription: string;
    let iconName: string;

    switch (status) {
      case 'UP':
        statusLabel = 'Disponível';
        statusDescription = 'Respondendo normalmente.';
        iconName = 'check_circle';
        break;
      case 'DEGRADED':
        statusLabel = 'Degradado';
        statusDescription = 'Respondendo com limitações.';
        iconName = 'warning_amber';
        break;
      case 'DOWN':
      default:
        statusLabel = 'Indisponível';
        statusDescription = 'Não foi possível confirmar a disponibilidade.';
        iconName = 'error_outline';
        break;
    }

    return {
      name: canonicalName,
      status,
      statusLabel,
      statusDescription,
      iconName,
      detail: raw.detail ? this.sanitizeDetail(raw.detail) : null,
      durationMs: typeof raw.durationMs === 'number' ? raw.durationMs : null,
    };
  }

  private normalizeStatus(status: unknown): ComponentHealthStatus {
    if (status === 'UP' || status === 'DEGRADED' || status === 'DOWN') {
      return status;
    }
    return 'DOWN';
  }

  private sanitizeDetail(detail: string): string {
    // Garante que detalhes nunca contenham tags HTML ou informações de stack
    return detail.replace(/<[^>]*>/gu, '').trim();
  }
}
