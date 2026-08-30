import { ChangeDetectionStrategy, Component, OnInit, inject } from '@angular/core';
import { AthleteLinkReviewStore } from './athlete-link-review.store';

@Component({
  selector: 'app-athlete-link-review-page',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="link-review-page" aria-labelledby="link-review-title">
      <header><span class="eyebrow">Console operacional · O8</span><h1 id="link-review-title">Revisões de vínculo</h1><p>Casos que precisam de análise manual autorizada.</p></header>
      @if (store.isLoading() && store.reviews().length === 0) {
        <article class="state-card" role="status"><h2>Carregando revisões...</h2><p>Aguarde enquanto consultamos a fila.</p></article>
      } @else if (store.isError()) {
        <article class="state-card state-card--error" role="alert"><h2>Não foi possível carregar a fila</h2><p>{{ store.errorMessage() }}</p><button type="button" class="button button--primary" (click)="retry()">Tentar novamente</button></article>
      } @else if (store.isEmpty()) {
        <article class="state-card" role="status"><h2>Nenhum vínculo aguardando revisão</h2><p>Não há propostas contestadas ou ambíguas neste escopo.</p></article>
      } @else {
        <ul class="review-list" aria-label="Casos de revisão">
          @for (review of store.reviews(); track review.reviewId) {
            <li class="review-row">
              <div><strong>{{ displayName(review) }}</strong><span>{{ store.reasonLabel(review.reasonCode) }} · {{ review.status }}</span></div>
              <button type="button" class="button button--secondary" (click)="openReview(review.reviewId)">Abrir detalhe</button>
            </li>
          }
        </ul>
        @if (store.selectedReview(); as review) {
          <article class="detail-card" aria-labelledby="review-detail-title">
            <span class="card-code">DETALHE AUDITADO</span><h2 id="review-detail-title">{{ displayName(review) }}</h2>
            <p><strong>Motivo:</strong> {{ store.reasonLabel(review.reasonCode) }}</p><p><strong>Estado:</strong> {{ review.status }}</p>
            <p class="safe-note">A fila não exibe matrícula, HMAC, e-mail ou candidatos concorrentes. Nenhuma associação manual é criada por esta tela.</p>
            <button type="button" class="button button--secondary" (click)="closeReview()">Fechar detalhe</button>
          </article>
        }
      }
    </section>
  `,
  styles: [`
    :host { display: block; } .link-review-page { max-width: 58rem; margin: 0 auto; padding: 2rem 1rem; color: #251d1d; }
    header { margin-bottom: 1.5rem; } .eyebrow, .card-code { color: #910008; font-size: .75rem; font-weight: 700; letter-spacing: .12em; text-transform: uppercase; }
    h1 { margin: .35rem 0; } h2 { margin: .35rem 0; } p { line-height: 1.55; } .state-card, .detail-card { border: 1px solid #e7caca; border-radius: 1rem; background: #fffaf5; padding: 1.5rem; }
    .state-card--error { border-color: #ff626b; } .review-list { display: grid; gap: .75rem; padding: 0; list-style: none; }
    .review-row { display: flex; align-items: center; justify-content: space-between; gap: 1rem; border: 1px solid #e7caca; border-radius: .8rem; background: #fffaf5; padding: 1rem; }
    .review-row div { display: grid; gap: .25rem; } .review-row span { color: #675858; } .detail-card { margin-top: 1rem; } .safe-note { border-left: 3px solid #ff626b; padding-left: 1rem; }
    .button { min-height: 2.75rem; border-radius: .6rem; padding: .7rem 1rem; cursor: pointer; font: inherit; font-weight: 700; } .button--primary { border: 0; background: #910008; color: #fff; }
    .button--secondary { border: 1px solid #910008; background: transparent; color: #910008; } @media (max-width: 599px) { .review-row { align-items: stretch; flex-direction: column; } }
  `],
})
export class AthleteLinkReviewPageComponent implements OnInit {
  readonly store = inject(AthleteLinkReviewStore);
  ngOnInit(): void { void this.store.load(); }
  retry(): void { void this.store.load(); }
  openReview(reviewId: string): void { this.store.openReview(reviewId); }
  closeReview(): void { this.store.closeReview(); }
  displayName(review: { athleteName?: string | null; reasonCode: string }): string {
    return review.reasonCode === 'DIVERGENT_DATA' || review.reasonCode === 'MULTIPLE_CANDIDATES'
      ? 'Perfil esportivo'
      : review.athleteName || 'Perfil esportivo';
  }
}
