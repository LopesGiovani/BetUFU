import { ChangeDetectionStrategy, Component, ElementRef, effect, inject, input, untracked, viewChild } from '@angular/core';
import { SessionStore } from '../../core/session/session.store';
import { ChatRulesStore } from './chat-rules.store';

@Component({
  selector: 'app-chat-rules-dialog', standalone: true, providers: [ChatRulesStore],
  changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <button #trigger type="button" (click)="store.openManual()">Regras</button>
    @if (store.status() === 'loading') { <p role="status">Consultando preferência de regras...</p> }
    @if (store.status() === 'dismiss-saving') { <p role="status">Salvando preferência de regras...</p> }
    @if (store.error()) {
      <p role="alert">{{ store.error() }}</p>
      <button type="button" (click)="store.retry()">Tentar sincronizar regras</button>
    }
    <dialog #dialog tabindex="-1" aria-labelledby="chat-rules-title" aria-describedby="chat-rules-description"
      (cancel)="$event.preventDefault(); store.close()" (click)="backdrop($event)"
      (keydown)="keepFocus($event, closeButton)">
      <h2 id="chat-rules-title">Regras da conversa</h2>
      <p id="chat-rules-description">Converse com respeito sobre os jogos universitários.</p>
      <ul>
        <li>Não publique discriminação, ofensas, assédio, spam ou fraude.</li>
        <li>Para enviar mensagens, entre na conta e mantenha sua matrícula ativa. Limite de 280 caracteres e intervalo de três segundos.</li>
        <li>Use Denunciar mensagem para pedir análise da moderação. Silenciar pessoa altera apenas a sua leitura.</li>
        <li>Restrições de chat podem ser consultadas e revisadas na sua conta. Observe os avisos de pausa e encerramento da partida.</li>
      </ul>
      <p>Fechar este aviso não significa aceite jurídico nem comprovação de leitura. Você pode reabrir Regras quando quiser.</p>
      <button #closeButton type="button" autofocus (click)="store.close()">Fechar regras</button>
    </dialog>
  `,
  styles: `
    :host { display:block; color:var(--bt-text); }
    button { min-height:44px; padding:var(--bt-space-2) var(--bt-space-4); color:var(--bt-text); background:var(--bt-surface-raised); border:1px solid var(--bt-border-strong); font:inherit; cursor:pointer; max-width:100%; }
    button:focus-visible { outline:3px solid var(--bt-focus); outline-offset:3px; }
    dialog { box-sizing:border-box; width:min(40rem,calc(100% - 2rem)); max-height:calc(100% - 2rem); overflow:auto; padding:var(--bt-space-4); color:var(--bt-text); background:var(--bt-surface); border:1px solid var(--bt-border-strong); overflow-wrap:anywhere; }
    dialog::backdrop { background:rgb(0 0 0 / 65%); } li { margin-block:var(--bt-space-2); }
  `,
})
export class ChatRulesDialogComponent {
  readonly automatic = input(false);
  readonly store = inject(ChatRulesStore);
  private readonly session = inject(SessionStore);
  private readonly dialog = viewChild<ElementRef<HTMLDialogElement>>('dialog');
  private readonly trigger = viewChild<ElementRef<HTMLButtonElement>>('trigger');
  constructor() {
    effect(() => {
      const automatic = this.automatic(); this.session.revision(); this.session.isAuthenticated();
      untracked(() => void this.store.start(automatic));
    });
    effect(() => {
      const dialog = this.dialog()?.nativeElement; const open = this.store.opened();
      if (!dialog) return;
      if (open && !dialog.open) dialog.showModal();
      else if (!open && dialog.open) { dialog.close(); this.trigger()?.nativeElement.focus(); }
    });
  }
  backdrop(event: MouseEvent) {
    const dialog = this.dialog()?.nativeElement; if (!dialog || event.target !== dialog) return;
    const bounds = dialog.getBoundingClientRect();
    if (event.clientX < bounds.left || event.clientX > bounds.right || event.clientY < bounds.top || event.clientY > bounds.bottom) void this.store.close();
  }
  keepFocus(event: KeyboardEvent, button: HTMLButtonElement) {
    if (event.key === 'Tab') { event.preventDefault(); button.focus(); }
  }
}
