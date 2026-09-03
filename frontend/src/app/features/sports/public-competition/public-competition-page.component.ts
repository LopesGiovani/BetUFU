import { ChangeDetectionStrategy, Component, OnInit, computed, inject } from '@angular/core';
import { RouterLink, ActivatedRoute } from '@angular/router';
import { PublicCompetitionDetail } from '../../../api/generated/model/public-competition-detail';
import { SportsPublicViewStore } from '../public-view/sports-public-view.store';

@Component({
  selector: 'app-public-competition-page',
  standalone: true,
  imports: [RouterLink],
  template: `
    <main class="public-detail" [attr.aria-busy]="store.isLoading()">
      <a class="back-link" routerLink="/">← Voltar para a agenda</a>
      @if (store.isLoading()) {
        <section class="state" role="status">
          <span class="spinner" aria-hidden="true"></span>
          <h1>Carregando competição</h1>
          <p>Buscando a publicação esportiva.</p>
        </section>
      } @else if (store.isNotFound()) {
        <section class="state">
          <h1>Competição não encontrada</h1>
          <p>Esta competição não possui uma publicação pública disponível.</p>
          <a class="action" routerLink="/">Ver agenda</a>
        </section>
      } @else if (store.isError()) {
        <section class="state state--error">
          <h1>Competição indisponível</h1>
          <p>{{ store.errorMessage() }}</p>
          <button class="action" type="button" (click)="store.retry()">Tentar novamente</button>
        </section>
      } @else if (detail(); as competition) {
        <header class="detail-head">
          <p class="eyebrow">Competição pública</p>
          <h1>{{ competition.name }}</h1>
          <p class="slug">/{{ competition.slug }}</p>
          @if (competition.sports.length > 0) {
            <div class="sports-list" aria-label="Modalidades da competição">
              @for (sport of competition.sports; track sport.sportId) {
                <span class="context">{{ sport.name }}</span>
              }
            </div>
          }
        </header>
        <section class="summary-grid" aria-label="Resumo da competição">
          <article>
            <strong>{{ competition.editions.length }}</strong
            ><span>{{
              competition.editions.length === 1 ? 'edição publicada' : 'edições publicadas'
            }}</span>
          </article>
          <article>
            <strong>{{ competition.teams.length }}</strong
            ><span>{{
              competition.teams.length === 1 ? 'equipe publicada' : 'equipes publicadas'
            }}</span>
          </article>
          <article>
            <strong>{{ competition.recentEvents.length }}</strong
            ><span>{{
              competition.recentEvents.length === 1 ? 'evento recente' : 'eventos recentes'
            }}</span>
          </article>
        </section>
        <section class="section" aria-labelledby="editions-title">
          <div class="section-head">
            <h2 id="editions-title">Edições</h2>
            <span>{{ competition.editions.length }} publicadas</span>
          </div>
          @if (competition.editions.length === 0) {
            <p class="empty">Nenhuma edição publicada nesta competição.</p>
          } @else {
            <div class="cards">
              @for (edition of competition.editions; track edition.editionId) {
                <article class="card">
                  <h3>{{ edition.name }}</h3>
                  <p>
                    {{
                      edition.startDate ? formatDate(edition.startDate) : 'Período não informado'
                    }}
                  </p>
                </article>
              }
            </div>
          }
        </section>
        <section class="section" aria-labelledby="teams-title">
          <div class="section-head">
            <h2 id="teams-title">Organizações e equipes</h2>
            <span
              >{{ competition.teams.length }}
              {{ competition.teams.length === 1 ? 'equipe' : 'equipes' }}</span
            >
          </div>
          @if (competition.teams.length === 0) {
            <p class="empty">Nenhuma equipe publicada nesta edição.</p>
          } @else {
            <div class="cards">
              @for (team of competition.teams; track team.teamId) {
                <a class="card card--link" [routerLink]="['/equipes', team.teamId]"
                  ><h3>{{ team.name }}</h3>
                  <p>{{ team.organizationName }} · {{ team.categoryName }}</p></a
                >
              }
            </div>
          }
        </section>
        <section class="section" aria-labelledby="events-title">
          <div class="section-head">
            <h2 id="events-title">Agenda publicada</h2>
            <span
              >{{ competition.recentEvents.length }}
              {{ competition.recentEvents.length === 1 ? 'evento' : 'eventos' }}</span
            >
          </div>
          @if (competition.recentEvents.length === 0) {
            <p class="empty">Nenhum evento publicado nesta competição.</p>
          } @else {
            <div class="event-list">
              @for (event of competition.recentEvents; track event.eventId) {
                <a class="event-card" [routerLink]="['/eventos', event.eventId]"
                  ><time>{{ formatDate(event.scheduledAt) }}</time
                  ><strong>{{ event.title }}</strong
                  ><span
                    >{{ event.editionName }} ·
                    {{
                      event.venueState === 'LOCAL_A_DEFINIR'
                        ? 'Local a definir'
                        : event.venueName || 'Local definido'
                    }}</span
                  ></a
                >
              }
            </div>
          }
        </section>
      } @else {
        <section class="state">
          <h1>Competição sem conteúdo</h1>
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
    .slug,
    .sports-list {
      display: flex;
      flex-wrap: wrap;
      gap: var(--bt-space-2);
      margin-top: var(--bt-space-4);
    }
    .context {
      margin: 0;
      color: var(--bt-signal-soft);
      font-size: var(--bt-type-label);
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }
    h1,
    h2,
    h3,
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
    h3 {
      margin: 0;
      font-size: 1.35rem;
    }
    .context {
      margin-top: var(--bt-space-4);
      color: var(--bt-text);
    }
    .summary-grid,
    .cards,
    .event-list {
      display: grid;
      gap: var(--bt-space-2);
    }
    .summary-grid {
      margin-top: var(--bt-space-4);
      grid-template-columns: repeat(3, 1fr);
    }
    .summary-grid article,
    .card,
    .event-card {
      padding: var(--bt-space-4);
      border: 1px solid var(--bt-border);
      border-radius: var(--bt-radius-card);
      background: var(--bt-surface);
    }
    .summary-grid strong {
      display: block;
      color: var(--bt-signal);
      font:
        600 var(--bt-type-heading)/0.8 'Barlow Condensed',
        sans-serif;
    }
    .summary-grid span,
    .card p,
    .event-card span {
      color: var(--bt-text-muted);
      font-size: var(--bt-type-label);
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
    .cards {
      grid-template-columns: repeat(auto-fit, minmax(190px, 1fr));
    }
    .card--link,
    .event-card {
      color: inherit;
      text-decoration: none;
    }
    .card--link:hover,
    .event-card:hover {
      border-color: var(--bt-signal);
    }
    .card p {
      margin: 0.5rem 0 0;
    }
    .event-card {
      display: grid;
      gap: 0.3rem;
    }
    .event-card time {
      color: var(--bt-signal-soft);
      font-size: var(--bt-type-label);
    }
    .empty {
      margin: 0;
      padding: var(--bt-space-4);
      border: 1px dashed var(--bt-border-strong);
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
      .summary-grid {
        grid-template-columns: 1fr;
      }
      .detail-head {
        padding: var(--bt-space-6) var(--bt-space-4);
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublicCompetitionPageComponent implements OnInit {
  readonly store = inject(SportsPublicViewStore);
  private readonly route = inject(ActivatedRoute, { optional: true });
  readonly detail = computed(() => this.store.data() as PublicCompetitionDetail | null);
  ngOnInit(): void {
    const id = this.route?.snapshot.paramMap.get('competitionId');
    if (id) void this.store.load('competition', id);
  }
  formatDate(value: string): string {
    return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium' }).format(new Date(value));
  }
}
