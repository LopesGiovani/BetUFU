import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { AthleteLinkProposal } from '../../../api/generated/model/athlete-link-proposal';
import { ConfirmDialogComponent } from '../../../shared/confirm-dialog/confirm-dialog.component';
import { AthleteLinkProposalStore } from './athlete-link-proposal.store';

type PendingDecision = 'accept' | 'reject' | 'contest' | null;

@Component({
  selector: 'app-athlete-link-proposal-page',
  standalone: true,
  imports: [ConfirmDialogComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="link-proposal-page" aria-labelledby="link-proposal-title">
      <header>
        <span class="eyebrow">Conta</span>
        <h1 id="link-proposal-title">Proposta de vínculo</h1>
        <p>Revise o perfil esportivo que poderá ser controlado pela sua conta.</p>
      </header>

      @if (store.isLoading() && !store.proposal()) {
        <article class="state-card" role="status">
          <h2>Carregando proposta...</h2>
          <p>Aguarde enquanto consultamos seu vínculo.</p>
        </article>
      } @else if (store.isError() && !store.proposal()) {
        <article class="state-card state-card--error" role="alert">
          <h2>Não foi possível carregar a proposta</h2>
          <p>{{ store.errorMessage() }}</p>
          <button type="button" class="button button--primary" (click)="retry()">
            Tentar novamente
          </button>
        </article>
      } @else if (store.isEmpty() || !store.proposal()) {
        <article class="state-card" role="status">
          <h2>Nenhuma proposta de vínculo</h2>
          <p>Não há uma proposta pendente para sua conta.</p>
        </article>
      } @else {
        @if (store.feedbackMessage()) {
          <div class="alert alert--success" role="status" aria-live="polite">
            {{ store.feedbackMessage() }}
          </div>
        }
        @if (store.errorMessage()) {
          <div class="alert alert--warning" role="alert">{{ store.errorMessage() }}</div>
        }
        <article class="proposal-card">
          <span class="card-code">PERFIL ESPORTIVO</span>
          <h2>{{ store.proposal()!.athleteName }}</h2>
          <p class="proposal-slug">{{ store.proposal()!.athleteSlug }}</p>
          <p class="notice">
            A correspondência entre os cadastros indica apenas que as informações de origem
            coincidem. Ela não comprova vínculo oficial com a UFU nem titularidade.
          </p>
          <p class="proposal-status">
            Estado: <strong>{{ statusLabel(store.proposal()!.status) }}</strong>
          </p>
          <div class="actions">
            @if (store.proposal()!.status === 'PENDENTE') {
              <button
                type="button"
                class="button button--primary"
                (click)="requestDecision('accept', $event)"
                [disabled]="store.isMutating()">
                Aceitar vínculo
              </button>
              <button
                type="button"
                class="button button--danger"
                (click)="requestDecision('reject', $event)"
                [disabled]="store.isMutating()">
                Rejeitar vínculo
              </button>
              <button
                type="button"
                class="button button--secondary"
                (click)="requestDecision('contest', $event)"
                [disabled]="store.isMutating()">
                Contestar vínculo
              </button>
            }
          </div>
        </article>
      }

      <app-confirm-dialog
        [isOpen]="dialogOpen()"
        [title]="dialogTitle()"
        [body]="dialogBody()"
        [confirmLabel]="dialogConfirmLabel()"
        [cancelLabel]="'Manter proposta'"
        [isDestructive]="pendingDecision() === 'reject'"
        [isLoading]="store.isMutating()"
        [triggerElement]="triggerElement()"
        (confirmed)="confirmDecision()"
        (cancelled)="cancelDecision()" />
    </section>
  `,
  styles: [
    `
      :host {
        display: block;
      }
      .link-proposal-page {
        max-width: 42rem;
        margin: 0 auto;
        padding: 2rem 1rem;
        color: var(--bt-text);
      }
      header {
        margin-bottom: 1.5rem;
      }
      .eyebrow,
      .card-code {
        color: var(--bt-signal-soft);
        font-size: 0.75rem;
        font-weight: 700;
        letter-spacing: 0.12em;
        text-transform: uppercase;
      }
      h1 {
        margin: 0.35rem 0;
        color: var(--bt-text);
      }
      h2 {
        margin: 0.35rem 0;
        color: var(--bt-text);
      }
      header p,
      .state-card p,
      .proposal-slug,
      .notice,
      .proposal-status {
        color: var(--bt-text-muted);
        line-height: 1.55;
      }
      .state-card,
      .proposal-card {
        border: 1px solid var(--bt-border);
        border-radius: 1rem;
        background: var(--bt-surface);
        padding: 1.5rem;
        box-shadow: var(--bt-shadow-raised);
      }
      .state-card--error,
      .alert--warning {
        border-color: var(--bt-danger);
      }
      .alert {
        margin-bottom: 1rem;
        border: 1px solid transparent;
        border-radius: 0.75rem;
        padding: 0.8rem 1rem;
        color: var(--bt-text);
      }
      .alert--success {
        border-color: var(--bt-success);
        background: rgba(114, 214, 160, 0.12);
      }
      .alert--warning {
        background: rgba(255, 122, 130, 0.12);
      }
      .proposal-slug {
        margin-top: 0;
      }
      .notice {
        border-left: 3px solid var(--bt-warning);
        padding-left: 1rem;
      }
      .proposal-status {
        margin-top: 1.5rem;
      }
      .proposal-status strong {
        color: var(--bt-text);
      }
      .actions {
        display: flex;
        flex-wrap: wrap;
        gap: 0.75rem;
        margin-top: 1.25rem;
      }
      .button {
        min-height: 2.75rem;
        border: 0;
        border-radius: 0.6rem;
        padding: 0.7rem 1rem;
        cursor: pointer;
        font: inherit;
        font-weight: 700;
      }
      .button:focus-visible {
        outline: 3px solid var(--bt-focus);
        outline-offset: 3px;
      }
      .button:disabled {
        cursor: wait;
        opacity: 0.6;
      }
      .button--primary {
        background: var(--bt-signal);
        color: var(--bt-ink);
      }
      .button--danger {
        background: var(--bt-danger);
        color: var(--bt-ink);
      }
      .button--secondary {
        border: 1px solid var(--bt-border-strong);
        background: transparent;
        color: var(--bt-text);
      }
      .button--secondary:hover:not(:disabled) {
        background: var(--bt-surface-raised);
      }
      @media (max-width: 599px) {
        .actions {
          flex-direction: column;
        }
        .button {
          width: 100%;
        }
      }
    `,
  ],
})
export class AthleteLinkProposalPageComponent implements OnInit {
  readonly store = inject(AthleteLinkProposalStore);
  readonly pendingDecision = signal<PendingDecision>(null);
  readonly triggerElement = signal<HTMLElement | null>(null);
  readonly dialogOpen = signal(false);

  ngOnInit(): void {
    void this.store.load();
  }

  retry(): void {
    void this.store.load();
  }

  requestDecision(decision: Exclude<PendingDecision, null>, event?: Event): void {
    this.pendingDecision.set(decision);
    this.triggerElement.set(
      event?.currentTarget instanceof HTMLElement ? event.currentTarget : null,
    );
    this.dialogOpen.set(true);
  }

  async confirmDecision(): Promise<void> {
    const decision = this.pendingDecision();
    if (!decision) return;
    const action =
      decision === 'accept'
        ? this.store.accept
        : decision === 'reject'
          ? this.store.reject
          : this.store.contest;
    const completed = await action.call(this.store);
    if (completed) this.cancelDecision();
  }

  cancelDecision(): void {
    this.dialogOpen.set(false);
    this.pendingDecision.set(null);
    this.triggerElement.set(null);
  }

  statusLabel(status: AthleteLinkProposal.StatusEnum): string {
    return (
      (
        {
          PENDENTE: 'Pendente',
          ACEITA: 'Aceito',
          REJEITADA: 'Rejeitado',
          EXPIRADA: 'Expirada',
          CONTESTADA: 'Contestada',
        } as Record<string, string>
      )[status] ?? status
    );
  }

  dialogTitle(): string {
    return this.pendingDecision() === 'reject'
      ? 'Rejeitar vínculo'
      : this.pendingDecision() === 'contest'
        ? 'Contestar vínculo'
        : 'Aceitar vínculo';
  }

  dialogBody(): string {
    const athleteName = this.store.proposal()?.athleteName ?? 'este atleta';
    if (this.pendingDecision() === 'reject')
      return `Rejeitar vínculo com ${athleteName}? Você não controlará este perfil. Se a proposta estiver incorreta, também poderá contestá-la.`;
    if (this.pendingDecision() === 'contest')
      return `Contestar vínculo com ${athleteName}? A proposta será enviada para revisão manual.`;
    return `Aceitar vínculo com ${athleteName}? Você confirma que este é o perfil esportivo que deseja controlar.`;
  }

  dialogConfirmLabel(): string {
    return this.pendingDecision() === 'reject'
      ? 'Rejeitar vínculo'
      : this.pendingDecision() === 'contest'
        ? 'Enviar contestação'
        : 'Aceitar vínculo';
  }
}
