import { Injectable, inject } from '@angular/core';
import { Observable, defer, map } from 'rxjs';
import { OperationalCapabilitiesService } from '../../api/generated/api/operational-capabilities.service';
import { OperationalPanelService } from '../../api/generated/api/operational-panel.service';
import { RoleService } from '../../api/generated/api/role.service';
import { OperationalCapability as GeneratedOperationalCapability } from '../../api/generated/model/operational-capability';
import { OperationalPanelResponse } from '../../api/generated/model/operational-panel-response';
import { RoleGrantResponse } from '../../api/generated/model/role-grant-response';
import { OperationalRole } from '../../api/generated/model/operational-role';
import { RejectedEnrollmentsBlock } from '../../api/generated/model/rejected-enrollments-block';
import { AdministrativeCorrectionsBlock } from '../../api/generated/model/administrative-corrections-block';
import { AdministrativeCorrectionEventDto } from '../../api/generated/model/administrative-correction-event-dto';
import { RoleChangesBlock } from '../../api/generated/model/role-changes-block';
import { RoleChangeEventDto } from '../../api/generated/model/role-change-event-dto';
import { UnregisteredEnrollmentsBlock } from '../../api/generated/model/unregistered-enrollments-block';
import {
  AdministrativeCorrectionEventViewModel,
  AdministrativeCorrectionsViewModel,
  OperationalCapabilityName,
  OperationsPanelSnapshotViewModel,
  RejectedEnrollmentsViewModel,
  RoleChangeEventViewModel,
  RoleChangesViewModel,
  RoleItemViewModel,
  TargetAccountRolesViewModel,
  TimeWindowViewModel,
  UnavailableBlockKey,
  UnregisteredEnrollmentsViewModel,
} from './operations.models';

const SAFE_UNAVAILABLE_BLOCK_MESSAGE = 'Este bloco não pôde ser carregado.';

const OPERATIONAL_CAPABILITY_ORDER: readonly OperationalCapabilityName[] = Object.freeze([
  GeneratedOperationalCapability.ViewOperationalPanel,
  GeneratedOperationalCapability.ManageRoles,
  GeneratedOperationalCapability.CorrectEnrollment,
]);

@Injectable({
  providedIn: 'root',
})
export class OperationsApiAdapter {
  private readonly operationalCapabilitiesService = inject(OperationalCapabilitiesService);
  private readonly operationalPanelService = inject(OperationalPanelService);
  private readonly roleService = inject(RoleService);

  /**
   * Consulta o painel operacional do BFF e adapta o payload OpenAPI para o ViewModel estrito.
   * Utiliza exclusivamente o OperationalPanelService gerado.
   */
  fetchOperationalPanel(): Observable<OperationsPanelSnapshotViewModel | null> {
    return defer(() => this.operationalPanelService.getOperationalPanel()).pipe(
      map((response) => this.adapt(response)),
    );
  }

  fetchOperationalCapabilities(): Observable<readonly OperationalCapabilityName[]> {
    return defer(() => this.operationalCapabilitiesService.getOperationalCapabilities()).pipe(
      map((response) => this.adaptOperationalCapabilities(response)),
    );
  }

  adaptOperationalCapabilities(response: unknown): readonly OperationalCapabilityName[] {
    if (typeof response !== 'object' || response === null) {
      return Object.freeze([]);
    }

    const capabilities = (response as { readonly capabilities?: unknown }).capabilities;
    if (!Array.isArray(capabilities)) {
      return Object.freeze([]);
    }

    const occurrences = new Map<unknown, number>();
    for (const capability of capabilities) {
      occurrences.set(capability, (occurrences.get(capability) ?? 0) + 1);
    }
    return Object.freeze(
      OPERATIONAL_CAPABILITY_ORDER.filter((capability) => occurrences.get(capability) === 1),
    );
  }

  /**
   * Adapta o payload da API para o ViewModel, tratando a presença ou ausência dos blocos opcionais.
   */
  adapt(
    response: OperationalPanelResponse | null | undefined,
  ): OperationsPanelSnapshotViewModel | null {
    if (!response) {
      return null;
    }

    const queriedAt = response.queriedAt ?? new Date().toISOString();
    const formattedQueriedAt = this.formatTime(queriedAt);

    const timeWindow: TimeWindowViewModel = {
      since: response.timeWindow?.since ?? '',
      until: response.timeWindow?.until ?? '',
    };

    const unavailableBlocks: UnavailableBlockKey[] = Array.isArray(response.unavailableBlocks)
      ? response.unavailableBlocks.filter(
          (block): block is UnavailableBlockKey =>
            block === 'ROLE_CHANGES' ||
            block === 'REJECTED_ENROLLMENTS_COUNT' ||
            block === 'ADMINISTRATIVE_CORRECTIONS' ||
            block === 'UNREGISTERED_ENROLLMENTS_COUNT',
        )
      : [];

    const isRejectedEnrollmentsUnavailable = unavailableBlocks.includes(
      'REJECTED_ENROLLMENTS_COUNT',
    );
    const isAdministrativeCorrectionsUnavailable = unavailableBlocks.includes(
      'ADMINISTRATIVE_CORRECTIONS',
    );
    const isRoleChangesUnavailable = unavailableBlocks.includes('ROLE_CHANGES');
    const isUnregisteredEnrollmentsUnavailable = unavailableBlocks.includes(
      'UNREGISTERED_ENROLLMENTS_COUNT',
    );

    const rejectedEnrollments = response.rejectedEnrollmentsCount
      ? this.adaptRejectedEnrollments(response.rejectedEnrollmentsCount)
      : null;

    const administrativeCorrections = response.administrativeCorrections
      ? this.adaptAdministrativeCorrections(response.administrativeCorrections)
      : null;

    const roleChanges = response.roleChanges ? this.adaptRoleChanges(response.roleChanges) : null;

    const unregisteredEnrollments = response.unregisteredEnrollmentsCount
      ? this.adaptUnregisteredEnrollments(response.unregisteredEnrollmentsCount)
      : null;

    const hasAnyBlock =
      rejectedEnrollments !== null ||
      administrativeCorrections !== null ||
      roleChanges !== null ||
      unregisteredEnrollments !== null ||
      unavailableBlocks.length > 0;

    const hasAnyPending =
      (rejectedEnrollments?.hasPending ?? false) ||
      (roleChanges?.hasPending ?? false) ||
      (unregisteredEnrollments?.hasPending ?? false) ||
      (administrativeCorrections?.totalCount ?? 0) > 0;

    return Object.freeze({
      queriedAt,
      formattedQueriedAt,
      timeWindow,
      rejectedEnrollments,
      administrativeCorrections,
      roleChanges,
      unregisteredEnrollments,
      unavailableBlocks: Object.freeze(unavailableBlocks),
      isRejectedEnrollmentsUnavailable,
      isAdministrativeCorrectionsUnavailable,
      isRoleChangesUnavailable,
      isUnregisteredEnrollmentsUnavailable,
      unavailableBlockMessage: SAFE_UNAVAILABLE_BLOCK_MESSAGE,
      hasAnyBlock,
      hasAnyPending,
    });
  }

  private adaptRejectedEnrollments(raw: RejectedEnrollmentsBlock): RejectedEnrollmentsViewModel {
    const duplicateCount = typeof raw.duplicateCount === 'number' ? raw.duplicateCount : 0;
    const attemptLimitCount = typeof raw.attemptLimitCount === 'number' ? raw.attemptLimitCount : 0;
    const totalCount =
      typeof raw.totalCount === 'number' ? raw.totalCount : duplicateCount + attemptLimitCount;
    const hasPending = totalCount > 0;

    const label = totalCount === 1 ? '1 recusa registrada' : `${totalCount} recusas registradas`;

    const duplicateLabel =
      duplicateCount === 1 ? '1 por duplicidade' : `${duplicateCount} por duplicidade`;

    const attemptLimitLabel =
      attemptLimitCount === 1
        ? '1 por limite de tentativas'
        : `${attemptLimitCount} por limite de tentativas`;

    return Object.freeze({
      duplicateCount,
      attemptLimitCount,
      totalCount,
      hasPending,
      label,
      duplicateLabel,
      attemptLimitLabel,
    });
  }

  private adaptAdministrativeCorrections(
    raw: AdministrativeCorrectionsBlock,
  ): AdministrativeCorrectionsViewModel {
    const rawEvents = Array.isArray(raw.recentEvents) ? raw.recentEvents : [];
    const adaptedEvents: AdministrativeCorrectionEventViewModel[] = rawEvents.map((event) =>
      this.adaptAdministrativeCorrectionEvent(event),
    );

    const totalCount = adaptedEvents.length;
    const label =
      totalCount === 0
        ? 'Nenhuma correção recente'
        : totalCount === 1
          ? '1 correção registrada'
          : `${totalCount} correções registradas`;

    return Object.freeze({
      events: Object.freeze(adaptedEvents),
      totalCount,
      label,
    });
  }

  private adaptAdministrativeCorrectionEvent(
    event: AdministrativeCorrectionEventDto,
  ): AdministrativeCorrectionEventViewModel {
    const occurredAt = event.occurredAt ?? '';
    return {
      eventId: event.eventId,
      occurredAt,
      formattedOccurredAt: this.formatDateTime(occurredAt),
      actorIdentityId: event.actorIdentityId ?? null,
      actorRole: event.actorRole ?? null,
      targetIdentityId: event.targetIdentityId ?? null,
      targetNickname: event.targetNickname ?? null,
      action: event.action,
      outcome: event.outcome,
      hasReason: Boolean(event.hasReason),
      reason: typeof event.reason === 'string' ? event.reason : null,
    };
  }

  private adaptRoleChanges(raw: RoleChangesBlock): RoleChangesViewModel {
    const rawEvents = Array.isArray(raw.recentEvents) ? raw.recentEvents : [];
    const adaptedEvents: RoleChangeEventViewModel[] = rawEvents.map((event) =>
      this.adaptRoleChangeEvent(event),
    );

    const totalCount = adaptedEvents.length;
    const blockedCount = adaptedEvents.filter((e) => e.isBlocked).length;
    const hasPending = blockedCount > 0;

    const label =
      totalCount === 0
        ? 'Nenhuma alteração recente'
        : totalCount === 1
          ? '1 alteração registrada'
          : `${totalCount} alterações registradas`;

    return Object.freeze({
      events: Object.freeze(adaptedEvents),
      totalCount,
      blockedCount,
      hasPending,
      label,
    });
  }

  private adaptRoleChangeEvent(event: RoleChangeEventDto): RoleChangeEventViewModel {
    const occurredAt = event.occurredAt ?? '';
    const isBlocked =
      event.outcome === 'BLOQUEADO' ||
      event.outcome === 'BLOCKED' ||
      event.action === 'AUTOCONCESSAO_PAPEL' ||
      event.action === 'REVOGACAO_ULTIMO_ADMINISTRADOR' ||
      event.action === 'ULTIMO_ADMINISTRADOR_GERAL' ||
      (typeof event.action === 'string' && event.action.toUpperCase().includes('BLOQUEAD'));

    return {
      eventId: event.eventId,
      occurredAt,
      formattedOccurredAt: this.formatDateTime(occurredAt),
      actorIdentityId: event.actorIdentityId ?? null,
      actorRole: event.actorRole ?? null,
      targetIdentityId: event.targetIdentityId ?? null,
      targetNickname: event.targetNickname ?? null,
      action: event.action,
      actionLabel: this.mapRoleActionLabel(event.action),
      outcome: event.outcome,
      isBlocked,
      hasReason: Boolean(event.hasReason),
    };
  }

  private adaptUnregisteredEnrollments(
    raw: UnregisteredEnrollmentsBlock,
  ): UnregisteredEnrollmentsViewModel {
    const count = typeof raw.count === 'number' ? raw.count : 0;
    const hasPending = count > 0;
    const label = count === 1 ? '1 conta sem matrícula' : `${count} contas sem matrícula`;

    return Object.freeze({
      count,
      hasPending,
      label,
    });
  }

  private mapRoleActionLabel(action: string): string {
    if (!action) {
      return 'Alteração de papel';
    }
    switch (action) {
      case 'PAPEL_CONCEDIDO':
        return 'Papel concedido';
      case 'PAPEL_REVOGADO':
        return 'Papel revogado';
      case 'AUTOCONCESSAO_PAPEL':
        return 'Autoconcessão de papel';
      case 'AUTOCONCESSAO_BLOQUEADA':
        return 'Autoconcessão bloqueada';
      case 'ULTIMO_ADMINISTRADOR_GERAL':
        return 'Último administrador geral';
      case 'REVOGACAO_ULTIMO_ADMINISTRADOR':
        return 'Revogação do último administrador';
      case 'ULTIMO_ADMIN_BLOQUEADO':
        return 'Último administrador bloqueado';
      default:
        return action
          .replace(/_/gu, ' ')
          .toLowerCase()
          .replace(/^\w/u, (c) => c.toUpperCase());
    }
  }

  private formatTime(isoString: string): string {
    if (!isoString) {
      return 'N/D';
    }
    try {
      const date = new Date(isoString);
      if (Number.isNaN(date.getTime())) {
        return isoString;
      }
      return date.toLocaleTimeString('pt-BR', {
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return isoString;
    }
  }

  private formatDateTime(isoString: string): string {
    if (!isoString) {
      return 'N/D';
    }
    try {
      const date = new Date(isoString);
      if (Number.isNaN(date.getTime())) {
        return isoString;
      }
      return date.toLocaleString('pt-BR', {
        dateStyle: 'short',
        timeStyle: 'medium',
      });
    } catch {
      return isoString;
    }
  }

  /**
   * Consulta o estado dos papéis operacionais de uma identidade alvo no BFF.
   * Utiliza exclusivamente o RoleService gerado.
   */
  getRoles(
    targetIdentityId: string,
    actorIdentityId?: string | null,
  ): Observable<TargetAccountRolesViewModel | null> {
    return defer(() => this.roleService.getRoles({ targetIdentityId })).pipe(
      map((response) => this.adaptRoleGrantResponse(response, actorIdentityId)),
    );
  }

  /**
   * Concede o papel de Administrador Geral a uma conta alvo.
   * Conforme D-48 e UI-SPEC S5, apenas ADMINISTRADOR_GERAL é acionável na Fase 2 e nunca envia scopeRef.
   */
  grantRole(
    targetIdentityId: string,
    role: typeof OperationalRole.AdministradorGeral,
    actorIdentityId?: string | null,
  ): Observable<TargetAccountRolesViewModel> {
    return defer(() =>
      this.roleService.grantRole({
        roleGrantRequest: {
          targetIdentityId,
          role,
        },
      }),
    ).pipe(map((response) => this.adaptRoleGrantResponse(response, actorIdentityId)!));
  }

  /**
   * Revoga o papel de Administrador Geral de uma conta alvo.
   * Conforme D-48 e UI-SPEC S5, apenas ADMINISTRADOR_GERAL é acionável na Fase 2.
   */
  revokeRole(
    targetIdentityId: string,
    role: typeof OperationalRole.AdministradorGeral,
    actorIdentityId?: string | null,
  ): Observable<TargetAccountRolesViewModel> {
    return defer(() =>
      this.roleService.revokeRole({
        roleGrantRequest: {
          targetIdentityId,
          role,
        },
      }),
    ).pipe(map((response) => this.adaptRoleGrantResponse(response, actorIdentityId)!));
  }

  /**
   * Adapta o payload RoleGrantResponse para TargetAccountRolesViewModel,
   * sempre gerando as 3 linhas fixas na ordem canônica e calculando guardas D-48.
   */
  adaptRoleGrantResponse(
    response: RoleGrantResponse | null | undefined,
    actorIdentityId?: string | null,
  ): TargetAccountRolesViewModel | null {
    if (!response) {
      return null;
    }

    const targetIdentityId = response.identityId;
    const targetNickname = response.nickname ?? null;
    const rawRoles = Array.isArray(response.roles) ? response.roles : [];

    const hasActorContext = Boolean(actorIdentityId);
    const isSelfTarget = Boolean(actorIdentityId && actorIdentityId === targetIdentityId);

    // 1. ADMINISTRADOR_GERAL
    const adminGeralStatusDto = rawRoles.find((r) => r.role === OperationalRole.AdministradorGeral);
    const isAdminGeralVigente = adminGeralStatusDto?.status === 'VIGENTE';

    const selfGrantBlocked = !isAdminGeralVigente && (!hasActorContext || isSelfTarget);
    const selfGrantBlockedReason = selfGrantBlocked
      ? isSelfTarget
        ? 'Concessão a si mesmo bloqueada. Peça a outro administrador geral.'
        : 'Não foi possível confirmar sua identidade. Recarregue a página antes de conceder.'
      : null;

    const adminGeralItem: RoleItemViewModel = Object.freeze({
      role: OperationalRole.AdministradorGeral,
      roleName: 'Administrador geral',
      status: isAdminGeralVigente ? 'VIGENTE' : 'NAO_CONCEDIDO',
      statusLabel: isAdminGeralVigente ? 'Vigente' : 'Não concedido',
      isActionable: true,
      isVigente: isAdminGeralVigente,
      unavailableReason: null,
      selfGrantBlocked,
      selfGrantBlockedReason,
    });

    // 2. ADMINISTRADOR_COMPETICAO
    const compAdminStatusDto = rawRoles.find(
      (r) => r.role === OperationalRole.AdministradorCompeticao,
    );
    const isCompAdminVigente = compAdminStatusDto?.status === 'VIGENTE';
    const compAdminItem: RoleItemViewModel = Object.freeze({
      role: OperationalRole.AdministradorCompeticao,
      roleName: 'Administrador de competição',
      status: 'INDISPONIVEL',
      statusLabel: 'Indisponível nesta fase',
      isActionable: false,
      isVigente: isCompAdminVigente,
      unavailableReason:
        'Disponível na Fase 3, quando o catálogo de competições fornecer escopos válidos.',
      selfGrantBlocked: false,
      selfGrantBlockedReason: null,
    });

    // 3. MODERADOR
    const moderadorStatusDto = rawRoles.find((r) => r.role === OperationalRole.Moderador);
    const isModeradorVigente = moderadorStatusDto?.status === 'VIGENTE';
    const moderadorItem: RoleItemViewModel = Object.freeze({
      role: OperationalRole.Moderador,
      roleName: 'Moderador',
      status: 'INDISPONIVEL',
      statusLabel: 'Indisponível nesta fase',
      isActionable: false,
      isVigente: isModeradorVigente,
      unavailableReason: 'Disponível na Fase 7, quando o escopo de moderação estiver definido.',
      selfGrantBlocked: false,
      selfGrantBlockedReason: null,
    });

    const roles: readonly RoleItemViewModel[] = Object.freeze([
      adminGeralItem,
      compAdminItem,
      moderadorItem,
    ]);

    const hasAnyGrantedRole = isAdminGeralVigente || isCompAdminVigente || isModeradorVigente;

    return Object.freeze({
      targetIdentityId,
      targetNickname,
      roles,
      hasAnyGrantedRole,
    });
  }
}
