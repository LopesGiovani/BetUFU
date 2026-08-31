import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, defer, throwError } from 'rxjs';
import { SportsBracketService } from '../../../api/generated/api/sports-bracket.service';
import { SportsBracketView } from '../../../api/generated/model/sports-bracket-view';

@Injectable({ providedIn: 'root' })
export class PublicBracketApiAdapter {
  private readonly service = inject(SportsBracketService);

  fetch(bracketId: string): Observable<SportsBracketView> {
    const normalized = bracketId.trim();
    if (!normalized) return throwError(() => new Error('CONTRATO_BRACKET_INVALIDO: identificador ausente.'));
    return defer(() => this.service.getPublicSportsBracket({ bracketId: normalized }));
  }

  isNotFound(error: unknown): boolean { return error instanceof HttpErrorResponse && error.status === 404; }
}
