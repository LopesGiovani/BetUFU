import { ChangeDetectionStrategy, Component, OnInit, computed, inject } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { ResponsiveDataViewComponent } from '../../../../shared/responsive-data-view/responsive-data-view.component';
import { SportsStandingsStore } from './sports-standings.store';

@Component({
  selector: 'app-sports-standings-page',
  standalone: true,
  imports: [RouterLink, ReactiveFormsModule, ResponsiveDataViewComponent],
  template: `
    <main class="operations-standings" [attr.aria-busy]="store.isLoading() || store.isMutating()">
      <a class="back-link" routerLink="/operacao/esportes">← Voltar para operação esportiva</a>
      @if (store.isLoading()) {
        <section class="state" role="status">
          <h1>Carregando chaveamento e classificação</h1>
          <p>Buscando o preview do escopo autorizado.</p>
        </section>
      } @else if (store.isDenied()) {
        <section class="state state--error">
          <h1>Acesso não autorizado</h1>
          <p>{{ store.errorMessage() }}</p>
        </section>
      } @else if (store.isNotFound()) {
        <section class="state">
          <h1>Classificação não encontrada</h1>
          <p>{{ store.errorMessage() }}</p>
        </section>
      } @else if (store.isError()) {
        <section class="state state--error">
          <h1>Não foi possível carregar chaveamento e classificação</h1>
          <p>{{ store.errorMessage() }}</p>
          <button class="action" type="button" (click)="store.retry()">
            Tentar carregar chaveamento e classificação
          </button>
        </section>
      } @else if (standings(); as current) {
        <header class="detail-head">
          <p class="eyebrow">O7 · Operação esportiva</p>
          <h1>{{ current.name }}</h1>
          <p>
            {{ current.competitionName || 'Escopo autorizado'
            }}{{ current.editionName ? ' · ' + current.editionName : ''
            }}{{ current.phaseName ? ' · ' + current.phaseName : '' }}
          </p>
          <p class="disclaimer">
            A classificação é transcrita do boletim oficial. O sistema não calcula pontuação ou
            desempate.
          </p>
        </header>

        @if (store.conflictMessage(); as conflict) {
          <p class="feedback feedback--error" role="alert">{{ conflict }}</p>
        }
        @if (store.feedbackMessage(); as feedback) {
          <p class="feedback" role="status">{{ feedback }}</p>
        }

        @if (current.status === 'RASCUNHO') {
          <form
            class="source-form"
            [formGroup]="publishForm"
            (ngSubmit)="publish()"
            aria-labelledby="publish-title">
            <h2 id="publish-title">Publicar classificação</h2>
            <label for="source-reference">Fonte oficial</label>
            <input
              id="source-reference"
              type="url"
              formControlName="sourceReference"
              placeholder="https://..."
              autocomplete="url" />
            <small>Informe uma fonte oficial válida antes de publicar.</small>
            <button
              class="action"
              type="submit"
              [disabled]="publishForm.invalid || store.isMutating()">
              {{ store.isMutating() ? 'Publicando…' : 'Publicar classificação' }}
            </button>
          </form>
        } @else {
          <form
            class="source-form"
            [formGroup]="correctionForm"
            (ngSubmit)="correct()"
            aria-labelledby="correction-title">
            <h2 id="correction-title">Corrigir versão publicada</h2>
            <label for="correction-reason">Motivo da correção</label>
            <textarea
              id="correction-reason"
              formControlName="reason"
              rows="3"
              placeholder="Descreva o motivo da retificação..."></textarea>
            <label for="correction-source">Fonte substituta</label>
            <input
              id="correction-source"
              type="url"
              formControlName="sourceReference"
              placeholder="https://..."
              autocomplete="url" />
            <button
              class="action"
              type="submit"
              [disabled]="correctionForm.invalid || store.isMutating()">
              {{ store.isMutating() ? 'Publicando nova versão…' : 'Publicar nova versão' }}
            </button>
          </form>
        }

        <app-responsive-data-view [snapshot]="current" [preview]="current.status !== 'PUBLICADO'" />
      } @else {
        <section class="state">
          <h1>Nenhuma classificação transcrita</h1>
          <p>Transcreva a classificação do boletim oficial e informe a fonte antes de publicar.</p>
        </section>
      }
    </main>
  `,
  styles: `
    :host {
      display: block;
      min-width: 0;
    }
    .operations-standings {
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
    .eyebrow {
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
    .disclaimer {
      margin-top: var(--bt-space-3);
      color: var(--bt-signal-soft);
      font-size: var(--bt-type-label);
    }
    .source-form {
      margin-bottom: var(--bt-space-4);
      padding: var(--bt-space-5);
      border: 1px solid var(--bt-border);
      background: var(--bt-surface-raised);
      display: grid;
      gap: 0.55rem;
    }
    h2 {
      margin: 0;
      color: var(--bt-display);
      font:
        600 var(--bt-type-heading)/0.9 'Barlow Condensed',
        sans-serif;
      text-transform: uppercase;
    }
    .source-form label {
      font-weight: 700;
    }
    .source-form input,
    .source-form textarea {
      width: 100%;
      min-height: 44px;
      padding: 0.6rem;
      border: 1px solid var(--bt-border);
      border-radius: var(--bt-radius-field);
      background: var(--bt-surface);
      color: var(--bt-text);
      font: inherit;
    }
    .source-form small {
      color: var(--bt-text-muted);
    }
    .action {
      width: max-content;
      min-height: 44px;
      padding: 0 var(--bt-space-4);
      border: 0;
      border-radius: var(--bt-radius-field);
      background: var(--bt-signal);
      color: var(--bt-ink);
      font-weight: 700;
      cursor: pointer;
    }
    .action:disabled {
      opacity: 0.5;
      cursor: not-allowed;
    }
    .feedback {
      padding: 0.7rem;
      border-left: 3px solid var(--bt-signal);
      background: var(--bt-surface-raised);
    }
    .feedback--error {
      border-color: var(--bt-danger);
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
    @media (max-width: 680px) {
      .operations-standings {
        padding-inline: var(--bt-space-4);
      }
      .detail-head {
        padding: var(--bt-space-4);
      }
      .action {
        width: 100%;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SportsStandingsPageComponent implements OnInit {
  readonly store = inject(SportsStandingsStore);
  private readonly route = inject(ActivatedRoute, { optional: true });
  readonly standings = computed(() => this.store.data());

  readonly publishForm = new FormGroup({
    sourceReference: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.maxLength(2048),
        Validators.pattern(/^https?:\/\//u),
      ],
    }),
  });

  readonly correctionForm = new FormGroup({
    reason: new FormControl('', {
      nonNullable: true,
      validators: [Validators.required, Validators.maxLength(1024)],
    }),
    sourceReference: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.maxLength(2048),
        Validators.pattern(/^https?:\/\//u),
      ],
    }),
  });

  async ngOnInit(): Promise<void> {
    const id = this.route?.snapshot.paramMap.get('standingsId');
    if (id) await this.store.load(id);
  }

  async publish(): Promise<void> {
    if (this.publishForm.valid) {
      await this.store.publish(this.publishForm.controls.sourceReference.value);
    }
  }

  async correct(): Promise<void> {
    if (this.correctionForm.valid && this.standings()) {
      const current = this.standings()!;
      await this.store.correct(
        this.correctionForm.controls.reason.value,
        this.correctionForm.controls.sourceReference.value,
        current.columns,
        current.rows,
      );
    }
  }
}
