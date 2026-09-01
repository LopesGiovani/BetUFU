import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, defer, throwError } from 'rxjs';
import { SportsBracketService } from '../../../../api/generated/api/sports-bracket.service';
import { SportsBracketView } from '../../../../api/generated/model/sports-bracket-view';
import { SportsBracketUpdateRequest } from '../../../../api/generated/model/sports-bracket-update-request';
import { SportsEventResultService } from '../../../../api/generated/api/sports-event-result.service';
import { SportsEventDetail } from '../../../../api/generated/model/sports-event-detail';

@Injectable({ providedIn: 'root' })
export class SportsBracketApiAdapter {
  private readonly service = inject(SportsBracketService);
  private readonly eventService = inject(SportsEventResultService);
  fetch(bracketId: string): Observable<SportsBracketView> {
    const normalized = bracketId.trim();
    if (!normalized)
      return throwError(() => new Error('CONTRATO_BRACKET_INVALIDO: identificador ausente.'));
    return defer(() => this.service.getSportsBracket({ bracketId: normalized }));
  }
  fetchEvents(editionId: string): Observable<Array<SportsEventDetail>> {
    return defer(() => this.eventService.listSportsEvents({ editionId }));
  }
  update(
    bracketId: string,
    expectedLockVersion: number,
    payload: SportsBracketUpdateRequest,
  ): Observable<SportsBracketView> {
    return defer(() =>
      this.service.updateSportsBracket({
        bracketId,
        expectedLockVersion,
        sportsBracketUpdateRequest: payload,
      }),
    );
  }
  publish(
    bracketId: string,
    expectedLockVersion: number,
    sourceReference: string,
  ): Observable<SportsBracketView> {
    return defer(() =>
      this.service.publishSportsBracket({
        bracketId,
        expectedLockVersion,
        sportsBracketPublishRequest: { sourceReference },
      }),
    );
  }
  correct(
    bracketId: string,
    expectedLockVersion: number,
    reason: string,
    sourceReference: string,
  ): Observable<SportsBracketView> {
    return defer(() =>
      this.service.correctSportsBracket({
        bracketId,
        expectedLockVersion,
        sportsBracketCorrectRequest: { reason, sourceReference },
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
