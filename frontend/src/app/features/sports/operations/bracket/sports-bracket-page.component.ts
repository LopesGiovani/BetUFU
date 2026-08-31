import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { DatePipe } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { SportsBracketMatchConfigureRequest } from '../../../../api/generated/model/sports-bracket-match-configure-request';
import { SportsBracketSlotConfigureRequest } from '../../../../api/generated/model/sports-bracket-slot-configure-request';
import { SportsBracketUpdateRequest } from '../../../../api/generated/model/sports-bracket-update-request';
import { BracketViewComponent } from '../../../../shared/sports/bracket-view.component';
import { SportsBracketStore } from './sports-bracket.store';

@Component({
  selector: 'app-sports-bracket-page',
  standalone: true,
  imports: [RouterLink, ReactiveFormsModule, DatePipe, BracketViewComponent],
  template: `
    <main class="operations-bracket" [attr.aria-busy]="store.isLoading() || store.isMutating()">
      <a class="back-link" routerLink="/operacao/esportes">← Voltar para operação esportiva</a>
      @if (store.isLoading()) { <section class="state" role="status"><h1>Carregando chaveamento e classificação</h1><p>Buscando o preview do escopo autorizado.</p></section> }
      @else if (store.isDenied()) { <section class="state state--error"><h1>Acesso não autorizado</h1><p>{{ store.errorMessage() }}</p></section> }
      @else if (store.isNotFound()) { <section class="state"><h1>Chaveamento não encontrado</h1><p>{{ store.errorMessage() }}</p></section> }
      @else if (store.isError()) { <section class="state state--error"><h1>Não foi possível carregar chaveamento e classificação</h1><p>{{ store.errorMessage() }}</p><button class="action" type="button" (click)="store.retry()">Tentar carregar chaveamento e classificação</button></section> }
      @else if (bracket(); as current) {
        <header class="detail-head"><p class="eyebrow">O7 · Operação esportiva</p><h1>{{ current.name }}</h1><p>{{ current.competitionName || 'Escopo autorizado' }}{{ current.editionName ? ' · ' + current.editionName : '' }}</p></header>
        @if (store.conflictMessage(); as conflict) { <p class="feedback feedback--error" role="alert">{{ conflict }}</p> }
        @if (store.feedbackMessage(); as feedback) { <p class="feedback" role="status">{{ feedback }}</p> }
        @if (current.status === 'RASCUNHO') {
          <section class="link-panel" aria-labelledby="link-title">
            <div class="section-head"><div><h2 id="link-title">Ligar eventos existentes</h2><p>Escolha pelo nome um evento já cadastrado nesta edição. Participantes e placares continuam derivados do evento.</p></div><span>{{ store.events().length }} eventos disponíveis</span></div>
            @if (store.events().length === 0) { <p class="empty">Nenhum evento cadastrado para esta edição. Cadastre o evento em O6 antes de ligar o chaveamento.</p> }
            @else {
              <div class="match-links">
                @for (match of matches(); track match.matchId) {
                  <label class="match-link"><span>{{ match.roundName }} · Jogo {{ match.matchNo }}{{ match.isThirdPlace ? ' · 3º lugar' : '' }}</span><select [value]="eventLinks()[match.matchId] || ''" (change)="linkEvent(match.matchId, $event)"><option value="">Sem evento ligado</option>@for (event of store.events(); track event.eventId) { <option [value]="event.eventId">{{ event.title }} · {{ event.scheduledAt | date:'short' }}</option> }</select></label>
                }
              </div>
              <button class="action" type="button" [disabled]="store.isMutating()" (click)="saveLinks()">{{ store.isMutating() ? 'Salvando…' : 'Salvar rascunho' }}</button>
            }
          </section>
          <form class="source-form" [formGroup]="publishForm" (ngSubmit)="publish()" aria-labelledby="publish-title"><h2 id="publish-title">Publicar chaveamento</h2><label for="source-reference">Fonte oficial</label><input id="source-reference" type="url" formControlName="sourceReference" placeholder="https://..." autocomplete="url"><small>Informe uma fonte oficial válida antes de publicar.</small><button class="action" type="submit" [disabled]="publishForm.invalid || store.isMutating()">Publicar chaveamento</button></form>
        } @else {
          <form class="source-form" [formGroup]="correctionForm" (ngSubmit)="correct()" aria-labelledby="correction-title"><h2 id="correction-title">Corrigir versão publicada</h2><label for="correction-reason">Motivo da correção</label><textarea id="correction-reason" formControlName="reason" rows="3"></textarea><label for="correction-source">Fonte substituta</label><input id="correction-source" type="url" formControlName="sourceReference" autocomplete="url"><button class="action" type="submit" [disabled]="correctionForm.invalid || store.isMutating()">Publicar nova versão</button></form>
        }
        <app-bracket-view [bracket]="current" [preview]="current.status !== 'PUBLICADO'" />
      } @else { <section class="state"><h1>Nenhum chaveamento configurado</h1><p>Ligue eventos existentes às rodadas e dependências antes de publicar o chaveamento.</p></section> }
    </main>
  `,
  styles: `:host{display:block;min-width:0}.operations-bracket{width:var(--bt-container);margin-inline:auto;padding:var(--bt-space-8) 0 var(--bt-space-16);color:var(--bt-text)}.back-link{color:var(--bt-signal);font-weight:700;text-decoration:none}.detail-head{margin:var(--bt-space-8) 0 var(--bt-space-4);padding:var(--bt-space-6);border-left:4px solid var(--bt-signal);background:var(--bt-surface-raised)}.eyebrow{margin:0;color:var(--bt-signal-soft);font-size:var(--bt-type-label);letter-spacing:.08em;text-transform:uppercase}.detail-head h1{margin:.3rem 0;color:var(--bt-display);font:600 var(--bt-type-display)/.9 'Barlow Condensed',sans-serif;text-transform:uppercase}.detail-head p{overflow-wrap:anywhere}.link-panel,.source-form{margin-bottom:var(--bt-space-4);padding:var(--bt-space-5);border:1px solid var(--bt-border);background:var(--bt-surface-raised)}.section-head{display:flex;justify-content:space-between;gap:var(--bt-space-4)}h2{margin:0;color:var(--bt-display);font:600 var(--bt-type-heading)/.9 'Barlow Condensed',sans-serif;text-transform:uppercase}.section-head p{margin:.35rem 0;color:var(--bt-text-muted)}.section-head span{color:var(--bt-signal-soft);font-size:var(--bt-type-label)}.match-links{display:grid;gap:.6rem;margin:var(--bt-space-4) 0}.match-link{display:grid;grid-template-columns:minmax(0,1fr) minmax(220px,1fr);align-items:center;gap:var(--bt-space-3);padding:.6rem;border:1px solid var(--bt-border);border-radius:var(--bt-radius-field)}.match-link span{font-weight:700;overflow-wrap:anywhere}.match-link select,.source-form input,.source-form textarea{width:100%;min-height:44px;padding:.6rem;border:1px solid var(--bt-border);border-radius:var(--bt-radius-field);background:var(--bt-surface);color:var(--bt-text);font:inherit}.source-form{display:grid;gap:.55rem}.source-form small{color:var(--bt-text-muted)}.action{width:max-content;min-height:44px;padding:0 var(--bt-space-4);border:0;border-radius:var(--bt-radius-field);background:var(--bt-signal);color:var(--bt-ink);font-weight:700;cursor:pointer}.action:disabled{opacity:.5;cursor:not-allowed}.feedback{padding:.7rem;border-left:3px solid var(--bt-signal);background:var(--bt-surface-raised)}.feedback--error{border-color:var(--bt-danger)}.empty{margin:var(--bt-space-4) 0;color:var(--bt-text-muted)}.state{display:grid;min-height:320px;margin-top:var(--bt-space-8);padding:var(--bt-space-8);align-content:center;gap:var(--bt-space-2);border:1px solid var(--bt-border);background:var(--bt-surface-raised)}.state h1{margin:0;color:var(--bt-display);font:600 var(--bt-type-heading)/.9 'Barlow Condensed',sans-serif}.state p{margin:0;color:var(--bt-text-muted)}.state--error{border-color:var(--bt-danger)}@media(max-width:680px){.operations-bracket{padding-inline:var(--bt-space-4)}.detail-head{padding:var(--bt-space-4)}.section-head,.match-link{display:grid}.match-link select{min-width:0}.action{width:100%}}`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SportsBracketPageComponent implements OnInit {
  readonly store = inject(SportsBracketStore);
  private readonly route = inject(ActivatedRoute, { optional: true });
  readonly bracket = computed(() => this.store.data());
  readonly matches = computed(() => this.bracket()?.rounds.flatMap((round) => round.matches) ?? []);
  readonly eventLinks = signal<Record<string, string>>({});
  readonly publishForm = new FormGroup({ sourceReference: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(2048), Validators.pattern(/^https?:\/\//u)] }) });
  readonly correctionForm = new FormGroup({ reason: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(1024)] }), sourceReference: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.maxLength(2048), Validators.pattern(/^https?:\/\//u)] }) });

  async ngOnInit(): Promise<void> {
    const id = this.route?.snapshot.paramMap.get('bracketId');
    if (id) await this.store.load(id);
    const current = this.bracket();
    if (current) this.eventLinks.set(Object.fromEntries(current.rounds.flatMap((round) => round.matches).map((match) => [match.matchId, match.eventId ?? ''])));
  }

  linkEvent(matchId: string, event: Event): void { const target = event.target; if (target instanceof HTMLSelectElement) this.eventLinks.update((links) => ({ ...links, [matchId]: target.value })); }

  async saveLinks(): Promise<void> {
    const current = this.bracket(); if (!current) return;
    const matches: SportsBracketMatchConfigureRequest[] = current.rounds.flatMap((round) => round.matches).map((match) => ({
      matchId: match.matchId, roundNo: match.roundNo, matchNo: match.matchNo, roundName: match.roundName,
      eventId: this.eventLinks()[match.matchId] || undefined, isThirdPlace: match.isThirdPlace,
       slots: match.slots.map((slot): SportsBracketSlotConfigureRequest => ({ slotNo: slot.slotNo, sourceType: slot.sourceType, sourceMatchId: slot.sourceMatchId, seedLabel: undefined })),
    }));
    await this.store.update({ name: current.name, slug: current.slug, sportId: current.sportId, categoryId: current.categoryId, phaseId: current.phaseId, matches });
  }

  async publish(): Promise<void> { if (this.publishForm.valid) await this.store.publish(this.publishForm.controls.sourceReference.value); }
  async correct(): Promise<void> { if (this.correctionForm.valid) await this.store.correct(this.correctionForm.controls.reason.value, this.correctionForm.controls.sourceReference.value); }
}
