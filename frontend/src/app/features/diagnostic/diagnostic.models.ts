/**
 * Modelos de View Model e Estado para o Diagnóstico Técnico da BETUFU.
 * Alinhado rigorosamente a 01-UI-SPEC.md, D-03, D-04, D-14, D-16 e D-17.
 */

export type DiagnosticStateStatus =
  'initial' | 'loading' | 'empty' | 'populated' | 'partial' | 'error';

export type ComponentHealthStatus = 'UP' | 'DEGRADED' | 'DOWN';

export const CANONICAL_COMPONENT_NAMES = Object.freeze([
  'API',
  'Sessão BFF',
  'Keycloak',
  'PostgreSQL',
  'Redis',
  'Armazenamento de objetos',
] as const);

export type CanonicalComponentName = (typeof CANONICAL_COMPONENT_NAMES)[number];

export interface DiagnosticCardViewModel {
  readonly name: CanonicalComponentName;
  readonly status: ComponentHealthStatus;
  readonly statusLabel: string;
  readonly statusDescription: string;
  readonly iconName: string;
  readonly detail: string | null;
  readonly durationMs: number | null;
}

export interface DiagnosticSnapshotViewModel {
  readonly status: ComponentHealthStatus;
  readonly summary: string;
  readonly correlationId: string;
  readonly timestamp: string;
  readonly components: readonly DiagnosticCardViewModel[];
  readonly environment: string | null;
  readonly version: string | null;
}

export interface DiagnosticState {
  readonly status: DiagnosticStateStatus;
  readonly snapshot: DiagnosticSnapshotViewModel | null;
  readonly previousSnapshot: DiagnosticSnapshotViewModel | null;
  readonly errorMessage: string | null;
  readonly errorCorrelationId: string | null;
  readonly isRetrying: boolean;
}
