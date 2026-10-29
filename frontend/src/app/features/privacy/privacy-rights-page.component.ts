import { ChangeDetectionStrategy, Component, ElementRef, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { SessionStore } from '../../core/session/session.store';
import type { PrivacyRequest } from '../../api/generated/model/privacy-request';
import type { PrivacyResponseCommand } from '../../api/generated/model/privacy-response-command';
import { PrivacyRightsStore } from './privacy-rights.store';
import { keepConfirmationFocus } from '../chat/chat-moderation-page.component';

@Component({
  selector: 'app-privacy-rights-page', standalone: true, imports: [FormsModule],
  providers: [PrivacyRightsStore], changeDetection: ChangeDetectionStrategy.OnPush,
  template: `
    <section class="privacy-page" aria-labelledby="privacy-title">
      <h1 id="privacy-title">{{ operational() ? 'Operacao de privacidade' : 'Privacidade da conta' }}</h1>
      <p>Solicite informacoes, acesso, correcao, exclusao, oposicao ou pseudonimizacao dos seus dados.</p>
      <p>Um pedido nao apaga fatos automaticamente. A operacao verifica o alcance e registra uma resposta que voce pode acompanhar aqui.</p>
      <details><summary>Aviso de privacidade e retencao</summary>
        <p>Mensagens comuns: 90 dias. Evidencias de moderacao: 180 dias apos encerramento do caso.
          Casos abertos, revisoes e impedimentos de descarte preservam as evidencias.
          Sancoes e auditoria minima sao preservadas. O descarte das demais categorias aguarda politica especifica.</p>
        <p>A BETUFU e um projeto independente, sem vinculo oficial com a UFU. Os palpites usam creditos virtuais.</p>
      </details>
      @if (store.busy()) { <p role="status">Carregando ou registrando a operacao...</p> }
      @if (store.error()) { <p role="alert">{{ store.error() }}</p> }
      @if (!operational()) {
        <form (submit)="$event.preventDefault(); prepare('request')">
          <h2>Novo pedido</h2>
          <label for="privacy-kind">Direito solicitado</label>
          <select id="privacy-kind" name="kind" [ngModel]="requestKind()" (ngModelChange)="requestKind.set($event)" [disabled]="store.busy()">
            @for (item of kinds; track item.value) { <option [value]="item.value">{{ item.label }}</option> }
          </select>
          <button type="submit" [disabled]="store.busy()">Solicitar direito</button>
        </form>
        <h2>Meus pedidos</h2>
        <button type="button" (click)="store.load()" [disabled]="store.busy()">Atualizar pedidos</button>
        @for (item of store.cases(); track item.caseId) {
          <article><h3>{{ kindLabel(item.requestKind) }}</h3>
            <p>Pedido <span>{{ item.caseId }}</span></p>
            <p>{{ states[item.state] }} · <time [attr.datetime]="item.requestedAt">{{ item.requestedAt }}</time></p>
            <button type="button" (click)="store.open(item.caseId)" [disabled]="store.busy()">Acompanhar pedido {{ item.caseId }}</button>
          </article>
        } @empty { @if (!store.busy() && !store.error()) { <p>Nenhum pedido nesta pagina.</p> } }
        <button type="button" (click)="store.load(store.offset() - 50)" [disabled]="store.busy() || store.offset() === 0">Pedidos anteriores</button>
        <button type="button" (click)="store.load(store.offset() + 50)" [disabled]="store.busy() || store.cases().length < 50">Proxima pagina</button>
      } @else {
        <button type="button" (click)="store.start(true)" [disabled]="store.busy()">Atualizar acesso operacional</button>
        @if (store.administrator()) {
          <form (submit)="$event.preventDefault(); store.open(caseId().trim())">
            <label for="privacy-case">Identificador do pedido</label>
            <input id="privacy-case" name="case" [ngModel]="caseId()" (ngModelChange)="caseId.set($event)" />
            <button type="submit" [disabled]="store.busy()">Consultar pedido</button>
          </form>
          <details><summary>Atleta sem conta</summary>
            <p>Confira a identidade fora do sistema. Registre apenas a referencia restrita da comprovacao e responda pelo canal verificado; esta tela nao abre acesso anonimo.</p>
            <label for="privacy-athlete">Identificador do atleta</label>
            <input id="privacy-athlete" [ngModel]="athleteId()" (ngModelChange)="athleteId.set($event)" autocomplete="off" />
            <label for="privacy-proof">Referencia restrita da comprovacao</label>
            <input id="privacy-proof" maxlength="280" [ngModel]="proof()" (ngModelChange)="proof.set($event)" autocomplete="off" />
            <label><input type="checkbox" [ngModel]="verified()" (ngModelChange)="verified.set($event)" /> Conferi a identidade fora do sistema e o canal de resposta</label>
            <label for="athlete-kind">Direito do atleta</label>
            <select id="athlete-kind" [ngModel]="requestKind()" (ngModelChange)="requestKind.set($event)">
              @for (item of kinds; track item.value) { <option [value]="item.value">{{ item.label }}</option> }
            </select>
            <button type="button" (click)="prepare('request')" [disabled]="store.busy() || !verified()">Abrir pedido verificado</button>
          </details>
        }
      }
      @if (store.selected(); as item) {
        <section aria-labelledby="privacy-detail">
          <h2 id="privacy-detail">Acompanhamento do pedido</h2>
          <p>Protocolo: <span>{{ item.caseId }}</span></p>
          <p>{{ kindLabel(item.requestKind) }} · <strong>{{ states[item.state] }}</strong></p>
          <button type="button" (click)="store.open(item.caseId)" [disabled]="store.busy()">Atualizar pedido</button>
          <ol>@for (event of item.timeline; track $index) {
            <li><span>{{ events[event.eventKind] }}</span> · <time [attr.datetime]="event.occurredAt">{{ event.occurredAt }}</time>
              @if (event.responseText) { <p class="response-text">{{ event.responseText }}</p> }
            </li>
          }</ol>
          @if (item.requestKind === 'ACCESS') {
            <p>Uma exportacao parcial informa categorias pendentes e nao conclui o pedido de acesso.</p>
            @if (item.timeline.some(hasExport)) { <button type="button" (click)="saveExport()" [disabled]="store.busy()">Baixar exportacao privada</button> }
          }
          @if (operational() && store.administrator() && item.state !== 'RESPONDED' && item.state !== 'REJECTED') {
            @if (item.requestKind === 'ACCESS') { <button type="button" (click)="prepare('export')" [disabled]="store.busy()">Preparar exportacao restrita</button> }
            <label for="privacy-decision">Decisao operacional</label>
            <select id="privacy-decision" [ngModel]="decision()" (ngModelChange)="decision.set($event)">
              <option value="ANALYZE">Em analise</option><option value="REJECT">Indeferir com resposta</option><option value="APPROVE">Responder com cumprimento comprovado</option>
            </select>
            <label for="privacy-response">Resposta ao titular</label>
            <textarea id="privacy-response" maxlength="2000" [ngModel]="response()" (ngModelChange)="response.set($event)"></textarea>
            <p>O servidor exige prova de cumprimento para concluir o pedido. Registrar analise nao concede aprovacao.</p>
            <button type="button" (click)="prepare('respond')" [disabled]="store.busy() || !response().trim()">Registrar resposta</button>
          }
          @if (store.artifact(); as artifact) {
            <p role="status">{{ artifact.complete ? 'Exportacao completa.' : 'Exportacao parcial; pedido de acesso ainda nao cumprido.' }}</p>
            @if (artifact.blockedClasses.length) { <p>Categorias pendentes: {{ artifact.blockedClasses.join(', ') }}</p> }
          }
        </section>
      }
      <dialog #confirmation tabindex="-1" aria-labelledby="privacy-confirm-title" aria-describedby="privacy-confirm-description"
        (cancel)="$event.preventDefault(); cancel()" (keydown)="keepFocus($event)">
        <h2 id="privacy-confirm-title">Confirmar operacao de privacidade</h2>
        <p id="privacy-confirm-description">{{ action() === 'request' ? 'Registrar um pedido nao apaga fatos automaticamente.' : action() === 'export' ? 'Preparar dados restritos do titular. Uma exportacao parcial nao conclui o acesso.' : 'Registrar esta resposta na trilha permanente do pedido.' }}</p>
        @if (store.error()) { <p role="alert">{{ store.error() }}</p> }
        <button type="button" (click)="cancel()" [disabled]="store.busy()">Cancelar</button>
        <button type="button" (click)="confirm()" [disabled]="store.busy()">Confirmar operacao</button>
      </dialog>
    </section>
  `,
  styles: `
    :host { display:block; min-width:0; color:var(--bt-text); }
    .privacy-page { max-width:64rem; margin:auto; padding:var(--bt-space-4); overflow-wrap:anywhere; }
    article, details, form, section section { margin-block:var(--bt-space-4); }
    label { display:block; margin-top:var(--bt-space-3); }
    input:not([type=checkbox]), select, textarea { box-sizing:border-box; width:100%; max-width:32rem; min-height:44px; font:inherit; }
    input[type=checkbox] { min-width:24px; min-height:24px; } label:has(input[type=checkbox]) { min-height:44px; }
    button, summary { min-height:44px; padding:var(--bt-space-2); margin-block:var(--bt-space-2); font:inherit; cursor:pointer; }
    button { max-width:100%; overflow-wrap:anywhere; } .response-text { white-space:pre-wrap; }
    dialog { box-sizing:border-box; width:min(calc(100% - 2rem),32rem); max-height:calc(100% - 2rem); overflow:auto; background:var(--bt-surface); color:var(--bt-text); border:1px solid var(--bt-border-strong); padding:var(--bt-space-4); }
    dialog::backdrop { background:rgb(0 0 0 / 65%); } :focus-visible { outline:3px solid var(--bt-focus); outline-offset:3px; }
  `,
})
export class PrivacyRightsPageComponent {
  readonly store = inject(PrivacyRightsStore);
  readonly operational = input(false);
  private readonly session = inject(SessionStore);
  private readonly dialog = viewChild<ElementRef<HTMLDialogElement>>('confirmation');
  private trigger: HTMLElement | null = null;
  readonly keepFocus = keepConfirmationFocus;
  readonly action = signal<'request' | 'respond' | 'export' | null>(null);
  readonly requestKind = signal<PrivacyRequest.RequestKindEnum>('ACCESS');
  readonly decision = signal<PrivacyResponseCommand.DecisionEnum>('ANALYZE');
  readonly caseId = signal(''); readonly athleteId = signal(''); readonly proof = signal('');
  readonly verified = signal(false); readonly response = signal('');
  readonly kinds: readonly { value: PrivacyRequest.RequestKindEnum; label: string }[] = [
    { value: 'NOTICE', label: 'Informacoes sobre meus dados' }, { value: 'ACCESS', label: 'Acesso aos dados' },
    { value: 'CORRECTION', label: 'Correcao' }, { value: 'DELETION', label: 'Exclusao' },
    { value: 'OBJECTION', label: 'Oposicao' }, { value: 'PSEUDONYMIZATION', label: 'Pseudonimizacao' },
  ];
  readonly states = { PENDING: 'Pendente', UNDER_REVIEW: 'Em analise', RESPONDED: 'Respondido', REJECTED: 'Indeferido' };
  readonly events = { REQUESTED: 'Pedido registrado', IDENTITY_VERIFIED: 'Identidade verificada', ANALYZED: 'Analise registrada', RESPONSE_SENT: 'Resposta registrada', EXPORT_READY: 'Exportacao preparada', DISPOSITIONED: 'Tratamento registrado', PSEUDONYMIZED: 'Pseudonimizacao registrada', REJECTED: 'Indeferimento registrado' };
  readonly hasExport = (event: { eventKind: string }) => event.eventKind === 'EXPORT_READY';
  constructor() {
    effect(() => { const mode = this.operational(); untracked(() => void this.store.start(mode)); });
    effect(() => {
      this.session.revision(); this.session.isAuthenticated();
      untracked(() => { this.action.set(null); this.dialog()?.nativeElement.close(); this.response.set(''); this.proof.set(''); this.athleteId.set(''); this.caseId.set(''); this.verified.set(false); });
    });
  }
  kindLabel(kind: string) { return this.kinds.find(item => item.value === kind)?.label ?? 'Pedido de privacidade'; }
  prepare(action: 'request' | 'respond' | 'export') {
    if (this.store.busy() || (this.operational() && !this.store.administrator())) return;
    this.trigger = document.activeElement as HTMLElement; this.action.set(action); this.dialog()?.nativeElement.showModal();
  }
  cancel() {
    if (this.store.busy()) return; this.action.set(null); this.dialog()?.nativeElement.close(); setTimeout(() => this.trigger?.focus());
  }
  async confirm() {
    const action = this.action();
    const ok = action === 'request' ? await this.store.request(this.requestKind(), this.operational()
      ? { athleteId: this.athleteId().trim(), proof: this.proof(), verified: this.verified() } : undefined)
      : action === 'respond' ? await this.store.respond(this.decision(), this.response())
      : action === 'export' ? await this.store.prepareExport() : false;
    if (ok) this.cancel();
  }
  async saveExport() {
    if (!await this.store.download()) return;
    const artifact = this.store.artifact(); if (!artifact) return;
    const url = URL.createObjectURL(new Blob([JSON.stringify(artifact, null, 2)], { type: 'application/json' }));
    const link = document.createElement('a'); link.href = url; link.download = 'betufu-dados-privados.json'; link.click(); setTimeout(() => URL.revokeObjectURL(url));
  }
}
