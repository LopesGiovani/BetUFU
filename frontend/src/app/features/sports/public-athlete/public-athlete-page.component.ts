import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { PublicAthleteDetail } from '../../../api/generated/model/public-athlete-detail';
import { SportsPublicViewStore } from '../public-view/sports-public-view.store';
import {
  PublicAthleteProfileStore,
  PublicAthleteTextProfile,
} from './public-athlete-profile.store';

@Component({
  selector: 'app-public-athlete-page',
  standalone: true,
  imports: [RouterLink],
  template: `
    <main class="public-detail" [attr.aria-busy]="store.isLoading()">
      <a class="back-link" routerLink="/">Voltar para a agenda</a>
      @if (store.isLoading()) {
        <section class="state" role="status">
          <h1>Carregando atleta</h1>
          <p>Buscando a publicacao esportiva.</p>
        </section>
      } @else if (store.isNotFound()) {
        <section class="state">
          <h1>Atleta nao encontrado</h1>
          <p>Este atleta nao possui uma publicacao publica disponivel.</p>
          <a class="action" routerLink="/">Ver agenda</a>
        </section>
      } @else if (store.isError()) {
        <section class="state state--error">
          <h1>Atleta indisponivel</h1>
          <p>{{ store.errorMessage() }}</p>
          <button class="action" type="button" (click)="store.retry()">Tentar novamente</button>
        </section>
      } @else if (detail(); as athlete) {
        <header class="detail-head">
          <div class="athlete-avatar">
            @if (profileStore.photoUrl(); as photoUrl) {
              <img [src]="photoUrl" [alt]="'Foto de ' + athlete.name" />
            } @else {
              <span aria-hidden="true">{{ athlete.name.charAt(0) }}</span>
            }
          </div>
          <div>
            <p class="eyebrow">Atleta publico</p>
            <h1>{{ athlete.name }}</h1>
            <p class="slug">/{{ athlete.slug }}</p>
          </div>
        </header>
        @if (profileStore.isReady() && profileStore.hasPublishedContent()) {
          @if (profile(); as publicProfile) {
            <section class="athlete-profile-card" aria-labelledby="public-profile-title">
              <p class="eyebrow">Perfil esportivo</p>
              <h2 id="public-profile-title">Sobre {{ athlete.name }}</h2>
              @if (publicProfile.course) {
                <p class="public-profile__course">{{ publicProfile.course }}</p>
              }
              @if (publicProfile.bio) {
                <p class="public-profile__bio">{{ publicProfile.bio }}</p>
              }
              @if (publicProfileLinks().length) {
                <nav class="public-profile__links" aria-label="Redes sociais do atleta">
                  @for (link of publicProfileLinks(); track link.label) {
                    <a [href]="link.href" target="_blank" rel="noopener noreferrer">{{
                      link.label
                    }}</a>
                  }
                </nav>
              }
            </section>
          }
        } @else if (profileStore.isLoading()) {
          <p class="profile-loading" role="status">Carregando perfil textual...</p>
        }
        <section class="summary-grid">
          <article>
            <strong>{{ athlete.stats.totalParticipations }}</strong
            ><span>participacoes publicas</span>
          </article>
          <article>
            <strong>{{ athlete.stats.totalWins }}</strong
            ><span>vitorias</span>
          </article>
          <article>
            <strong>{{ athlete.stats.totalLosses }}</strong
            ><span>derrotas</span>
          </article>
        </section>
        <section class="section">
          <div class="section-head">
            <h2>Equipes e participacoes</h2>
            <span
              >{{ athlete.teams.length }}
              {{ athlete.teams.length === 1 ? 'equipe' : 'equipes' }}</span
            >
          </div>
          @if (athlete.teams.length === 0) {
            <p class="empty">Nenhuma participacao publicada.</p>
          } @else {
            <div class="cards">
              @for (team of athlete.teams; track team.teamId + '-' + team.editionId) {
                <a class="card card--link" [routerLink]="['/equipes', team.teamId]"
                  ><h3>{{ team.teamName }}</h3>
                  <p>
                    {{ team.organizationName }} · {{ team.editionName }} · {{ team.sportName }} ·
                    {{ team.categoryName }}
                  </p></a
                >
              }
            </div>
          }
        </section>
        <section class="section">
          <div class="section-head">
            <h2>Eventos</h2>
            <span
              >{{ athlete.events.length }}
              {{ athlete.events.length === 1 ? 'participacao' : 'participacoes' }}</span
            >
          </div>
          @if (athlete.events.length === 0) {
            <p class="empty">Nenhum resultado ou evento publicado para este atleta.</p>
          } @else {
            <div class="events">
              @for (event of athlete.events; track event.eventId) {
                <a class="event" [routerLink]="['/eventos', event.eventId]"
                  ><strong>{{ event.title }}</strong
                  ><span>{{ formatDate(event.scheduledAt) }} · {{ event.editionName }}</span></a
                >
              }
            </div>
          }
        </section>
      } @else {
        <section class="state">
          <h1>Atleta sem conteudo</h1>
          <p>A publicacao ainda nao possui detalhes para exibir.</p>
        </section>
      }
    </main>
  `,
  styles: `
    :host {
      display: block;
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
      display: flex;
      align-items: center;
      gap: var(--bt-space-4);
      margin-top: var(--bt-space-8);
      padding: var(--bt-space-8);
      border-left: 4px solid var(--bt-signal);
      background: var(--bt-surface-raised);
    }
    .athlete-avatar {
      width: 88px;
      height: 88px;
      flex: 0 0 88px;
      display: grid;
      place-items: center;
      overflow: hidden;
      border: 1px solid var(--bt-border);
      border-radius: 50%;
      background: var(--bt-surface);
      color: var(--bt-signal);
      font: 600 2.5rem/.9 'Barlow Condensed', sans-serif;
    }
    .athlete-avatar img { width: 100%; height: 100%; object-fit: cover; }
    .eyebrow,
    .slug {
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
    .athlete-profile-card {
      display: grid;
      gap: var(--bt-space-2);
      margin-top: var(--bt-space-4);
      padding: var(--bt-space-6);
      border: 1px solid var(--bt-border);
      border-radius: var(--bt-radius-card);
      background: var(--bt-surface);
    }
    .athlete-profile-card h2 {
      margin-top: var(--bt-space-1);
    }
    .public-profile__course {
      margin: 0;
      color: var(--bt-signal-soft);
      font-weight: 700;
    }
    .public-profile__bio {
      margin: var(--bt-space-2) 0 0;
      color: var(--bt-text-muted);
      white-space: pre-wrap;
    }
    .public-profile__links {
      display: flex;
      flex-wrap: wrap;
      gap: var(--bt-space-2);
      margin-top: var(--bt-space-3);
    }
    .public-profile__links a {
      padding: var(--bt-space-2) var(--bt-space-3);
      border: 1px solid var(--bt-border);
      border-radius: var(--bt-radius-full);
      color: var(--bt-text);
      font-weight: 700;
      text-decoration: none;
    }
    .profile-loading {
      margin-top: var(--bt-space-4);
      color: var(--bt-text-muted);
    }
    .summary-grid,
    .cards,
    .events {
      display: grid;
      gap: var(--bt-space-2);
    }
    .summary-grid {
      grid-template-columns: repeat(3, 1fr);
      margin-top: var(--bt-space-4);
    }
    .summary-grid article,
    .card,
    .event,
    .empty {
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
    .event span,
    .empty {
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
    .event {
      color: inherit;
      text-decoration: none;
    }
    .card--link:hover,
    .event:hover {
      border-color: var(--bt-signal);
    }
    .card p {
      margin: 0.5rem 0 0;
    }
    .event {
      display: grid;
      gap: 0.35rem;
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
    @media (max-width: 640px) {
      .public-detail {
        padding-inline: var(--bt-space-4);
      }
      .summary-grid {
        grid-template-columns: 1fr;
      }
      .detail-head {
        align-items: flex-start;
        padding: var(--bt-space-6) var(--bt-space-4);
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublicAthletePageComponent implements OnDestroy, OnInit {
  readonly store = inject(SportsPublicViewStore);
  readonly profileStore = inject(PublicAthleteProfileStore);
  private readonly route = inject(ActivatedRoute, { optional: true });
  readonly detail = computed(() => this.store.data() as PublicAthleteDetail | null);
  readonly profile = computed(() => this.profileStore.profile() as PublicAthleteTextProfile | null);

  ngOnInit(): void {
    const id = this.route?.snapshot.paramMap.get('athleteId');
    if (id) {
      void this.store.load('athlete', id);
      void this.profileStore.load(id);
    }
  }

  ngOnDestroy(): void {
    this.profileStore.releasePhotoResources();
  }

  publicProfileLinks(): readonly { label: string; href: string }[] {
    const profile = this.profile();
    if (!profile) return [];
    return [
      { label: 'Instagram', href: profile.instagramUrl },
      { label: 'X / Twitter', href: profile.xUrl },
      { label: 'LinkedIn', href: profile.linkedinUrl },
      { label: 'Strava', href: profile.stravaUrl },
      { label: 'YouTube', href: profile.youtubeUrl },
    ].filter(
      (item): item is { label: string; href: string } =>
        typeof item.href === 'string' && item.href.trim() !== '',
    );
  }

  formatDate(value: string): string {
    return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium' }).format(new Date(value));
  }
}
