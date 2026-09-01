import { Injectable, inject } from '@angular/core';
import { Observable, defer, map, throwError } from 'rxjs';
import { SportsPublicService } from '../../../api/generated/api/sports-public.service';
import { PublicAthleteDetail } from '../../../api/generated/model/public-athlete-detail';
import { PublicCompetitionDetail } from '../../../api/generated/model/public-competition-detail';
import { PublicEventDetail } from '../../../api/generated/model/public-event-detail';
import { PublicOrganizationDetail } from '../../../api/generated/model/public-organization-detail';
import { PublicTeamDetail } from '../../../api/generated/model/public-team-detail';

export type PublicDetailKind = 'competition' | 'event' | 'organization' | 'team' | 'athlete';
export type PublicDetail =
  | PublicCompetitionDetail
  | PublicEventDetail
  | PublicOrganizationDetail
  | PublicTeamDetail
  | PublicAthleteDetail;

@Injectable({ providedIn: 'root' })
export class SportsPublicViewApiAdapter {
  private readonly service = inject(SportsPublicService);

  fetch(kind: PublicDetailKind, id: string): Observable<PublicDetail> {
    const normalizedId = id.trim();
    if (!normalizedId) {
      return throwError(() => new Error('CONTRATO_PUBLICO_INVALIDO: identificador ausente.'));
    }

    return defer(() => {
      switch (kind) {
        case 'competition':
          return this.service.getPublicCompetitionDetail({ competitionId: normalizedId });
        case 'event':
          return this.service.getPublishedSportsEvent({ eventId: normalizedId });
        case 'organization':
          return this.service.getPublicOrganizationDetail({ organizationId: normalizedId });
        case 'team':
          return this.service.getPublicTeamDetail({ teamId: normalizedId });
        case 'athlete':
          return this.service.getPublicAthleteDetail({ athleteId: normalizedId });
      }
    }).pipe(map((data) => this.validate(kind, data as PublicDetail, normalizedId)));
  }

  private validate(kind: PublicDetailKind, data: PublicDetail, id: string): PublicDetail {
    if (!data || typeof data !== 'object') {
      throw new Error('CONTRATO_PUBLICO_INVALIDO: resposta ausente.');
    }

    const idField =
      kind === 'competition'
        ? 'competitionId'
        : kind === 'event'
          ? 'eventId'
          : kind === 'organization'
            ? 'organizationId'
            : kind === 'team'
              ? 'teamId'
              : 'athleteId';
    const actualId = (data as unknown as Record<string, unknown>)[idField];
    if (typeof actualId !== 'string' || actualId !== id) {
      throw new Error('CONTRATO_PUBLICO_INVALIDO: identificador da resposta diverge da rota.');
    }

    const collectionFields =
      kind === 'competition'
        ? ['editions', 'categories', 'phases', 'venues', 'teams', 'recentEvents']
        : kind === 'event'
          ? ['slots', 'sourceHistory']
          : kind === 'organization'
            ? ['teams', 'rosterMembers', 'events']
            : kind === 'team'
              ? ['events']
              : ['teams', 'events'];
    for (const field of collectionFields) {
      if (!Array.isArray((data as unknown as Record<string, unknown>)[field])) {
        throw new Error(`CONTRATO_PUBLICO_INVALIDO: coleção ${field} ausente.`);
      }
    }
    return data;
  }
}
