import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, defer, map } from 'rxjs';
import {
  CorrectSportsRosterRequestParams,
  GetSportsRosterDraftRequestParams,
  GetSportsRosterPublicationRequestParams,
  PublishSportsRosterRequestParams,
  SportsRosterService,
  UpdateSportsRosterDraftRequestParams,
} from '../../../../api/generated/api/sports-roster.service';
import { SportsRosterCorrectRequest } from '../../../../api/generated/model/sports-roster-correct-request';
import { SportsRosterDraft } from '../../../../api/generated/model/sports-roster-draft';
import { SportsRosterMemberItem } from '../../../../api/generated/model/sports-roster-member-item';
import { SportsRosterMemberRequest } from '../../../../api/generated/model/sports-roster-member-request';
import { SportsRosterPublication } from '../../../../api/generated/model/sports-roster-publication';
import { SportsRosterUpdateRequest } from '../../../../api/generated/model/sports-roster-update-request';

@Injectable({ providedIn: 'root' })
export class SportsRostersApiAdapter {
  private readonly service = inject(SportsRosterService);

  fetchDraft(teamId: string): Observable<SportsRosterDraft> {
    const request: GetSportsRosterDraftRequestParams = { teamId };
    return defer(() => this.service.getSportsRosterDraft(request)).pipe(
      map((response) => this.requireDraft(response)),
    );
  }

  fetchPublication(teamId: string): Observable<SportsRosterPublication> {
    const request: GetSportsRosterPublicationRequestParams = { teamId };
    return defer(() => this.service.getSportsRosterPublication(request)).pipe(
      map((response) => this.requirePublication(response)),
    );
  }

  updateDraft(
    teamId: string,
    expectedLockVersion: number,
    members: readonly SportsRosterMemberRequest[],
  ): Observable<SportsRosterDraft> {
    const request: UpdateSportsRosterDraftRequestParams = {
      teamId,
      expectedLockVersion,
      sportsRosterUpdateRequest: { members: [...members] } satisfies SportsRosterUpdateRequest,
    };
    return defer(() => this.service.updateSportsRosterDraft(request)).pipe(
      map((response) => this.requireDraft(response)),
    );
  }

  publish(teamId: string, expectedLockVersion: number): Observable<SportsRosterPublication> {
    const request: PublishSportsRosterRequestParams = { teamId, expectedLockVersion };
    return defer(() => this.service.publishSportsRoster(request)).pipe(
      map((response) => this.requirePublication(response)),
    );
  }

  correct(
    teamId: string,
    expectedLockVersion: number,
    reason: string,
    members: readonly SportsRosterMemberRequest[],
  ): Observable<SportsRosterPublication> {
    const body: SportsRosterCorrectRequest = { reason, members: [...members] };
    const request: CorrectSportsRosterRequestParams = {
      teamId,
      expectedLockVersion,
      sportsRosterCorrectRequest: body,
    };
    return defer(() => this.service.correctSportsRoster(request)).pipe(
      map((response) => this.requirePublication(response)),
    );
  }

  isConflict(error: unknown): boolean {
    return error instanceof HttpErrorResponse && error.status === 409;
  }

  isUnauthorized(error: unknown): boolean {
    return error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403);
  }

  requireDraft(value: unknown): SportsRosterDraft {
    if (!value || typeof value !== 'object') {
      throw new Error('CONTRATO_ELENCO_INVALIDO: rascunho ausente.');
    }
    const draft = value as Partial<SportsRosterDraft>;
    if (
      typeof draft.rosterId !== 'string' ||
      typeof draft.teamId !== 'string' ||
      typeof draft.teamName !== 'string' ||
      typeof draft.competitionName !== 'string' ||
      (draft.status !== 'RASCUNHO' && draft.status !== 'PUBLICADO') ||
      typeof draft.lockVersion !== 'number' ||
      !Array.isArray(draft.members)
    ) {
      throw new Error('CONTRATO_ELENCO_INVALIDO: rascunho incompleto.');
    }
    return {
      ...draft,
      members: draft.members.map((member) => this.requireMember(member)),
    } as SportsRosterDraft;
  }

  requirePublication(value: unknown): SportsRosterPublication {
    if (!value || typeof value !== 'object') {
      throw new Error('CONTRATO_ELENCO_INVALIDO: snapshot ausente.');
    }
    const publication = value as Partial<SportsRosterPublication>;
    if (
      typeof publication.publicationId !== 'string' ||
      typeof publication.rosterId !== 'string' ||
      typeof publication.teamId !== 'string' ||
      typeof publication.teamName !== 'string' ||
      typeof publication.competitionName !== 'string' ||
      typeof publication.versionNo !== 'number' ||
      typeof publication.publishedAt !== 'string' ||
      typeof publication.baseLockVersion !== 'number' ||
      !Array.isArray(publication.members)
    ) {
      throw new Error('CONTRATO_ELENCO_INVALIDO: snapshot incompleto.');
    }
    return {
      ...publication,
      members: publication.members.map((member) => this.requireMember(member)),
    } as SportsRosterPublication;
  }

  private requireMember(value: unknown): SportsRosterMemberItem {
    if (!value || typeof value !== 'object') {
      throw new Error('CONTRATO_ELENCO_INVALIDO: membro ausente.');
    }
    const member = value as Partial<SportsRosterMemberItem>;
    if (
      typeof member.athleteId !== 'string' ||
      typeof member.athleteName !== 'string' ||
      typeof member.athleteSlug !== 'string' ||
      (member.jerseyNumber !== null &&
        member.jerseyNumber !== undefined &&
        typeof member.jerseyNumber !== 'number') ||
      (member.positionName !== null &&
        member.positionName !== undefined &&
        typeof member.positionName !== 'string')
    ) {
      throw new Error('CONTRATO_ELENCO_INVALIDO: membro incompleto.');
    }
    return member as SportsRosterMemberItem;
  }
}
