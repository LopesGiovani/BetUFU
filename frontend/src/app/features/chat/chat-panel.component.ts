import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
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
import { firstValueFrom, Subject, takeUntil } from 'rxjs';
import { SessionStore } from '../../core/session/session.store';
import { ChatReadStore } from './chat-read.store';
import { ChatOutboxStore, validChatText } from './chat-outbox.store';
import { ChatWriteApiAdapter } from './chat-write-api.adapter';
import { ChatPersonalMuteStore } from './chat-personal-mute.store';
import { ChatCommunityApiAdapter, type ChatReportReason } from './chat-community-api.adapter';
import type { ChatMessage } from '../../api/generated/model/chat-message';

@Component({
  selector: 'app-chat-panel',
  standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [
    ChatReadStore,
    ChatWriteApiAdapter,
    ChatOutboxStore,
    ChatCommunityApiAdapter,
    ChatPersonalMuteStore,
  ],
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
          @for (message of visibleMessages(); track message.messageId) {
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
              @if (session.isAuthenticated() || !message.removed) {
                <details class="chat-menu">
                  <summary class="chat-action">Acoes</summary>
                  @if (session.isAuthenticated()) {
                    @if (message.participantId && message.participantId !== store.ownId()) {
                      <button
                        class="chat-action"
                        type="button"
                        [disabled]="!mute.canChange()"
                        (click)="toggleMute(message.participantId)">
                        Silenciar pessoa
                      </button>
                    }
                    <button class="chat-action" type="button" (click)="openReport(message)">
                      Denunciar mensagem
                    </button>
                  } @else {
                    <a class="chat-action chat-login" [href]="loginUrl()"
                      >Entrar para denunciar ou silenciar</a
                    >
                  }
                </details>
              }
            </article>
          } @empty {
            @if (store.status() === 'ready') {
              <p class="chat-empty">Ainda não há mensagens nesta conversa.</p>
            }
          }
        </div>
        @if (session.isAuthenticated() && mutedPeople().length > 0) {
          <details class="chat-muted">
            <summary class="chat-action">Pessoas silenciadas ({{ mutedPeople().length }})</summary>
            <p>O silenciamento vale para todos os chats, somente na sua conta.</p>
            @for (person of mutedPeople(); track person.id) {
              <p>
                {{ person.name }}
                <button
                  class="chat-action"
                  type="button"
                  [disabled]="!mute.canChange()"
                  (click)="mute.setMuted(person.id, false)">
                  Desfazer silenciamento
                </button>
              </p>
            }
          </details>
        }
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
        @if (mute.notice()) {
          <p class="chat-error" role="alert">{{ mute.notice() }}</p>
          @if (mute.error()) {
            <button class="chat-action" type="button" (click)="mute.retry()">
              Tentar novamente
            </button>
          }
        }
        @if (reporting()) {
          <dialog
            #reportDialog
            class="chat-dialog"
            role="dialog"
            aria-modal="true"
            [attr.aria-labelledby]="titleId() + '-report-title'"
            tabindex="-1"
            (keydown)="reportKeydown($event)"
            (cancel)="cancelReport()">
            <h3 [id]="titleId() + '-report-title'">Denunciar mensagem</h3>
            <p>Escolha um motivo. O texto da mensagem nao e exposto nesta tela.</p>
            <label [for]="titleId() + '-report-reason'">Motivo</label>
            <select
              [id]="titleId() + '-report-reason'"
              [disabled]="reportBusy() || reportDone()"
              [value]="reportReason()"
              (change)="changeReason($event)">
              <option value="DISCURSO_DE_ODIO">Discurso de odio</option>
              <option value="SPAM">Spam</option>
              <option value="OFENSA">Ofensa</option>
              <option value="ASSEDIO">Assedio</option>
              <option value="FRAUDE">Fraude</option>
            </select>
            @if (reportNotice()) {
              <p role="status">{{ reportNotice() }}</p>
            }
            <div>
              <button class="chat-action" type="button" (click)="cancelReport()">Cancelar</button>
              <button
                class="chat-action"
                type="button"
                [disabled]="reportBusy() || reportDone()"
                (click)="submitReport()">
                Enviar denuncia
              </button>
            </div>
          </dialog>
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
    .chat-menu {
      display: inline-block;
      margin-top: var(--bt-space-1);
    }
    .chat-menu summary {
      list-style: none;
      cursor: pointer;
      min-height: 44px;
      padding: var(--bt-space-1) var(--bt-space-2);
    }
    .chat-menu summary::-webkit-details-marker {
      display: none;
    }
    .chat-dialog::backdrop {
      background: rgb(0 0 0 / 55%);
    }
    .chat-dialog {
      box-sizing: border-box;
      width: min(calc(100% - 2rem), 24rem);
      max-height: calc(100% - 2rem);
      overflow-y: auto;
      padding: var(--bt-space-4);
      border: 1px solid var(--bt-border-strong);
      border-radius: var(--bt-radius-card);
      background: var(--bt-surface);
      color: var(--bt-text);
    }
    .chat-dialog select {
      width: 100%;
      min-height: 44px;
      margin-block: var(--bt-space-2) var(--bt-space-4);
    }
  `,
})
export class ChatPanelComponent {
  readonly eventId = input<string | null>(null);
  readonly store = inject(ChatReadStore);
  readonly session = inject(SessionStore);
  readonly outbox = inject(ChatOutboxStore);
  readonly mute = inject(ChatPersonalMuteStore);
  private readonly communityApi = inject(ChatCommunityApiAdapter);
  readonly visibleMessages = computed(() => {
    const muted = this.mute.participantIds();
    return this.store
      .messages()
      .filter(
        (message) =>
          message.removed ||
          !message.participantId ||
          message.participantId === this.store.ownId() ||
          !muted.has(message.participantId),
      );
  });
  readonly reporting = signal<ChatMessage | null>(null);
  readonly mutedPeople = computed(() =>
    [...this.mute.participantIds()].map((id) => ({
      id,
      name:
        this.store.messages().find((message) => message.participantId === id)?.nickname ??
        'Pessoa silenciada',
    })),
  );
  readonly reportReason = signal<ChatReportReason>('SPAM');
  readonly reportBusy = signal(false);
  readonly reportDone = signal(false);
  readonly reportNotice = signal<string | null>(null);
  private reportTrigger: HTMLElement | null = null;
  private readonly reportDialog = viewChild<ElementRef<HTMLDialogElement>>('reportDialog');
  private readonly reportCancel = new Subject<void>();
  private reportGeneration = 0;
  private reportKey = '';
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
    inject(DestroyRef).onDestroy(() => this.cancelReport());
    effect(() => {
      this.session.revision();
      if (this.session.isChecking()) this.store.close();
      else this.store.open(this.eventId(), this.session.isAuthenticated());
      const eventId = this.eventId();
      const authenticated = this.session.isAuthenticated();
      untracked(() => {
        this.draft.set('');
        this.cancelReport();
        if (authenticated) {
          this.outbox.open(eventId);
        } else {
          this.outbox.close();
        }
      });
    });
    effect(() => {
      const revision = this.store.pushRevision();
      if (revision > 0) untracked(() => void this.mute.refresh());
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

  toggleMute(participantId: string): void {
    void this.mute.setMuted(participantId, !this.mute.isMuted(participantId));
  }

  openReport(message: ChatMessage): void {
    if (!this.session.isAuthenticated()) return;
    this.reportGeneration++;
    this.reportKey = crypto.randomUUID();
    this.reportTrigger =
      document.activeElement instanceof HTMLElement ? document.activeElement : null;
    this.reportNotice.set(null);
    this.reportDone.set(false);
    this.reportReason.set('SPAM');
    this.reporting.set(message);
    const generation = this.reportGeneration;
    afterNextRender(
      () => {
        if (generation !== this.reportGeneration) return;
        const dialog = this.reportDialog()?.nativeElement;
        dialog?.showModal();
        dialog?.querySelector<HTMLElement>('select')?.focus();
      },
      { injector: this.injector },
    );
  }

  cancelReport(): void {
    this.reportGeneration++;
    this.reportCancel.next();
    this.reportDialog()?.nativeElement.close();
    this.reporting.set(null);
    this.reportBusy.set(false);
    this.reportNotice.set(null);
    this.reportKey = '';
    this.reportTrigger?.focus();
    this.reportTrigger = null;
  }

  reportKeydown(event: KeyboardEvent): void {
    if (event.key !== 'Tab') return;
    const controls = this.reportDialog()?.nativeElement.querySelectorAll<HTMLElement>(
      'select:not(:disabled), button:not(:disabled)',
    );
    if (!controls?.length) return;
    const first = controls[0];
    const last = controls[controls.length - 1];
    const boundary = event.shiftKey ? first : last;
    if (document.activeElement === boundary) {
      event.preventDefault();
      (event.shiftKey ? last : first).focus();
    }
  }

  changeReason(event: Event): void {
    this.reportReason.set((event.target as HTMLSelectElement).value as ChatReportReason);
    this.reportKey = crypto.randomUUID();
  }

  async submitReport(): Promise<void> {
    const message = this.reporting();
    if (!message || this.reportBusy() || this.reportDone() || !this.session.isAuthenticated())
      return;
    const generation = this.reportGeneration;
    const epoch = this.session.revision();
    this.reportBusy.set(true);
    this.reportNotice.set(null);
    try {
      await firstValueFrom(
        this.communityApi
          .report(message.messageId, this.reportReason(), this.reportKey)
          .pipe(takeUntil(this.reportCancel)),
      );
      if (generation !== this.reportGeneration || epoch !== this.session.revision()) return;
      this.reportNotice.set('Denuncia recebida. Obrigado por ajudar a moderar a comunidade.');
      this.reportDone.set(true);
    } catch {
      if (generation !== this.reportGeneration || epoch !== this.session.revision()) return;
      this.reportNotice.set('Nao foi possivel enviar a denuncia. Tente novamente.');
    } finally {
      if (generation === this.reportGeneration) this.reportBusy.set(false);
    }
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
