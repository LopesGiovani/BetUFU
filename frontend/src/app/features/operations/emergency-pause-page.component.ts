import { ChangeDetectionStrategy, Component, ElementRef, effect, inject, signal, untracked, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { EmergencyPauseStore } from './emergency-pause.store';
import { SessionStore } from '../../core/session/session.store';
import type { EmergencyPauseCommand } from '../../api/generated/model/emergency-pause-command';
import type { EmergencyPauseState } from '../../api/generated/model/emergency-pause-state';
import { keepConfirmationFocus } from '../chat/chat-moderation-page.component';

@Component({
  selector: 'app-emergency-pause-page', standalone: true,
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink], providers: [EmergencyPauseStore],
  template: `
    <section class="pause-page" aria-labelledby="pause-title">
      <a routerLink="/operacao/painel">Painel operacional</a>
      <h1 id="pause-title">Pausas de emergencia</h1>
      <p>Contenha novos envios. Historico, direitos e liquidacoes de palpites aceitos continuam disponiveis.</p>
      @if (store.loading()) { <p role="status">Carregando pausas</p> }
      @if (store.error()) { <p role="alert">{{ store.error() }}</p> }
      <button type="button" (click)="store.load()" [disabled]="store.loading() || store.sending()">Atualizar pausas</button>
      @if (store.administrator()) {
        <section aria-labelledby="new-pause">
          <h2 id="new-pause">Nova pausa</h2>
          <label for="pause-kind">Alvo da pausa</label>
          <select id="pause-kind" [ngModel]="kind()" (ngModelChange)="kind.set($event)">
            <option value="ACCOUNT">Conta</option><option value="EVENT">Evento</option>
            <option value="MARKET">Mercado</option><option value="CHANNEL">Canal</option>
          </select>
          <label for="pause-target">Identificador do alvo</label>
          <input id="pause-target" [ngModel]="target()" (ngModelChange)="target.set($event)" autocomplete="off" />
          <button type="button" (click)="prepare()" [disabled]="store.sending()">Pausar novos envios</button>
        </section>
        <section aria-labelledby="active-pauses">
          <h2 id="active-pauses">Pausas ativas</h2>
          @for (pause of store.pauses(); track pause.factId) {
            <article [attr.data-pause-id]="pause.factId">
              <h3>{{ labels[pause.kind] }}</h3>
              <p>{{ pause.targetId }}</p><p>{{ pause.reason }}</p>
              <p>Pausado em <time [attr.datetime]="pause.recordedAt">{{ pause.recordedAt }}</time></p>
              <button type="button" (click)="prepare(pause)" [disabled]="store.sending()">Retomar novos envios</button>
            </article>
          } @empty { <p>Nenhuma pausa ativa.</p> }
        </section>
      }
      <dialog #confirmation tabindex="-1" aria-labelledby="pause-confirm-title" (cancel)="$event.preventDefault(); cancel()" (keydown)="keepFocus($event)">
        <h2 id="pause-confirm-title">{{ command()?.action === 'RESUME' ? 'Confirmar retomada' : 'Confirmar pausa' }}</h2>
        <p>{{ impact() }}</p>
        <p>Retomar nao reabre mercado encerrado, remove sancao ou concede permissao de escrita.</p>
        <label for="pause-reason">Motivo da operacao</label>
        <textarea id="pause-reason" maxlength="280" [ngModel]="reason()" (ngModelChange)="reason.set($event)" [disabled]="store.sending()"></textarea>
        @if (store.error()) { <p role="alert">{{ store.error() }}</p> }
        <button type="button" (click)="cancel()" [disabled]="store.sending()">Cancelar</button>
        <button type="button" (click)="confirm()" [disabled]="store.sending() || !reason().trim()">Confirmar operacao</button>
      </dialog>
    </section>
  `,
  styles: `
    :host { display:block; min-width:0; color:var(--bt-text); }
    .pause-page { max-width:64rem; margin:auto; padding:var(--bt-space-4); overflow-wrap:anywhere; }
    section, article { margin-block:var(--bt-space-4); }
    label { display:block; margin-top:var(--bt-space-3); }
    input, select, textarea { box-sizing:border-box; width:100%; max-width:32rem; min-height:44px; font:inherit; }
    button, a { display:inline-flex; align-items:center; min-height:44px; margin:var(--bt-space-2); font:inherit; }
    button { padding:var(--bt-space-2); cursor:pointer; }
    dialog { box-sizing:border-box; width:min(calc(100% - 2rem),32rem); max-height:calc(100% - 2rem); overflow:auto; background:var(--bt-surface); color:var(--bt-text); border:1px solid var(--bt-border-strong); padding:var(--bt-space-4); }
    dialog::backdrop { background:rgb(0 0 0 / 65%); }
    :focus-visible { outline:3px solid var(--bt-focus); outline-offset:3px; }
  `,
})
export class EmergencyPausePageComponent {
  readonly store = inject(EmergencyPauseStore);
  private readonly session = inject(SessionStore);
  private readonly dialog = viewChild<ElementRef<HTMLDialogElement>>('confirmation');
  readonly kind = signal<EmergencyPauseCommand.KindEnum>('ACCOUNT');
  readonly target = signal('');
  readonly reason = signal('');
  readonly command = signal<EmergencyPauseCommand | null>(null);
  readonly labels = { ACCOUNT: 'Conta', EVENT: 'Evento', MARKET: 'Mercado', CHANNEL: 'Canal' };
  readonly keepFocus = keepConfirmationFocus;
  private trigger: HTMLElement | null = null;

  constructor() {
    effect(() => {
      this.session.revision();
      const authenticated = this.session.isAuthenticated();
      untracked(() => {
        this.command.set(null);
        this.dialog()?.nativeElement.close();
        if (authenticated) void this.store.load();
      });
    });
  }

  impact() {
    switch (this.command()?.kind) {
      case 'ACCOUNT': return 'Conta: bloqueia novos palpites e mensagens; consulta e pedidos de revisao permanecem disponiveis.';
      case 'EVENT': return 'Evento: bloqueia novos palpites em todos os seus mercados. O chat tem uma pausa independente.';
      case 'MARKET': return 'Mercado: bloqueia somente novos palpites neste mercado.';
      case 'CHANNEL': return 'Canal: bloqueia novas mensagens; o historico continua legivel.';
      default: return '';
    }
  }

  prepare(pause?: EmergencyPauseState) {
    if (!this.store.administrator() || this.store.sending()) return;
    this.trigger = document.activeElement as HTMLElement;
    this.reason.set('');
    this.command.set({
      clientCommandId: crypto.randomUUID(), kind: pause?.kind ?? this.kind(),
      targetId: pause?.targetId ?? this.target().trim(), action: pause ? 'RESUME' : 'PAUSE',
      reason: '', ...(pause ? { resumedPauseId: pause.factId } : {}),
    });
    this.dialog()?.nativeElement.showModal();
  }

  cancel() {
    if (this.store.sending()) return;
    this.command.set(null);
    this.dialog()?.nativeElement.close();
    setTimeout(() => this.trigger?.focus());
  }

  async confirm() {
    const command = this.command();
    if (command && await this.store.command({ ...command, reason: this.reason().trim() })) this.cancel();
  }
}
