import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, defer, map } from 'rxjs';
import {
  CancelSportsEventRequestParams,
  ConfirmSportsResultRequestParams,
  CorrectSportsResultRequestParams,
  CreateSportsEventRequestParams,
  GetSportsEventDetailRequestParams,
  InformSportsResultRequestParams,
  ListSportsEventsRequestParams,
  ListSportsResultVersionsRequestParams,
  PostponeSportsEventRequestParams,
  PublishSportsEventRequestParams,
  ReviewSportsResultRequestParams,
  SportsEventResultService,
  SuspendSportsEventRequestParams,
  UpdateSportsEventDraftRequestParams,
} from '../../../../api/generated/api/sports-event-result.service';
import { SportsEventDetail } from '../../../../api/generated/model/sports-event-detail';
import { SportsEventFormat } from '../../../../api/generated/model/sports-event-format';
import { SportsEventSlotItem } from '../../../../api/generated/model/sports-event-slot-item';
import { SportsResultScoreItem } from '../../../../api/generated/model/sports-result-score-item';
import { SportsResultVersion } from '../../../../api/generated/model/sports-result-version';
import { SportsVenueState } from '../../../../api/generated/model/sports-venue-state';

export interface SportsEventSlotRequest {
  slotNo: number;
  displayLabel: string;
  teamId?: string | null;
}

export interface SportsEventCreatePayload {
  title: string;
  format: SportsEventFormat;
  scheduledAt: string;
  zoneId: string;
  venueState: SportsVenueState;
  sourceReference: string;
  slots: readonly SportsEventSlotRequest[];
  sportId?: string | null;
  categoryId?: string | null;
  phaseId?: string | null;
  venueId?: string | null;
  venueSpaceId?: string | null;
}

export interface SportsEventUpdatePayload {
  title: string;
  format: SportsEventFormat;
  scheduledAt: string;
  zoneId: string;
  venueState: SportsVenueState;
  sourceReference: string;
  slots: readonly SportsEventSlotRequest[];
  sportId?: string | null;
  categoryId?: string | null;
  phaseId?: string | null;
  venueId?: string | null;
  venueSpaceId?: string | null;
}

export interface SportsResultScoreItemRequest {
  slotNo: number;
  score: number;
}

@Injectable({ providedIn: 'root' })
export class SportsEventResultApiAdapter {
  private readonly service = inject(SportsEventResultService);

  fetchEvents(editionId: string): Observable<readonly SportsEventDetail[]> {
    const request: ListSportsEventsRequestParams = { editionId };
    return defer(() => this.service.listSportsEvents(request)).pipe(
      map((response) => this.adaptEvents(response)),
    );
  }

  fetchEventDetail(eventId: string): Observable<SportsEventDetail> {
    const request: GetSportsEventDetailRequestParams = { eventId };
    return defer(() => this.service.getSportsEventDetail(request)).pipe(
      map((response) => this.requireEventDetail(response)),
    );
  }

  createEvent(editionId: string, payload: SportsEventCreatePayload): Observable<SportsEventDetail> {
    const request: CreateSportsEventRequestParams = {
      editionId,
      sportsEventCreateRequest: {
        title: payload.title.trim(),
        format: payload.format,
        scheduledAt: payload.scheduledAt,
        zoneId: payload.zoneId.trim(),
        venueState: payload.venueState,
        sourceReference: payload.sourceReference.trim(),
        slots: payload.slots.map((s) => ({
          slotNo: s.slotNo,
          displayLabel: s.displayLabel.trim(),
          teamId: s.teamId?.trim() ? s.teamId.trim() : undefined,
        })),
        sportId: payload.sportId?.trim() ? payload.sportId.trim() : undefined,
        categoryId: payload.categoryId?.trim() ? payload.categoryId.trim() : undefined,
        phaseId: payload.phaseId?.trim() ? payload.phaseId.trim() : undefined,
        venueId: payload.venueId?.trim() ? payload.venueId.trim() : undefined,
        venueSpaceId: payload.venueSpaceId?.trim() ? payload.venueSpaceId.trim() : undefined,
      },
    };
    return defer(() => this.service.createSportsEvent(request)).pipe(
      map((response) => this.requireEventDetail(response)),
    );
  }

  updateEventDraft(
    eventId: string,
    expectedLockVersion: number,
    payload: SportsEventUpdatePayload,
  ): Observable<SportsEventDetail> {
    const request: UpdateSportsEventDraftRequestParams = {
      eventId,
      expectedLockVersion,
      sportsEventUpdateRequest: {
        title: payload.title.trim(),
        format: payload.format,
        scheduledAt: payload.scheduledAt,
        zoneId: payload.zoneId.trim(),
        venueState: payload.venueState,
        sourceReference: payload.sourceReference.trim(),
        slots: payload.slots.map((s) => ({
          slotNo: s.slotNo,
          displayLabel: s.displayLabel.trim(),
          teamId: s.teamId?.trim() ? s.teamId.trim() : undefined,
        })),
        sportId: payload.sportId?.trim() ? payload.sportId.trim() : undefined,
        categoryId: payload.categoryId?.trim() ? payload.categoryId.trim() : undefined,
        phaseId: payload.phaseId?.trim() ? payload.phaseId.trim() : undefined,
        venueId: payload.venueId?.trim() ? payload.venueId.trim() : undefined,
        venueSpaceId: payload.venueSpaceId?.trim() ? payload.venueSpaceId.trim() : undefined,
      },
    };
    return defer(() => this.service.updateSportsEventDraft(request)).pipe(
      map((response) => this.requireEventDetail(response)),
    );
  }

  publishEvent(eventId: string, expectedLockVersion: number): Observable<SportsEventDetail> {
    const request: PublishSportsEventRequestParams = { eventId, expectedLockVersion };
    return defer(() => this.service.publishSportsEvent(request)).pipe(
      map((response) => this.requireEventDetail(response)),
    );
  }

  suspendEvent(eventId: string, expectedLockVersion: number, reason?: string | null): Observable<SportsEventDetail> {
    const request: SuspendSportsEventRequestParams = {
      eventId,
      expectedLockVersion,
      sportsEventSuspendRequest: reason?.trim() ? { reason: reason.trim() } : undefined,
    };
    return defer(() => this.service.suspendSportsEvent(request)).pipe(
      map((response) => this.requireEventDetail(response)),
    );
  }

  postponeEvent(
    eventId: string,
    expectedLockVersion: number,
    scheduledAt: string,
    zoneId: string,
    reason?: string | null,
  ): Observable<SportsEventDetail> {
    const request: PostponeSportsEventRequestParams = {
      eventId,
      expectedLockVersion,
      sportsEventPostponeRequest: {
        scheduledAt,
        zoneId: zoneId.trim(),
        reason: reason?.trim() ? reason.trim() : undefined,
      },
    };
    return defer(() => this.service.postponeSportsEvent(request)).pipe(
      map((response) => this.requireEventDetail(response)),
    );
  }

  cancelEvent(eventId: string, expectedLockVersion: number, reason: string): Observable<SportsEventDetail> {
    const request: CancelSportsEventRequestParams = {
      eventId,
      expectedLockVersion,
      sportsEventCancelRequest: { reason: reason.trim() },
    };
    return defer(() => this.service.cancelSportsEvent(request)).pipe(
      map((response) => this.requireEventDetail(response)),
    );
  }

  fetchResultVersions(eventId: string): Observable<readonly SportsResultVersion[]> {
    const request: ListSportsResultVersionsRequestParams = { eventId };
    return defer(() => this.service.listSportsResultVersions(request)).pipe(
      map((response) => this.adaptResultVersions(response)),
    );
  }

  informResult(
    eventId: string,
    expectedLockVersion: number,
    sourceReference: string,
    scores: readonly SportsResultScoreItemRequest[],
  ): Observable<SportsResultVersion> {
    const request: InformSportsResultRequestParams = {
      eventId,
      expectedLockVersion,
      sportsResultInformRequest: {
        sourceReference: sourceReference.trim(),
        scores: scores.map((s) => ({ slotNo: s.slotNo, score: s.score })),
      },
    };
    return defer(() => this.service.informSportsResult(request)).pipe(
      map((response) => this.requireResultVersion(response)),
    );
  }

  reviewResult(
    eventId: string,
    expectedLockVersion: number,
    sourceReference: string,
    scores: readonly SportsResultScoreItemRequest[],
  ): Observable<SportsResultVersion> {
    const request: ReviewSportsResultRequestParams = {
      eventId,
      expectedLockVersion,
      sportsResultReviewRequest: {
        sourceReference: sourceReference.trim(),
        scores: scores.map((s) => ({ slotNo: s.slotNo, score: s.score })),
      },
    };
    return defer(() => this.service.reviewSportsResult(request)).pipe(
      map((response) => this.requireResultVersion(response)),
    );
  }

  confirmResult(
    eventId: string,
    expectedLockVersion: number,
    sourceReference: string,
    scores: readonly SportsResultScoreItemRequest[],
  ): Observable<SportsResultVersion> {
    const request: ConfirmSportsResultRequestParams = {
      eventId,
      expectedLockVersion,
      sportsResultConfirmRequest: {
        sourceReference: sourceReference.trim(),
        scores: scores.map((s) => ({ slotNo: s.slotNo, score: s.score })),
      },
    };
    return defer(() => this.service.confirmSportsResult(request)).pipe(
      map((response) => this.requireResultVersion(response)),
    );
  }

  correctResult(
    eventId: string,
    expectedLockVersion: number,
    reason: string,
    sourceReference: string,
    scores: readonly SportsResultScoreItemRequest[],
  ): Observable<SportsResultVersion> {
    const request: CorrectSportsResultRequestParams = {
      eventId,
      expectedLockVersion,
      sportsResultCorrectRequest: {
        reason: reason.trim(),
        sourceReference: sourceReference.trim(),
        scores: scores.map((s) => ({ slotNo: s.slotNo, score: s.score })),
      },
    };
    return defer(() => this.service.correctSportsResult(request)).pipe(
      map((response) => this.requireResultVersion(response)),
    );
  }

  isConflict(error: unknown): boolean {
    return error instanceof HttpErrorResponse && error.status === 409;
  }

  isUnauthorized(error: unknown): boolean {
    return error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403);
  }

  adaptEvents(response: unknown): readonly SportsEventDetail[] {
    if (!Array.isArray(response)) {
      throw new Error('CONTRATO_EVENTOS_INVALIDO: lista de eventos ausente ou malformada.');
    }
    return response.map((item) => this.requireEventDetail(item));
  }

  adaptResultVersions(response: unknown): readonly SportsResultVersion[] {
    if (!Array.isArray(response)) {
      throw new Error('CONTRATO_RESULTADOS_INVALIDO: lista de versoes ausente ou malformada.');
    }
    return response.map((item) => this.requireResultVersion(item));
  }

  requireEventDetail(value: unknown): SportsEventDetail {
    if (!value || typeof value !== 'object') {
      throw new Error('CONTRATO_EVENTOS_INVALIDO: detalhe do evento ausente.');
    }
    const item = value as Partial<SportsEventDetail>;
    if (
      typeof item.eventId !== 'string'
      || typeof item.competitionId !== 'string'
      || typeof item.competitionName !== 'string'
      || typeof item.editionId !== 'string'
      || typeof item.editionName !== 'string'
      || typeof item.title !== 'string'
      || typeof item.format !== 'string'
      || typeof item.status !== 'string'
      || typeof item.scheduledAt !== 'string'
      || typeof item.zoneId !== 'string'
      || typeof item.venueState !== 'string'
      || typeof item.sourceReference !== 'string'
      || typeof item.lockVersion !== 'number'
      || !Array.isArray(item.slots)
    ) {
      throw new Error('CONTRATO_EVENTOS_INVALIDO: detalhe do evento incompleto.');
    }
    return {
      ...item,
      slots: item.slots.map((slot) => this.requireSlot(slot)),
    } as SportsEventDetail;
  }

  requireResultVersion(value: unknown): SportsResultVersion {
    if (!value || typeof value !== 'object') {
      throw new Error('CONTRATO_RESULTADOS_INVALIDO: versao de resultado ausente.');
    }
    const item = value as Partial<SportsResultVersion>;
    if (
      typeof item.versionId !== 'string'
      || typeof item.eventId !== 'string'
      || typeof item.competitionId !== 'string'
      || typeof item.editionId !== 'string'
      || typeof item.versionNo !== 'number'
      || typeof item.status !== 'string'
      || typeof item.sourceReference !== 'string'
      || typeof item.recordedByIdentityId !== 'string'
      || typeof item.recordedAt !== 'string'
      || typeof item.baseLockVersion !== 'number'
      || !Array.isArray(item.scores)
    ) {
      throw new Error('CONTRATO_RESULTADOS_INVALIDO: versao de resultado incompleta.');
    }
    return {
      ...item,
      scores: item.scores.map((score) => this.requireScore(score)),
    } as SportsResultVersion;
  }

  private requireSlot(value: unknown): SportsEventSlotItem {
    if (!value || typeof value !== 'object') {
      throw new Error('CONTRATO_EVENTOS_INVALIDO: slot ausente.');
    }
    const slot = value as Partial<SportsEventSlotItem>;
    if (
      typeof slot.slotNo !== 'number'
      || typeof slot.displayLabel !== 'string'
    ) {
      throw new Error('CONTRATO_EVENTOS_INVALIDO: slot incompleto.');
    }
    return slot as SportsEventSlotItem;
  }

  private requireScore(value: unknown): SportsResultScoreItem {
    if (!value || typeof value !== 'object') {
      throw new Error('CONTRATO_RESULTADOS_INVALIDO: pontuacao ausente.');
    }
    const score = value as Partial<SportsResultScoreItem>;
    if (
      typeof score.slotNo !== 'number'
      || typeof score.score !== 'number'
      || typeof score.displayLabel !== 'string'
    ) {
      throw new Error('CONTRATO_RESULTADOS_INVALIDO: pontuacao incompleta.');
    }
    return score as SportsResultScoreItem;
  }
}
