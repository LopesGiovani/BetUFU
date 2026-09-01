import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AthleteLinkReviewSummary } from '../../../../api/generated/model/athlete-link-review-summary';
import { AthleteLinkReviewApiAdapter } from './athlete-link-review-api.adapter';
import { AthleteLinkReviewStore } from './athlete-link-review.store';

const review: AthleteLinkReviewSummary = {
  reviewId: 'review-1',
  proposalId: 'proposal-1',
  athleteId: 'athlete-1',
  athleteName: 'Atleta Um',
  reasonCode: AthleteLinkReviewSummary.ReasonCodeEnum.Contested,
  status: AthleteLinkReviewSummary.StatusEnum.Pendente,
  createdAt: '2026-08-30T10:00:00Z',
};

type ReviewAdapterMock = {
  fetchReviews: ReturnType<typeof vi.fn>;
  isUnauthorized: ReturnType<typeof vi.fn>;
};

describe('AthleteLinkReviewStore', () => {
  let adapter: ReviewAdapterMock;
  beforeEach(() => {
    adapter = {
      fetchReviews: vi.fn(() => of([review])),
      isUnauthorized: vi.fn(
        (error: unknown) =>
          error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403),
      ),
    };
    TestBed.configureTestingModule({
      providers: [
        AthleteLinkReviewStore,
        { provide: AthleteLinkReviewApiAdapter, useValue: adapter },
      ],
    });
  });

  it('carrega a fila, abre detalhe por item autorizado e fecha sem mutar a lista', async () => {
    const store = TestBed.inject(AthleteLinkReviewStore);
    await store.load();
    expect(store.reviews()).toHaveLength(1);
    expect(store.openReview('review-1')).toBe(true);
    expect(store.selectedReview()?.reviewId).toBe('review-1');
    store.closeReview();
    expect(store.selectedReview()).toBeNull();
  });

  it('401 limpa fila e detalhe e não repete a leitura autorizada', async () => {
    const store = TestBed.inject(AthleteLinkReviewStore);
    await store.load();
    store.openReview('review-1');
    adapter.fetchReviews.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 401 })));
    await store.load();
    expect(store.reviews()).toEqual([]);
    expect(store.selectedReview()).toBeNull();
    expect(store.errorMessage()).toContain('sessão expirou');
    expect(adapter.fetchReviews).toHaveBeenCalledTimes(2);
  });
});
