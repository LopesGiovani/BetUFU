import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, defer, throwError } from 'rxjs';
import { SportsStandingsService } from '../../../api/generated/api/sports-standings.service';
import { SportsStandingsSnapshot } from '../../../api/generated/model/sports-standings-snapshot';

@Injectable({ providedIn: 'root' })
export class PublicStandingsApiAdapter {
  private readonly service = inject(SportsStandingsService);

  fetch(standingsId: string): Observable<SportsStandingsSnapshot> {
    const normalized = standingsId.trim();
    if (!normalized) return throwError(() => new Error('CONTRATO_STANDINGS_INVALIDO: identificador ausente.'));
    return defer(() => this.service.getPublicSportsStandings({ standingsId: normalized }));
  }

  isNotFound(error: unknown): boolean {
    return error instanceof HttpErrorResponse && error.status === 404;
  }
}
