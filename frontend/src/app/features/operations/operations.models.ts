/**
 * Modelos de View Model e Estado para a Superfície S4: Painel Operacional da BETUFU.
 * Alinhado rigorosamente a 02-UI-SPEC.md §S4, OPER-06, D-14, D-16 e D-17.
 */

export type OperationsPanelStateStatus =
  | 'initial'
  | 'loading'
  | 'empty'
  | 'populated'
  | 'partial'
  | 'error';

export type UnavailableBlockKey =
  | 'ROLE_CHANGES'
  | 'REJECTED_ENROLLMENTS_COUNT'
  | 'ADMINISTRATIVE_CORRECTIONS'
  | 'UNREGISTERED_ENROLLMENTS_COUNT';

export interface TimeWindowViewModel {
  readonly since: string;
  readonly until: string;
}

export interface RejectedEnrollmentsViewModel {
  readonly duplicateCount: number;
  readonly attemptLimitCount: number;
  readonly totalCount: number;
  readonly hasPending: boolean;
  readonly label: string;
  readonly duplicateLabel: string;
  readonly attemptLimitLabel: string;
}

export interface AdministrativeCorrectionEventViewModel {
  readonly eventId: string;
  readonly occurredAt: string;
  readonly formattedOccurredAt: string;
  readonly actorIdentityId: string | null;
  readonly actorRole: string | null;
  readonly targetIdentityId: string | null;
  readonly targetNickname: string | null;
  readonly action: string;
  readonly outcome: string;
  readonly hasReason: boolean;
  readonly reason: string | null;
}

export interface AdministrativeCorrectionsViewModel {
  readonly events: readonly AdministrativeCorrectionEventViewModel[];
  readonly totalCount: number;
  readonly label: string;
}

export interface RoleChangeEventViewModel {
  readonly eventId: string;
  readonly occurredAt: string;
  readonly formattedOccurredAt: string;
  readonly actorIdentityId: string | null;
  readonly actorRole: string | null;
  readonly targetIdentityId: string | null;
  readonly targetNickname: string | null;
  readonly action: string;
  readonly actionLabel: string;
  readonly outcome: string;
  readonly isBlocked: boolean;
  readonly hasReason: boolean;
}

export interface RoleChangesViewModel {
  readonly events: readonly RoleChangeEventViewModel[];
  readonly totalCount: number;
  readonly blockedCount: number;
  readonly hasPending: boolean;
  readonly label: string;
}

export interface UnregisteredEnrollmentsViewModel {
  readonly count: number;
  readonly hasPending: boolean;
  readonly label: string;
}

export interface OperationsPanelSnapshotViewModel {
  readonly queriedAt: string;
  readonly formattedQueriedAt: string;
  readonly timeWindow: TimeWindowViewModel;
  readonly rejectedEnrollments: RejectedEnrollmentsViewModel | null;
  readonly administrativeCorrections: AdministrativeCorrectionsViewModel | null;
  readonly roleChanges: RoleChangesViewModel | null;
  readonly unregisteredEnrollments: UnregisteredEnrollmentsViewModel | null;
  readonly unavailableBlocks: readonly UnavailableBlockKey[];
  readonly isRejectedEnrollmentsUnavailable: boolean;
  readonly isAdministrativeCorrectionsUnavailable: boolean;
  readonly isRoleChangesUnavailable: boolean;
  readonly isUnregisteredEnrollmentsUnavailable: boolean;
  readonly unavailableBlockMessage: string;
  readonly hasAnyBlock: boolean;
  readonly hasAnyPending: boolean;
}

export interface OperationsPanelState {
  readonly status: OperationsPanelStateStatus;
  readonly snapshot: OperationsPanelSnapshotViewModel | null;
  readonly previousSnapshot: OperationsPanelSnapshotViewModel | null;
  readonly errorMessage: string | null;
  readonly errorCorrelationId: string | null;
  readonly isRetrying: boolean;
  readonly isStale: boolean;
}
