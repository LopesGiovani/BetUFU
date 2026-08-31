import { ChangeDetectionStrategy, Component, computed, input, signal } from '@angular/core';
import { SportsBracketMatchView } from '../../api/generated/model/sports-bracket-match-view';
import { SportsBracketSlotView } from '../../api/generated/model/sports-bracket-slot-view';
import { SportsBracketView } from '../../api/generated/model/sports-bracket-view';

@Component({
  selector: 'app-bracket-view',
  standalone: true,
  template: `
    @if (bracket(); as current) {
      <section class="bracket" [class.bracket--preview]="preview()" aria-label="Chaveamento esportivo">
        <header class="bracket__head">
          <div>
            <p class="eyebrow">{{ preview() ? 'Pré-visualização operacional' : 'Chaveamento publicado' }}</p>
            <h2>{{ current.name }}</h2>
            @if (current.phaseName) { <p class="context">{{ current.phaseName }}</p> }
          </div>
          <span class="status" [class.status--draft]="current.status !== 'PUBLICADO'">{{ statusLabel(current.status) }}</span>
        </header>

        @if (preview()) {
          <p class="preview-note" role="status">Pré-visualização não publicada. Nenhuma alteração é aplicada por esta tela.</p>
        }

        <nav class="round-switcher" aria-label="Selecionar rodada" role="tablist">
          @for (round of rounds(); track round.roundNo; let index = $index) {
            <button
              type="button"
              role="tab"
              [attr.aria-selected]="index === selectedRound()"
              [class.is-active]="index === selectedRound()"
              (click)="selectRound(index)">
              {{ round.roundName }}
            </button>
          }
        </nav>
        <div class="round-mobile">
          <label for="bracket-round-select">Rodada do chaveamento</label>
          <select id="bracket-round-select" [value]="selectedRound()" (change)="selectRoundFromEvent($event)">
            @for (round of rounds(); track round.roundNo; let index = $index) {
              <option [value]="index">{{ round.roundName }}</option>
            }
          </select>
          <div class="round-mobile__actions">
            <button type="button" [disabled]="selectedRound() === 0" (click)="selectRound(selectedRound() - 1)">Rodada anterior</button>
            <button type="button" [disabled]="selectedRound() >= rounds().length - 1" (click)="selectRound(selectedRound() + 1)">Próxima rodada</button>
          </div>
        </div>

        <div class="rounds">
          @for (round of rounds(); track round.roundNo; let index = $index) {
            <section class="round" [class.round--active]="index === selectedRound()">
              <h3>{{ round.roundName }}</h3>
              <div class="matches">
                @for (match of round.matches; track match.matchId) {
                  <article class="match" [class.match--third-place]="match.isThirdPlace" [attr.aria-label]="matchLabel(match)">
                    <header class="match__head">
                      <span>{{ match.isThirdPlace ? '3º lugar' : 'Jogo ' + match.matchNo }}</span>
                      <strong>{{ matchStatus(match) }}</strong>
                    </header>
                    <div class="slots">
                      @for (slot of match.slots; track slot.slotNo) {
                        <div class="slot" [class.slot--winner]="slot.isWinner === true">
                          <span class="slot__number">{{ slot.slotNo }}</span>
                          <div class="slot__body">
                            <strong>{{ slot.displayLabel || 'A definir' }}</strong>
                            @if (slot.teamName && slot.teamName !== slot.displayLabel) { <small>{{ slot.teamName }}</small> }
                            @if (slot.sourceType === 'BYE') { <small>Bye · avanço sem confronto</small> }
                            @else if (slot.sourceType !== 'DIRECT') { <small>{{ dependencyLabel(slot) }}</small> }
                          </div>
                          @if (slot.score !== undefined && slot.score !== null) { <b class="slot__score">{{ slot.score }}</b> }
                        </div>
                      }
                    </div>
                    @if (match.eventTitle) { <p class="match__event">{{ match.eventTitle }}</p> }
                    @if (match.scheduledAt) { <time [attr.datetime]="match.scheduledAt">{{ formatDate(match.scheduledAt, match.zoneId) }}</time> }
                  </article>
                }
              </div>
            </section>
          }
        </div>
      </section>
    } @else {
      <section class="bracket bracket--empty" aria-live="polite"><h2>Chaveamento sem confrontos</h2><p>A publicação ainda não possui rodadas para exibir.</p></section>
    }
  `,
  styles: `
    :host { display:block; min-width:0; }
    .bracket { color:var(--bt-text); padding:var(--bt-space-6); border:1px solid var(--bt-border); border-radius:var(--bt-radius-card); background:var(--bt-surface); }
    .bracket--preview { border-color:var(--bt-signal-soft); background:var(--bt-surface-raised); }
    .bracket__head { display:flex; align-items:flex-start; justify-content:space-between; gap:var(--bt-space-4); }
    .eyebrow { margin:0; color:var(--bt-signal-soft); font-size:var(--bt-type-label); letter-spacing:.08em; text-transform:uppercase; }
    h2,h3,p { overflow-wrap:anywhere; } h2 { margin:.3rem 0 0; color:var(--bt-display); font:600 var(--bt-type-heading)/.9 'Barlow Condensed',sans-serif; text-transform:uppercase; } h3 { margin:0 0 var(--bt-space-2); color:var(--bt-signal); font:600 1.45rem/.9 'Barlow Condensed',sans-serif; text-transform:uppercase; } .context { margin:.3rem 0 0; color:var(--bt-text-muted); }
    .status { flex:0 0 auto; padding:.35rem .6rem; border-radius:999px; background:var(--bt-signal); color:var(--bt-ink); font-size:var(--bt-type-label); font-weight:700; } .status--draft { background:var(--bt-warning, #ffc4c4); color:var(--bt-text); }
    .preview-note { margin:var(--bt-space-4) 0 0; padding:var(--bt-space-3); border-left:3px solid var(--bt-signal); color:var(--bt-text-muted); background:var(--bt-surface); }
    .round-switcher { display:flex; gap:.5rem; margin:var(--bt-space-6) 0 var(--bt-space-4); overflow-x:auto; padding-bottom:.25rem; } .round-switcher button { min-height:44px; flex:0 0 auto; padding:0 .9rem; border:1px solid var(--bt-border); border-radius:var(--bt-radius-field); background:transparent; color:var(--bt-text); font-weight:700; cursor:pointer; } .round-switcher button.is-active { border-color:var(--bt-signal); background:var(--bt-signal); color:var(--bt-ink); }
    .round-mobile { display:none; }
    .rounds { display:grid; grid-template-columns:repeat(auto-fit,minmax(230px,1fr)); gap:var(--bt-space-4); min-width:0; } .round { min-width:0; } .matches { display:grid; gap:var(--bt-space-3); } .match { min-width:0; padding:var(--bt-space-3); border:1px solid var(--bt-border); border-radius:var(--bt-radius-card); background:var(--bt-surface-raised); } .match--third-place { border-style:dashed; }
    .match__head { display:flex; justify-content:space-between; gap:.5rem; color:var(--bt-text-muted); font-size:var(--bt-type-label); } .match__head strong { color:var(--bt-signal); font-size:var(--bt-type-label); } .slots { display:grid; gap:.35rem; margin-top:.6rem; } .slot { display:grid; grid-template-columns:24px minmax(0,1fr) auto; align-items:center; gap:.45rem; min-height:48px; padding:.45rem; border:1px solid var(--bt-border); border-radius:var(--bt-radius-field); background:var(--bt-surface); } .slot--winner { border-color:var(--bt-signal); } .slot__number { display:grid; width:24px; height:24px; place-items:center; border-radius:50%; background:var(--bt-signal-soft); color:var(--bt-ink); font-size:.75rem; font-weight:700; } .slot__body { min-width:0; } .slot__body strong,.slot__body small { display:block; overflow-wrap:anywhere; } .slot__body small,.match__event,time { color:var(--bt-text-muted); font-size:var(--bt-type-label); } .slot__score { color:var(--bt-signal); font:600 1.6rem/.8 'Barlow Condensed',sans-serif; } .match__event { margin:.55rem 0 0; color:var(--bt-text-muted); font-size:var(--bt-type-label); } time { display:block; margin-top:.25rem; }
    .bracket--empty { min-height:240px; } .bracket--empty p { color:var(--bt-text-muted); }
    @media (max-width:680px) { .bracket { padding:var(--bt-space-4); } .bracket__head { display:grid; } .status { width:max-content; } .round-switcher { display:none; } .round-mobile { display:grid; gap:.45rem; margin:var(--bt-space-6) 0 var(--bt-space-4); } .round-mobile label { font-weight:700; } .round-mobile select { width:100%; min-height:44px; padding:0 .65rem; border:1px solid var(--bt-border); border-radius:var(--bt-radius-field); background:var(--bt-surface); color:var(--bt-text); } .round-mobile__actions { display:grid; grid-template-columns:1fr 1fr; gap:.5rem; } .round-mobile__actions button { min-height:44px; border:1px solid var(--bt-border); border-radius:var(--bt-radius-field); background:var(--bt-surface); color:var(--bt-text); font-weight:700; } .round-mobile__actions button:disabled { opacity:.5; } .rounds { display:block; } .round { display:none; } .round--active { display:block; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BracketViewComponent {
  readonly bracket = input<SportsBracketView | null>(null);
  readonly preview = input(false);
  readonly selectedRound = signal(0);
  readonly rounds = computed(() => this.bracket()?.rounds ?? []);

  selectRound(index: number): void {
    if (index >= 0 && index < this.rounds().length) this.selectedRound.set(index);
  }

  selectRoundFromEvent(event: Event): void {
    const target = event.target;
    if (target instanceof HTMLSelectElement) this.selectRound(Number(target.value));
  }

  statusLabel(status: string): string { return status === 'PUBLICADO' ? 'Publicado' : 'Rascunho'; }
  matchStatus(match: SportsBracketMatchView): string {
    if (match.winnerSlotNo !== undefined && match.winnerSlotNo !== null) return 'Confirmado';
    return match.status === 'PUBLICADO' ? 'Publicado'
      : match.status === 'SUSPENSO' ? 'Suspenso'
        : match.status === 'ADIADO' ? 'Adiado'
          : match.status === 'CANCELADO' ? 'Cancelado' : 'Pendente';
  }
  dependencyLabel(slot: SportsBracketSlotView): string { return slot.sourceType === 'PREDECESSOR_WINNER' ? 'Vencedor do jogo anterior' : 'Perdedor do jogo anterior'; }
  matchLabel(match: SportsBracketMatchView): string { return `${match.roundName}, jogo ${match.matchNo}`; }
  formatDate(value: string, timeZone?: string): string {
    return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short', timeZone: timeZone || undefined }).format(new Date(value));
  }
}
