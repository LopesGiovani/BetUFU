import { ChangeDetectionStrategy, Component, OnInit, computed, inject } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ResponsiveDataViewComponent } from '../../../shared/responsive-data-view/responsive-data-view.component';
import { PublicStandingsStore } from './public-standings.store';

@Component({
  selector: 'app-public-standings-page',
  standalone: true,
  imports: [RouterLink, ResponsiveDataViewComponent],
  template: `
    <main class="public-standings" [attr.aria-busy]="store.isLoading()">
      <a class="back-link" routerLink="/">← Voltar para a Arena</a>
      @if (store.isLoading()) {
        <section class="state" role="status">
          <h1>Carregando classificação</h1>
          <p>Buscando a publicação oficial.</p>
        </section>
      } @else if (store.isNotFound()) {
        <section class="state">
          <h1>Classificação não encontrada</h1>
          <p>{{ store.errorMessage() }}</p>
          <a class="action" routerLink="/">Ver agenda</a>
        </section>
      } @else if (store.isError()) {
        <section class="state state--error">
          <h1>Classificação indisponível</h1>
          <p>{{ store.errorMessage() }}</p>
          <button class="action" type="button" (click)="store.retry()">Tentar novamente</button>
        </section>
      } @else if (standings(); as current) {
        <header class="detail-head">
          <p class="eyebrow">P8 · Arena pública</p>
          <h1>{{ current.name }}</h1>
          <p>
            {{ current.competitionName || 'Competição publicada'
            }}{{ current.editionName ? ' · ' + current.editionName : ''
            }}{{ current.phaseName ? ' · ' + current.phaseName : '' }}
          </p>
          <p class="source">
            Classificação transcrita da fonte oficial:
            {{ current.sourceReference || 'Fonte publicada no registro' }}
          </p>
        </header>
        <app-responsive-data-view [snapshot]="current" />
      } @else {
        <section class="state">
          <h1>Classificação sem conteúdo</h1>
          <p>A publicação ainda não possui dados para exibir.</p>
        </section>
      }
    </main>
  `,
  styles: `
    :host {
      display: block;
      min-width: 0;
    }
    .public-standings {
      width: var(--bt-container);
      margin-inline: auto;
      padding: var(--bt-space-8) 0 var(--bt-space-16);
      color: var(--bt-text);
    }
    .back-link {
      color: var(--bt-signal);
      font-weight: 700;
      text-decoration: none;
    }
    .detail-head {
      margin: var(--bt-space-8) 0 var(--bt-space-4);
      padding: var(--bt-space-6);
      border-left: 4px solid var(--bt-signal);
      background: var(--bt-surface-raised);
    }
    .eyebrow,
    .source {
      margin: 0;
      color: var(--bt-signal-soft);
      font-size: var(--bt-type-label);
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }
    .detail-head h1 {
      margin: 0.3rem 0;
      color: var(--bt-display);
      font:
        600 var(--bt-type-display)/0.9 'Barlow Condensed',
        sans-serif;
      text-transform: uppercase;
    }
    .detail-head p {
      overflow-wrap: anywhere;
    }
    .state {
      display: grid;
      min-height: 320px;
      margin-top: var(--bt-space-8);
      padding: var(--bt-space-8);
      align-content: center;
      gap: var(--bt-space-2);
      border: 1px solid var(--bt-border);
      background: var(--bt-surface-raised);
    }
    .state h1 {
      margin: 0;
      color: var(--bt-display);
      font:
        600 var(--bt-type-heading)/0.9 'Barlow Condensed',
        sans-serif;
    }
    .state p {
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
    @media (max-width: 680px) {
      .public-standings {
        padding-inline: var(--bt-space-4);
      }
      .detail-head {
        padding: var(--bt-space-4);
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublicStandingsPageComponent implements OnInit {
  readonly store = inject(PublicStandingsStore);
  private readonly route = inject(ActivatedRoute, { optional: true });
  readonly standings = computed(() => this.store.data());

  ngOnInit(): void {
    const id = this.route?.snapshot.paramMap.get('standingsId');
    if (id) void this.store.load(id);
  }
}
