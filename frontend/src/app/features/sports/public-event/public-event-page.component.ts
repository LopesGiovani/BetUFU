import { ChangeDetectionStrategy, Component, OnInit, computed, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { PublicEventDetail } from '../../../api/generated/model/public-event-detail';
import { SportsPublicViewStore } from '../public-view/sports-public-view.store';

@Component({
  selector: 'app-public-event-page',
  standalone: true,
  imports: [RouterLink],
  template: `
    <main class="public-detail" [attr.aria-busy]="store.isLoading()">
      <a class="back-link" routerLink="/">← Voltar para a agenda</a>
      @if (store.isLoading()) {
        <section class="state" role="status">
          <span class="spinner" aria-hidden="true"></span>
          <h1>Carregando evento</h1>
          <p>Buscando a publicação esportiva.</p>
        </section>
      } @else if (store.isNotFound()) {
        <section class="state">
          <h1>Evento não encontrado</h1>
          <p>Este evento não possui uma publicação pública disponível.</p>
          <a class="action" routerLink="/">Ver agenda</a>
        </section>
      } @else if (store.isError()) {
        <section class="state state--error">
          <h1>Evento indisponível</h1>
          <p>{{ store.errorMessage() }}</p>
          <button class="action" type="button" (click)="store.retry()">Tentar novamente</button>
        </section>
      } @else if (detail(); as event) {
        <header class="detail-head">
          <p class="eyebrow">
            Evento publicado · {{ event.format === 'HEAD_TO_HEAD' ? 'Confronto' : 'Agenda básica' }}
          </p>
          <h1>{{ title(event) }}</h1>
          <p class="context">{{ event.competitionName }} · {{ event.editionName }}</p>
          <p class="context">
            {{ event.sportName || 'Esporte publicado'
            }}{{ event.categoryName ? ' · ' + event.categoryName : ''
            }}{{ event.phaseName ? ' · ' + event.phaseName : '' }}
          </p>
        </header>
        <section class="facts" aria-label="Informações do evento">
          <article>
            <span>Horário</span
            ><strong>{{ formatDateTime(event.scheduledAt, event.zoneId) }}</strong
            ><small>{{ event.zoneId }}</small>
          </article>
          <article>
            <span>Situação</span><strong>{{ statusLabel(event.status) }}</strong>
            @if (event.statusReason) {
              <small>{{ event.statusReason }}</small>
            }
          </article>
          <article>
            <span>Local</span><strong>{{ venueLabel(event) }}</strong>
            @if (event.venueAddress) {
              <small>{{ event.venueAddress }}</small>
            }
          </article>
        </section>
        <section class="section" aria-labelledby="slots-title">
          <div class="section-head">
            <h2 id="slots-title">Participantes</h2>
            <span
              >{{ event.slots.length }}
              {{ event.slots.length === 1 ? 'participante' : 'participantes' }}</span
            >
          </div>
          <div class="slots">
            @for (slot of event.slots; track slot.slotNo) {
              <article class="slot">
                <span>{{ slot.slotNo }}</span
                ><strong>{{ slot.displayLabel }}</strong>
              </article>
            }
          </div>
        </section>
        @if (event.result; as result) {
          <section class="result" aria-labelledby="result-title">
            <div class="section-head">
              <h2 id="result-title">Resultado confirmado</h2>
              <span>Versão {{ result.versionNo }}</span>
            </div>
            <div class="scoreboard">
              @for (score of result.scores; track score.slotNo) {
                <div>
                  <strong>{{ score.score }}</strong
                  ><span>{{ score.displayLabel }}</span>
                </div>
              }
            </div>
            <p>Resultado confirmado em {{ formatDateTime(result.confirmedAt, event.zoneId) }}.</p>
          </section>
        } @else {
          <section class="empty">
            <h2>Resultado ainda não publicado</h2>
            <p>O placar aparecerá aqui quando houver uma confirmação pública.</p>
          </section>
        }
        <section class="section" aria-labelledby="source-title">
          <div class="section-head">
            <h2 id="source-title">Fonte publicada</h2>
            <span>{{ event.sourceReference }}</span>
          </div>
          <p class="source-current">
            Fonte vigente: <strong>{{ event.sourceReference }}</strong> · publicada em
            {{ formatDateTime(event.publishedAt, event.zoneId) }}.
          </p>
          <details>
            <summary>Ver histórico de fontes</summary>
            @if (event.sourceHistory.length === 0) {
              <p>
                Nenhuma fonte anterior. A fonte atual é a única fonte publicada para este evento.
              </p>
            } @else {
              <p>
                {{ event.sourceHistory.length }}
                {{ event.sourceHistory.length === 1 ? 'fonte anterior' : 'fontes anteriores' }}.
              </p>
              <ol>
                @for (source of event.sourceHistory; track source.versionNo) {
                  <li>
                    Versão {{ source.versionNo }} · {{ source.sourceReference
                    }}{{ source.reason ? ' · ' + source.reason : '' }}
                  </li>
                }
              </ol>
            }
          </details>
        </section>
      } @else {
        <section class="state">
          <h1>Evento sem conteúdo</h1>
          <p>A publicação ainda não possui detalhes para exibir.</p>
        </section>
      }
    </main>
  `,
  styles: `
    :host {
      display: block;
      min-width: 0;
    }
    .public-detail {
      width: var(--bt-container);
      margin-inline: auto;
      padding: var(--bt-space-8) 0 var(--bt-space-16);
      color: var(--bt-text);
    }
    .back-link {
      color: var(--bt-signal);
      font-weight: 600;
      text-decoration: none;
    }
    .detail-head {
      margin-top: var(--bt-space-8);
      padding: var(--bt-space-8);
      border-left: 4px solid var(--bt-signal);
      background: var(--bt-surface-raised);
    }
    .eyebrow,
    .context {
      margin: 0;
      color: var(--bt-signal-soft);
      font-size: var(--bt-type-label);
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }
    h1,
    h2,
    p {
      overflow-wrap: anywhere;
    }
    h1 {
      margin: var(--bt-space-2) 0;
      color: var(--bt-display);
      font:
        600 var(--bt-type-display)/0.9 'Barlow Condensed',
        sans-serif;
      text-transform: uppercase;
    }
    h2 {
      margin: 0;
      font:
        600 var(--bt-type-heading)/0.9 'Barlow Condensed',
        sans-serif;
      text-transform: uppercase;
    }
    .context + .context {
      margin-top: 0.35rem;
    }
    .facts {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: var(--bt-space-2);
      margin-top: var(--bt-space-4);
    }
    .facts article,
    .slot,
    .result,
    .empty {
      padding: var(--bt-space-4);
      border: 1px solid var(--bt-border);
      border-radius: var(--bt-radius-card);
      background: var(--bt-surface);
    }
    .facts span,
    .facts small {
      display: block;
      color: var(--bt-text-muted);
      font-size: var(--bt-type-label);
    }
    .facts strong {
      display: block;
      margin: 0.35rem 0;
      color: var(--bt-signal);
    }
    .section {
      margin-top: var(--bt-space-8);
    }
    .section-head {
      display: flex;
      justify-content: space-between;
      gap: var(--bt-space-4);
      margin-bottom: var(--bt-space-2);
    }
    .section-head span {
      color: var(--bt-signal-soft);
      font-size: var(--bt-type-label);
    }
    .slots {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
      gap: var(--bt-space-2);
    }
    .slot {
      display: flex;
      align-items: center;
      gap: var(--bt-space-4);
    }
    .slot span {
      display: grid;
      width: 32px;
      height: 32px;
      place-items: center;
      border-radius: 50%;
      background: var(--bt-signal);
      color: var(--bt-ink);
      font-weight: 700;
    }
    .result {
      margin-top: var(--bt-space-8);
      border-color: var(--bt-signal);
    }
    .scoreboard {
      display: flex;
      flex-wrap: wrap;
      gap: var(--bt-space-4);
    }
    .scoreboard div {
      display: grid;
      grid-template-columns: auto 1fr;
      align-items: center;
      gap: var(--bt-space-2);
    }
    .scoreboard strong {
      color: var(--bt-signal);
      font:
        600 var(--bt-type-display)/0.8 'Barlow Condensed',
        sans-serif;
    }
    .scoreboard span {
      font-weight: 600;
    }
    .result p,
    .source-current,
    details p {
      color: var(--bt-text-muted);
    }
    details {
      margin-top: var(--bt-space-4);
      padding: var(--bt-space-4);
      border: 1px solid var(--bt-border);
    }
    summary {
      cursor: pointer;
      color: var(--bt-signal);
      font-weight: 700;
    }
    ol {
      margin-bottom: 0;
    }
    .empty {
      margin-top: var(--bt-space-8);
    }
    .empty h2 {
      font-size: 1.3rem;
    }
    .empty p {
      color: var(--bt-text-muted);
    }
    .state {
      display: grid;
      min-height: 360px;
      margin-top: var(--bt-space-8);
      padding: var(--bt-space-8);
      align-content: center;
      gap: var(--bt-space-2);
      border: 1px solid var(--bt-border);
      background: var(--bt-surface-raised);
    }
    .state h1 {
      margin: 0;
      font-size: var(--bt-type-heading);
    }
    .state p {
      max-width: 60ch;
      margin: 0;
      color: var(--bt-text-muted);
    }
    .state--error {
      border-color: var(--bt-danger);
    }
    .action {
      width: max-content;
      min-height: 44px;
      padding: 0 var(--bt-space-4);
      display: inline-flex;
      align-items: center;
      border: 0;
      border-radius: var(--bt-radius-field);
      background: var(--bt-signal);
      color: var(--bt-ink);
      font-weight: 700;
      text-decoration: none;
      cursor: pointer;
    }
    .spinner {
      width: 22px;
      height: 22px;
      border: 2px solid var(--bt-border);
      border-top-color: var(--bt-signal);
      border-radius: 50%;
      animation: spin 0.8s linear infinite;
    }
    @keyframes spin {
      to {
        transform: rotate(1turn);
      }
    }
    @media (max-width: 640px) {
      .public-detail {
        padding-inline: var(--bt-space-4);
      }
      .facts {
        grid-template-columns: 1fr;
      }
      .detail-head {
        padding: var(--bt-space-6) var(--bt-space-4);
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublicEventPageComponent implements OnInit {
  readonly store = inject(SportsPublicViewStore);
  private readonly route = inject(ActivatedRoute, { optional: true });
  readonly detail = computed(() => this.store.data() as PublicEventDetail | null);
  ngOnInit(): void {
    const id = this.route?.snapshot.paramMap.get('eventId');
    if (id) void this.store.load('event', id);
  }
  title(event: PublicEventDetail): string {
    const labels = event.slots.map((slot) => slot.displayLabel.trim()).filter(Boolean);
    return labels.length === 2 ? `${labels[0]} × ${labels[1]}` : event.title;
  }
  venueLabel(event: PublicEventDetail): string {
    if (event.venueState === 'LOCAL_A_DEFINIR') return 'Local a definir';
    return event.venueSpaceName
      ? `${event.venueName || 'Local definido'} · ${event.venueSpaceName}`
      : event.venueName || 'Local definido';
  }
  statusLabel(status: string | null | undefined): string {
    return status === 'ADIADO'
      ? 'Adiado'
      : status === 'SUSPENSO'
        ? 'Suspenso'
        : status === 'CANCELADO'
          ? 'Cancelado'
          : 'Publicado';
  }
  formatDateTime(value: string, timeZone: string): string {
    return new Intl.DateTimeFormat('pt-BR', {
      dateStyle: 'medium',
      timeStyle: 'short',
      timeZone,
    }).format(new Date(value));
  }
}
