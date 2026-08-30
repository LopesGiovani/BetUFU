import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, defer, map } from 'rxjs';
import { AthleteLinkService } from '../../../../api/generated/api/athlete-link.service';
import { AthleteLinkReviewPage } from '../../../../api/generated/model/athlete-link-review-page';
import { AthleteLinkReviewSummary } from '../../../../api/generated/model/athlete-link-review-summary';

@Injectable({ providedIn: 'root' })
export class AthleteLinkReviewApiAdapter {
  private readonly service = inject(AthleteLinkService);

  fetchReviews(page = 0, size = 20): Observable<readonly AthleteLinkReviewSummary[]> {
    return defer(() => this.service.listAthleteLinkReviews({ page, size })).pipe(
      map((response) => this.adaptPage(response)),
    );
  }

  isUnauthorized(error: unknown): boolean {
    return error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403);
  }

  private adaptPage(response: AthleteLinkReviewPage | null | undefined): readonly AthleteLinkReviewSummary[] {
    if (!response || !Array.isArray(response.items) || typeof response.page !== 'number'
      || typeof response.size !== 'number' || typeof response.totalElements !== 'number'
      || typeof response.totalPages !== 'number' || typeof response.hasNext !== 'boolean') {
      throw new Error('CONTRATO_REVISOES_VINCULO_INVALIDO: pagina ausente ou malformada.');
    }
    return Object.freeze(response.items.map((item) => this.requireSummary(item)));
  }

  private requireSummary(value: AthleteLinkReviewSummary): AthleteLinkReviewSummary {
    if (!value || typeof value.reviewId !== 'string' || typeof value.reasonCode !== 'string'
      || typeof value.status !== 'string' || typeof value.createdAt !== 'string') {
      throw new Error('CONTRATO_REVISOES_VINCULO_INVALIDO: item incompleto.');
    }
    return value;
  }
}
