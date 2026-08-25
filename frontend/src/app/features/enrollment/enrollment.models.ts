/**
 * Modelos de View Model e Estado para a Matrícula UFU (Superfície S3).
 * Alinhado rigorosamente a 02-UI-SPEC.md, VERI-06, VERI-07, PRIV-01, D-16, D-25, D-26 e D-32.
 */

export type EnrollmentStateStatus =
  | 'initial'
  | 'loading'
  | 'empty'
  | 'populated'
  | 'partial'
  | 'error';

export interface EnrollmentViewModel {
  readonly status: string;
  readonly createdAt: string | null;
}

export interface EnrollmentState {
  readonly status: EnrollmentStateStatus;
  readonly enrollment: EnrollmentViewModel | null;
  readonly previousEnrollment: EnrollmentViewModel | null;
  readonly errorMessage: string | null;
  readonly errorCorrelationId: string | null;
  readonly isSaving: boolean;
  readonly isRetrying: boolean;
}
