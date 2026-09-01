import { ComponentFixture, TestBed } from '@angular/core/testing';
import { signal } from '@angular/core';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AthleteLinkReviewSummary } from '../../../../api/generated/model/athlete-link-review-summary';
import { AthleteLinkReviewStore } from './athlete-link-review.store';
import { AthleteLinkReviewPageComponent } from './athlete-link-review-page.component';

const review: AthleteLinkReviewSummary = {
  reviewId: 'review-1',
  proposalId: 'proposal-1',
  athleteId: 'athlete-1',
  athleteName: 'Atleta Um',
  reasonCode: AthleteLinkReviewSummary.ReasonCodeEnum.Contested,
  status: AthleteLinkReviewSummary.StatusEnum.Pendente,
  createdAt: '2026-08-30T10:00:00Z',
};

describe('AthleteLinkReviewPageComponent', () => {
  let fixture: ComponentFixture<AthleteLinkReviewPageComponent>;
  let store: Record<string, unknown>;
  beforeEach(async () => {
    store = {
      reviews: signal<readonly AthleteLinkReviewSummary[]>([review]),
      selectedReview: signal<AthleteLinkReviewSummary | null>(null),
      isLoading: signal(false),
      isError: signal(false),
      isEmpty: signal(false),
      errorMessage: signal<string | null>(null),
      load: vi.fn().mockResolvedValue(undefined),
      openReview: vi.fn(),
      closeReview: vi.fn(),
      reasonLabel: vi.fn(() => 'Contestação'),
    };
    await TestBed.configureTestingModule({
      imports: [AthleteLinkReviewPageComponent],
      providers: [{ provide: AthleteLinkReviewStore, useValue: store }],
    }).compileComponents();
    fixture = TestBed.createComponent(AthleteLinkReviewPageComponent);
    fixture.detectChanges();
  });

  it('renderiza motivo seguro e não enumera dados privados', () => {
    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Contestação');
    expect(root.textContent).toContain('Atleta Um');
    expect(root.textContent).not.toMatch(/matr[ií]cula|hmac|@/iu);
    expect(root.textContent).not.toContain('proposal-1');
  });

  it('abre detalhe somente pela ação explícita', () => {
    const root = fixture.nativeElement as HTMLElement;
    const button = Array.from(root.querySelectorAll('button')).find((item) =>
      item.textContent?.includes('Abrir detalhe'),
    ) as HTMLButtonElement;
    button.click();
    expect(store['openReview']).toHaveBeenCalledWith('review-1');
  });

  it('não enumera nome em caso ambíguo', () => {
    const ambiguous = {
      ...review,
      reasonCode: AthleteLinkReviewSummary.ReasonCodeEnum.MultipleCandidates,
      athleteName: 'Candidato privado',
    };
    expect(fixture.componentInstance.displayName(ambiguous)).toBe('Perfil esportivo');
  });
});
