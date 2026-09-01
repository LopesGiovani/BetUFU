import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom, retry, timer } from 'rxjs';
import { AthleteLinkReviewSummary } from '../../../../api/generated/model/athlete-link-review-summary';
import { AthleteLinkReviewApiAdapter } from './athlete-link-review-api.adapter';

export type AthleteLinkReviewStatus = 'initial' | 'loading' | 'ready' | 'empty' | 'error';

interface ReviewState {
  status: AthleteLinkReviewStatus;
  reviews: readonly AthleteLinkReviewSummary[];
  selectedReview: AthleteLinkReviewSummary | null;
  errorMessage: string | null;
  isRetrying: boolean;
}

const INITIAL_STATE: ReviewState = Object.freeze({
  status: 'initial',
  reviews: Object.freeze([]),
  selectedReview: null,
  errorMessage: null,
  isRetrying: false,
});

@Injectable({ providedIn: 'root' })
export class AthleteLinkReviewStore {
  private readonly adapter = inject(AthleteLinkReviewApiAdapter);
  private readonly state = signal<ReviewState>(INITIAL_STATE);
  private requestSequence = 0;

  readonly status = computed(() => this.state().status);
  readonly reviews = computed(() => this.state().reviews);
  readonly selectedReview = computed(() => this.state().selectedReview);
  readonly errorMessage = computed(() => this.state().errorMessage);
  readonly isRetrying = computed(() => this.state().isRetrying);
  readonly isLoading = computed(() => this.status() === 'loading');
  readonly isReady = computed(() => this.status() === 'ready');
  readonly isEmpty = computed(() => this.status() === 'empty');
  readonly isError = computed(() => this.status() === 'error');

  async load(): Promise<void> {
    const requestId = ++this.requestSequence;
    this.state.update((current) => ({
      ...current,
      status: 'loading',
      errorMessage: null,
      isRetrying: false,
      selectedReview: null,
    }));
    try {
      const reviews = await firstValueFrom(
        this.adapter.fetchReviews().pipe(
          retry({
            count: 2,
            delay: (error: unknown, retryCount) => {
              if (this.adapter.isUnauthorized(error)) throw error;
              this.state.update((current) => ({ ...current, isRetrying: true }));
              return timer(retryCount === 1 ? 250 : 500);
            },
          }),
        ),
      );
      if (requestId !== this.requestSequence) return;
      this.state.set({
        ...INITIAL_STATE,
        status: reviews.length === 0 ? 'empty' : 'ready',
        reviews,
      });
    } catch (error: unknown) {
      if (requestId !== this.requestSequence) return;
      this.state.set({
        ...INITIAL_STATE,
        status: 'error',
        errorMessage: this.sanitizeError(error),
      });
    }
  }

  openReview(reviewId: string): boolean {
    const review = this.reviews().find((item) => item.reviewId === reviewId);
    if (!review) return false;
    this.state.update((current) => ({ ...current, selectedReview: review }));
    return true;
  }

  closeReview(): void {
    this.state.update((current) => ({ ...current, selectedReview: null }));
  }

  reset(): void {
    this.requestSequence += 1;
    this.state.set(INITIAL_STATE);
  }

  reasonLabel(reasonCode: AthleteLinkReviewSummary.ReasonCodeEnum): string {
    return (
      (
        {
          DIVERGENT_DATA: 'Ambiguidade',
          MULTIPLE_CANDIDATES: 'Ambiguidade',
          ALREADY_LINKED: 'Já vinculado',
          CONTESTED: 'Contestação',
        } as Record<string, string>
      )[reasonCode] ?? 'Revisão de vínculo'
    );
  }

  private sanitizeError(error: unknown): string {
    if (error instanceof HttpErrorResponse && error.status === 401)
      return 'Sua sessão expirou. Entre novamente para continuar.';
    if (error instanceof HttpErrorResponse && error.status === 403)
      return 'Você não tem acesso a esta área.';
    if (error instanceof Error && error.message.startsWith('CONTRATO_'))
      return 'Dados de revisão inválidos recebidos da plataforma.';
    return 'Não foi possível carregar a fila de revisão. Tente novamente.';
  }
}
