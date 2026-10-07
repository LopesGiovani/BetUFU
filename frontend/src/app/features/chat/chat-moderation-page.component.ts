import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnInit,
  ViewChild,
  effect,
  inject,
} from '@angular/core';
import { DatePipe } from '@angular/common';
import { ChatModerationApiAdapter } from './chat-moderation-api.adapter';
import { ChatModerationStore, type ModerationAction } from './chat-moderation.store';
import { SessionStore } from '../../core/session/session.store';

export function keepConfirmationFocus(event: KeyboardEvent) {
  if (event.key !== 'Tab') return;
  const dialog = event.currentTarget as HTMLDialogElement;
  const controls = Array.from(
    dialog.querySelectorAll<HTMLElement>('textarea:not(:disabled), button:not(:disabled)'),
  );
  if (!controls.length) return;
  event.preventDefault();
  const current = controls.indexOf(document.activeElement as HTMLElement);
  const next =
    current < 0 ? 0 : (current + (event.shiftKey ? -1 : 1) + controls.length) % controls.length;
  controls[next].focus();
}

export const MODERATION_STYLES = `
  :host { display:block; color:var(--color-text-primary); }
  section { max-width:72rem; margin:auto; padding:var(--spacing-4,1rem); }
  article, aside { border:1px solid var(--color-border-default,#ccc); border-radius:var(--radius-md,.5rem); padding:1rem; margin:1rem 0; overflow-wrap:anywhere; }
  button { min-height:44px; padding:.6rem 1rem; margin:.2rem; cursor:pointer; }
  button:focus-visible, textarea:focus-visible { outline:3px solid var(--color-brand-primary,#23684c); outline-offset:2px; }
  dialog { color:var(--color-text-primary); background:var(--color-surface-card,#fff); border:1px solid var(--color-border-default,#ccc); border-radius:var(--radius-md,.5rem); width:min(32rem,calc(100vw - 2rem)); padding:1rem; box-sizing:border-box; }
  dialog::backdrop { background:rgb(0 0 0 / .5); }
  textarea { width:100%; min-height:6rem; box-sizing:border-box; }
  pre { white-space:pre-wrap; overflow-wrap:anywhere; }
  .actions { display:flex; flex-wrap:wrap; gap:.3rem; }
`;

@Component({
  selector: 'app-chat-moderation-page',
  standalone: true,
  imports: [DatePipe],
  providers: [ChatModerationApiAdapter, ChatModerationStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [MODERATION_STYLES],
  template: `
    <section aria-labelledby="moderation-title">
      <h1 id="moderation-title">Moderacao da comunidade</h1>
      <p>Casos no seu escopo. Punicoes de chat preservam saldo, apostas e ranking.</p>
      <button
        type="button"
        (click)="store.loadModeration()"
        [disabled]="store.loading() || store.sending()">
        Atualizar casos
      </button>
      @if (store.loading()) {
        <p role="status">Consultando casos...</p>
      }
      @if (store.error()) {
        <p role="alert">{{ store.error() }}</p>
      }
      @if (store.notice()) {
        <p role="status">{{ store.notice() }}</p>
      }
      @if (!store.loading() && !store.error() && !store.cases().length) {
        <p>Nenhum caso no seu escopo.</p>
      }
      @for (item of store.cases(); track item.caseId) {
        <article [attr.data-case-id]="item.caseId">
          <h2>Caso {{ item.caseId }}</h2>
          <p>Estado: {{ item.status }} · Aberto {{ item.openedAt | date: 'short' }}</p>
          <div class="actions">
            <button
              type="button"
              [disabled]="store.sending()"
              (click)="confirm('evidence', item.caseId, $event)">
              Consultar original restrito
            </button>
            <button
              type="button"
              [disabled]="store.sending()"
              (click)="confirm('remove', item.caseId, $event)">
              Remover mensagem
            </button>
            @if (item.status !== 'RESOLVED') {
              <button
                type="button"
                [disabled]="store.sending()"
                (click)="confirm('forward', item.caseId, $event)">
                Encaminhar caso
              </button>
              @if (store.admin()) {
                <button
                  type="button"
                  [disabled]="store.sending()"
                  (click)="propose(item.caseId, $event)">
                  Propor sancao
                </button>
              }
            }
          </div>
        </article>
      }
      @if (store.admin()) {
        <h2>Revisoes pendentes</h2>
        @for (review of store.reviews(); track review.reviewId) {
          <article [attr.data-review-id]="review.reviewId">
            <p>Pedido: {{ review.reason }}</p>
            <button
              type="button"
              [disabled]="store.sending()"
              (click)="confirm('maintain', review.reviewId, $event)">
              Manter sancao
            </button>
            <button
              type="button"
              [disabled]="store.sending()"
              (click)="confirm('annul', review.reviewId, $event)">
              Anular sancao
            </button>
          </article>
        }
      }
      @if (store.evidence(); as evidence) {
        <aside aria-label="Evidencia restrita">
          <h2>Original restrito — acesso auditado</h2>
          <pre>{{ evidence.originalBody }}</pre>
          <button type="button" (click)="store.evidence.set(null)">Fechar evidencia</button>
        </aside>
      }
      @if (store.action()) {
        <dialog
          #confirmation
          tabindex="-1"
          aria-labelledby="confirmation-title"
          (cancel)="onCancel($event)"
          (keydown)="keepFocus($event)">
          <h2 id="confirmation-title">{{ title() }}</h2>
          @if (store.action() === 'sanction') {
            <p>Duracao sugerida: {{ store.suggestedKind() }}. Afeta somente a escrita no chat.</p>
          }
          @if (store.action() === 'forward') {
            <p>Encaminha para analise administrativa. Nao aplica punicao automaticamente.</p>
          }
          @if (store.action() === 'remove') {
            <p>O publico vera um marcador. O original sera preservado para revisao autorizada.</p>
          }
          @if (store.action() === 'evidence') {
            <p>O original so sera consultado apos confirmar; o acesso sera auditado.</p>
          }
          <label for="moderation-reason">Motivo da operacao</label>
          <textarea
            id="moderation-reason"
            maxlength="280"
            [disabled]="store.sending()"
            [value]="store.reason()"
            (input)="reason($event)"></textarea>
          @if (store.error()) {
            <p role="alert">{{ store.error() }}</p>
          }
          <button type="button" [disabled]="store.sending()" (click)="close()">Cancelar</button>
          <button
            type="button"
            [disabled]="store.sending() || !store.reason().trim()"
            (click)="submit()">
            {{ store.sending() ? 'Registrando...' : 'Confirmar operacao' }}
          </button>
        </dialog>
      }
    </section>
  `,
})
export class ChatModerationPageComponent implements OnInit {
  readonly keepFocus = keepConfirmationFocus;
  readonly store = inject(ChatModerationStore);
  private readonly session = inject(SessionStore);
  private trigger: HTMLElement | null = null;
  private dialog: HTMLDialogElement | undefined;
  @ViewChild('confirmation') set confirmation(value: ElementRef<HTMLDialogElement> | undefined) {
    this.dialog = value?.nativeElement;
    if (value && !value.nativeElement.open) value.nativeElement.showModal();
  }
  constructor() {
    effect(() => {
      if (this.session.isLoggedOut() || this.session.isExpired()) this.store.clear();
    });
  }
  ngOnInit() {
    void this.store.loadModeration();
  }
  confirm(action: ModerationAction, id: string, event: Event) {
    this.trigger = event.currentTarget as HTMLElement;
    this.store.prepare(action, id);
  }
  propose(id: string, event: Event) {
    this.trigger = event.currentTarget as HTMLElement;
    void this.store.propose(id);
  }
  reason(event: Event) {
    this.store.reason.set((event.target as HTMLTextAreaElement).value);
  }
  close() {
    if (this.store.sending()) return;
    this.dialog?.close();
    this.store.cancel();
    setTimeout(() => this.trigger?.focus());
  }
  onCancel(event: Event) {
    event.preventDefault();
    if (!this.store.sending()) this.close();
  }
  async submit() {
    await this.store.submit();
    if (!this.store.action()) {
      this.dialog?.close();
      setTimeout(() => this.trigger?.focus());
    }
  }
  title() {
    switch (this.store.action()) {
      case 'forward':
        return 'Encaminhar caso';
      case 'remove':
        return 'Remover mensagem';
      case 'evidence':
        return 'Consultar original restrito';
      case 'sanction':
        return 'Confirmar sancao sugerida';
      case 'maintain':
        return 'Manter sancao';
      case 'annul':
        return 'Anular sancao';
      default:
        return 'Confirmar operacao';
    }
  }
}
