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
import { RouterLink } from '@angular/router';
import { ChatModerationApiAdapter } from './chat-moderation-api.adapter';
import { ChatModerationStore } from './chat-moderation.store';
import { SessionStore } from '../../core/session/session.store';
import { MODERATION_STYLES, keepConfirmationFocus } from './chat-moderation-page.component';

@Component({
  selector: 'app-chat-sanctions-page',
  standalone: true,
  imports: [DatePipe, RouterLink],
  providers: [ChatModerationApiAdapter, ChatModerationStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [MODERATION_STYLES],
  template: `
    <section aria-labelledby="sanctions-title">
      <h1 id="sanctions-title">Punicoes da conta</h1>
      <p>
        Uma sancao bloqueia a escrita em todos os chats. Leitura, saldo e ranking continuam
        acessiveis.
      </p>
      <a routerLink="/conta/historico">Consultar historico e saldo</a>
      <button
        type="button"
        (click)="store.loadSanctions()"
        [disabled]="store.loading() || store.sending()">
        Atualizar punicoes
      </button>
      @if (store.loading()) {
        <p role="status">Consultando punicoes...</p>
      }
      @if (store.error()) {
        <p role="alert">{{ store.error() }}</p>
      }
      @if (store.notice()) {
        <p role="status">{{ store.notice() }}</p>
      }
      @if (!store.loading() && !store.error() && !store.sanctions().length) {
        <p>Nenhuma punicao registrada.</p>
      }
      @for (item of store.sanctions(); track item.sanctionId) {
        <article [attr.data-sanction-id]="item.sanctionId">
          <h2>{{ item.kind }}</h2>
          <p>Motivo: {{ item.reason }}</p>
          <p>
            Inicio: {{ item.effectiveFrom | date: 'short' }}. Fim:
            {{
              item.effectiveUntil ? (item.effectiveUntil | date: 'short') : 'Sem prazo (banimento)'
            }}.
          </p>
          <p>{{ item.active ? 'Ativa' : 'Encerrada' }} · Revisao: {{ item.reviewStatus }}</p>
          @if (item.reviewStatus === 'NONE') {
            <button
              type="button"
              [disabled]="store.sending()"
              (click)="review(item.sanctionId, $event)">
              Pedir revisao
            </button>
          }
        </article>
      }
      @if (store.action()) {
        <dialog
          #confirmation
          tabindex="-1"
          aria-labelledby="review-title"
          (cancel)="cancel($event)"
          (keydown)="keepFocus($event)">
          <h2 id="review-title">Pedir revisao da sancao</h2>
          <p>
            O administrador geral analisara seu pedido. Solicitar revisao nao suspende a punicao
            automaticamente.
          </p>
          <label for="review-reason">Explique seu pedido</label>
          <textarea
            id="review-reason"
            maxlength="280"
            [value]="store.reason()"
            [disabled]="store.sending()"
            (input)="reason($event)"></textarea>
          @if (store.error()) {
            <p role="alert">{{ store.error() }}</p>
          }
          <button type="button" [disabled]="store.sending()" (click)="close()">Cancelar</button>
          <button
            type="button"
            [disabled]="store.sending() || !store.reason().trim()"
            (click)="submit()">
            {{ store.sending() ? 'Registrando...' : 'Enviar pedido de revisao' }}
          </button>
        </dialog>
      }
    </section>
  `,
})
export class ChatSanctionsPageComponent implements OnInit {
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
    void this.store.loadSanctions();
  }
  review(id: string, event: Event) {
    this.trigger = event.currentTarget as HTMLElement;
    this.store.prepare('request-review', id);
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
  cancel(event: Event) {
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
}
