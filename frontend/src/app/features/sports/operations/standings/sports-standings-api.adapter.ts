import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, defer, throwError } from 'rxjs';
import { SportsStandingsService } from '../../../../api/generated/api/sports-standings.service';
import { SportsStandingsSnapshot } from '../../../../api/generated/model/sports-standings-snapshot';
import { SportsStandingsRow } from '../../../../api/generated/model/sports-standings-row';

export interface SportsStandingsUpdatePayload {
  name: string;
  slug: string;
  sportId?: string;
  categoryId?: string;
  phaseId?: string;
  columns: Array<string>;
  rows: Array<SportsStandingsRow>;
}

@Injectable({ providedIn: 'root' })
export class SportsStandingsApiAdapter {
  private readonly service = inject(SportsStandingsService);

  fetch(standingsId: string): Observable<SportsStandingsSnapshot> {
    const normalized = standingsId.trim();
    if (!normalized)
      return throwError(() => new Error('CONTRATO_STANDINGS_INVALIDO: identificador ausente.'));
    return defer(() => this.service.getSportsStandings({ standingsId: normalized }));
  }

  update(
    standingsId: string,
    expectedLockVersion: number,
    payload: SportsStandingsUpdatePayload,
  ): Observable<SportsStandingsSnapshot> {
    return defer(() =>
      this.service.updateSportsStandings({
        standingsId,
        expectedLockVersion,
        sportsStandingsUpdateRequest: payload,
      }),
    );
  }

  publish(
    standingsId: string,
    expectedLockVersion: number,
    sourceReference: string,
  ): Observable<SportsStandingsSnapshot> {
    return defer(() =>
      this.service.publishSportsStandings({
        standingsId,
        expectedLockVersion,
        sportsStandingsPublishRequest: { sourceReference },
      }),
    );
  }

  correct(
    standingsId: string,
    expectedLockVersion: number,
    reason: string,
    sourceReference: string,
    columns: Array<string>,
    rows: Array<SportsStandingsRow>,
  ): Observable<SportsStandingsSnapshot> {
    return defer(() =>
      this.service.correctSportsStandings({
        standingsId,
        expectedLockVersion,
        sportsStandingsCorrectRequest: {
          reason,
          sourceReference,
          columns,
          rows,
        },
      }),
    );
  }

  isUnauthorized(error: unknown): boolean {
    return error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403);
  }

  isNotFound(error: unknown): boolean {
    return error instanceof HttpErrorResponse && error.status === 404;
  }
}
