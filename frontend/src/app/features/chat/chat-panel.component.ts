import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  Injector,
  afterNextRender,
  computed,
  effect,
  inject,
  input,
  signal,
  untracked,
  viewChild,
} from '@angular/core';
import { SessionStore } from '../../core/session/session.store';
import { ChatReadStore } from './chat-read.store';
import { ChatOutboxStore, validChatText } from './chat-outbox.store';
import { ChatWriteApiAdapter } from './chat-write-api.adapter';

@Component({
  selector: 'app-chat-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [ChatReadStore, ChatWriteApiAdapter, ChatOutboxStore],
  template: `
    <section
      class="chat-panel"
      [attr.aria-labelledby]="titleId()"
      [attr.aria-busy]="
        store.status() === 'loading' || store.isResynchronizing() || store.historyLoading()
      ">
      <header class="chat-heading">
        <h2 [id]="titleId()">{{ heading() }}</h2>
        <p class="chat-connection" role="status">{{ store.connectionLabel() }}</p>
      </header>
      @if (eventId() === null && !session.isAuthenticated()) {
        @if (session.isChecking()) {
          <p role="status">Verificando sessão</p>
        } @else {
          <p>A conversa da Comunidade é reservada a quem entrou na conta.</p>
          <a class="chat-action chat-login" [href]="loginUrl()">Entrar para ler a Comunidade</a>
        }
      } @else {
        <p class="chat-readonly">
          Somente leitura. As permissões de envio são verificadas pelo servidor.
        </p>
        @if (store.status() === 'loading') {
          <p role="status">Carregando mensagens</p>
        }
        @if (store.error()) {
          <p class="chat-error" role="alert">{{ store.error() }}</p>
          <button class="chat-action" type="button" (click)="store.retry()">
            Tentar novamente
          </button>
        }
        @if (store.historyCursor()) {
          <button
            class="chat-action"
            type="button"
            [disabled]="store.historyLoading()"
            (click)="store.older()">
            Carregar mensagens anteriores
          </button>
        }
        <div
          #history
          class="chat-history"
          role="region"
          [attr.aria-label]="heading() + ': mensagens'"
          tabindex="0">
          @for (message of store.messages(); track message.messageId) {
            <article
              class="chat-message"
              [attr.data-message-id]="message.messageId"
              [class.chat-message--own]="
                !message.removed && message.participantId === store.ownId()
              ">
              <header>
                @if (!message.removed) {
                  <strong>{{ message.nickname }}</strong>
                  @if (message.participantId === store.ownId()) {
                    <span class="chat-own">Você</span>
                  }
                }
                <time [attr.datetime]="message.postedAt" [title]="fullDate(message.postedAt)">
                  {{ time(message.postedAt) }}
                </time>
              </header>
              <p>{{ message.removed ? 'Mensagem removida' : message.text }}</p>
            </article>
          } @empty {
            @if (store.status() === 'ready') {
              <p class="chat-empty">Ainda não há mensagens nesta conversa.</p>
            }
          }
        </div>
        <button class="chat-action chat-new" type="button" (click)="toBottom()" aria-live="polite">
          {{ pending() > 0 ? 'Novas mensagens (' + pending() + ')' : 'Ir ao fim' }}
        </button>
        @if (session.isAuthenticated()) {
          <form class="chat-composer" (submit)="send($event)">
            <label [for]="titleId() + '-draft'">Sua mensagem</label>
            <textarea
              [id]="titleId() + '-draft'"
              [value]="draft()"
              (input)="edit($event)"
              [disabled]="!outbox.canCompose()"
              [attr.aria-describedby]="titleId() + '-counter'"
              rows="2"></textarea>
            <p [id]="titleId() + '-counter'">{{ characters() }}/280 caracteres</p>
            <button
              class="chat-action"
              type="submit"
              [disabled]="!outbox.canCompose() || !validDraft()">
              Enviar mensagem
            </button>
            <p class="chat-readonly">
              Pendentes ficam somente nesta aba. Fechar, atualizar ou sair da conta descarta a fila.
            </p>
          </form>
          <div class="chat-outbox" aria-live="polite">
            @if (outbox.notice()) {
              <p role="status">{{ outbox.notice() }}</p>
            }
            @for (item of outbox.items(); track item.key) {
              <p class="chat-pending">
                {{ item.text }}
                <strong>{{
                  item.status === 'sent'
                    ? 'Enviada'
                    : item.status === 'sending'
                      ? 'Aguardando confirmacao'
                      : 'Aguardando conexao ou confirmacao'
                }}</strong>
              </p>
            }
          </div>
        }
        @if (!session.isAuthenticated() && !session.isChecking()) {
          <a class="chat-action chat-login" [href]="loginUrl()">Entrar na conta</a>
        }
      }
    </section>
  `,
  styles: `
    :host {
      display: block;
      min-width: 0;
      margin-top: var(--bt-space-8);
    }
    .chat-panel {
      padding: var(--bt-space-4);
      border: 1px solid var(--bt-border);
      border-radius: var(--bt-radius-card);
      background: var(--bt-surface);
      color: var(--bt-text);
    }
    .chat-heading {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      justify-content: space-between;
      gap: var(--bt-space-2);
      margin-bottom: var(--bt-space-4);
    }
    h2 {
      margin: 0;
      color: var(--bt-display);
      font:
        600 var(--bt-type-heading)/1.2 'Barlow Condensed',
        sans-serif;
    }
    .chat-connection,
    .chat-readonly,
    time {
      color: var(--bt-text-muted);
      font-size: var(--bt-type-label);
    }
    .chat-connection {
      margin: 0;
    }
    .chat-history {
      height: 26rem;
      overflow-y: auto;
      overscroll-behavior: contain;
      scrollbar-gutter: stable;
      margin-block: var(--bt-space-4);
      padding: var(--bt-space-2);
      border-block: 1px solid var(--bt-border);
    }
    .chat-message {
      padding-block: var(--bt-space-2);
      padding-inline: var(--bt-space-2);
      border-left: 3px solid transparent;
    }
    .chat-message--own {
      border-left-color: var(--bt-signal-soft);
    }
    .chat-message header {
      display: flex;
      flex-wrap: wrap;
      align-items: baseline;
      gap: var(--bt-space-2);
    }
    strong,
    .chat-own {
      font-size: var(--bt-type-label);
    }
    .chat-own {
      color: var(--bt-signal-soft);
      font-weight: 600;
    }
    .chat-message p {
      margin: var(--bt-space-1) 0 0;
      white-space: pre-wrap;
      overflow-wrap: anywhere;
      line-height: 1.5;
    }
    .chat-action {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      min-height: 44px;
      padding: var(--bt-space-2) var(--bt-space-4);
      margin-right: var(--bt-space-2);
      border: 1px solid var(--bt-border-strong);
      border-radius: var(--bt-radius-card);
      background: var(--bt-surface-raised);
      color: var(--bt-text);
      font: inherit;
      cursor: pointer;
      text-decoration: none;
      overflow-wrap: anywhere;
    }
    .chat-action:disabled {
      opacity: 0.65;
      cursor: wait;
    }
    .chat-action:focus-visible,
    .chat-history:focus-visible {
      outline: 3px solid var(--bt-focus);
      outline-offset: 3px;
    }
    .chat-new {
      border-color: var(--bt-signal-soft);
    }
    .chat-error {
      color: var(--bt-danger);
    }
    .chat-empty {
      color: var(--bt-text-muted);
    }
    .chat-composer {
      display: grid;
      gap: var(--bt-space-2);
      margin-top: var(--bt-space-4);
    }
    textarea {
      box-sizing: border-box;
      width: 100%;
      min-height: 44px;
      padding: var(--bt-space-2);
      color: var(--bt-text);
      background: var(--bt-surface-raised);
      border: 1px solid var(--bt-border-strong);
      border-radius: var(--bt-radius-card);
      font: inherit;
      resize: vertical;
    }
    textarea:focus-visible {
      outline: 3px solid var(--bt-focus);
      outline-offset: 3px;
    }
    .chat-pending {
      white-space: pre-wrap;
      overflow-wrap: anywhere;
    }
  `,
})
export class ChatPanelComponent {
  readonly eventId = input<string | null>(null);
  readonly store = inject(ChatReadStore);
  readonly session = inject(SessionStore);
  readonly outbox = inject(ChatOutboxStore);
  readonly draft = signal('');
  readonly characters = computed(() => [...this.draft()].length);
  readonly validDraft = computed(() => validChatText(this.draft()));
  readonly heading = computed(() => (this.eventId() === null ? 'Chat global' : 'Chat da partida'));
  readonly titleId = computed(() => 'chat-title-' + (this.eventId() ?? 'global'));
  readonly loginUrl = computed(
    () =>
      '/login/start?returnTo=' +
      encodeURIComponent(this.eventId() === null ? '/comunidade' : '/eventos/' + this.eventId()),
  );
  readonly pending = signal(0);
  private readonly history = viewChild<ElementRef<HTMLDivElement>>('history');
  private readonly injector = inject(Injector);
  private previousIds = new Set<string>();

  constructor() {
    effect(() => {
      this.session.revision();
      if (this.session.isChecking()) this.store.close();
      else this.store.open(this.eventId(), this.session.isAuthenticated());
      const eventId = this.eventId();
      const authenticated = this.session.isAuthenticated();
      untracked(() => {
        this.draft.set('');
        if (authenticated) this.outbox.open(eventId);
        else this.outbox.close();
      });
    });
    effect(() => {
      const revision = this.outbox.acknowledgements();
      if (revision > 0) untracked(() => this.store.refresh());
    });
    effect(() => {
      const revision = this.store.revision();
      const messages = this.store.messages();
      const mode = this.store.mode();
      const box = this.history()?.nativeElement;
      if (!box) {
        this.previousIds.clear();
        this.pending.set(0);
        return;
      }
      const atEnd = box.scrollHeight - box.clientHeight - box.scrollTop < 40;
      const anchor = [...box.querySelectorAll<HTMLElement>('[data-message-id]')].find(
        (element) => element.getBoundingClientRect().bottom > box.getBoundingClientRect().top,
      );
      const anchorId = anchor?.dataset['messageId'];
      const anchorOffset = anchor
        ? anchor.getBoundingClientRect().top - box.getBoundingClientRect().top
        : 0;
      afterNextRender(
        () => {
          if (revision !== this.store.revision()) return;
          if (mode === 'initial' || (mode === 'sync' && atEnd)) {
            box.scrollTop = box.scrollHeight;
            this.pending.set(0);
          } else {
            const currentAnchor = anchorId
              ? box.querySelector<HTMLElement>(`[data-message-id="${anchorId}"]`)
              : null;
            if (currentAnchor)
              box.scrollTop +=
                currentAnchor.getBoundingClientRect().top -
                box.getBoundingClientRect().top -
                anchorOffset;
            if (mode === 'sync')
              this.pending.update(
                (n) => n + messages.filter((m) => !this.previousIds.has(m.messageId)).length,
              );
          }
          this.previousIds = new Set(messages.map((m) => m.messageId));
        },
        { injector: this.injector },
      );
    });
  }

  toBottom(): void {
    const box = this.history()?.nativeElement;
    if (box) box.scrollTop = box.scrollHeight;
    this.pending.set(0);
  }

  edit(event: Event): void {
    this.draft.set((event.target as HTMLTextAreaElement).value);
  }
  send(event: Event): void {
    event.preventDefault();
    if (this.outbox.enqueue(this.draft())) this.draft.set('');
  }

  time(value: string): string {
    return new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit' }).format(
      new Date(value),
    );
  }
  fullDate(value: string): string {
    return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'long', timeStyle: 'short' }).format(
      new Date(value),
    );
  }
}

@Component({
  selector: 'app-community-page',
  standalone: true,
  imports: [ChatPanelComponent],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="community" aria-labelledby="community-title">
      <p class="community-kicker">Arena / Comunidade</p>
      <h1 id="community-title">Comunidade</h1>
      <p>Uma conversa sobre os jogos universitários.</p>
      <app-chat-panel />
    </section>
  `,
  styles: `
    .community {
      width: var(--bt-container);
      margin-inline: auto;
      padding-block: var(--bt-space-8) var(--bt-space-16);
      color: var(--bt-text);
    }
    h1 {
      margin-block: var(--bt-space-2);
      font:
        600 var(--bt-type-heading)/1.2 'Barlow Condensed',
        sans-serif;
    }
    .community-kicker {
      color: var(--bt-signal-soft);
      font-size: var(--bt-type-label);
      text-transform: uppercase;
    }
    p {
      overflow-wrap: anywhere;
    }
  `,
})
export class CommunityPageComponent {}
