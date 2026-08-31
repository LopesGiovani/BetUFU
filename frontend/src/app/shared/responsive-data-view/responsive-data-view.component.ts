import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { SportsStandingsSnapshot } from '../../api/generated/model/sports-standings-snapshot';
import { SportsStandingsRow } from '../../api/generated/model/sports-standings-row';

@Component({
  selector: 'app-responsive-data-view',
  standalone: true,
  template: `
    @if (snapshot(); as current) {
      <section class="responsive-data-view" [class.responsive-data-view--preview]="preview()" aria-label="Classificação oficial">
        <header class="data-view__head">
          <div>
            <p class="eyebrow">{{ preview() ? 'Pré-visualização operacional' : 'Classificação oficial' }}</p>
            <h2>{{ current.name }}</h2>
            <p class="context">
              {{ current.competitionName }}{{ current.editionName ? ' · ' + current.editionName : '' }}{{ current.phaseName ? ' · ' + current.phaseName : '' }}
            </p>
          </div>
          <div class="badges">
            @if (current.versionNo) {
              <span class="version-badge">Versão {{ current.versionNo }}</span>
            }
            <span class="status" [class.status--draft]="current.status !== 'PUBLICADO'">{{ statusLabel(current.status) }}</span>
          </div>
        </header>

        @if (preview()) {
          <p class="preview-note" role="status">Pré-visualização não publicada. Nenhuma alteração é aplicada por esta tela.</p>
        }

        <div class="meta-panel">
          <p class="disclaimer">
            <strong>Aviso:</strong> Classificação transcrita do boletim oficial. O sistema não calcula pontos ou critérios de desempate.
          </p>
          @if (current.sourceReference) {
            <p class="source-info">
              Fonte oficial:
              @if (isUrl(current.sourceReference)) {
                <a class="source-link" [href]="current.sourceReference" target="_blank" rel="noopener noreferrer">{{ current.sourceReference }}</a>
              } @else {
                <span>{{ current.sourceReference }}</span>
              }
            </p>
          }
          @if (current.publishedAt) {
            <p class="published-at">Atualizada em: <time [attr.datetime]="current.publishedAt">{{ formatDate(current.publishedAt) }}</time></p>
          }
        </div>

        @if (hasData()) {
          <!-- Desktop Table View (>=600px) -->
          <div class="data-view__table-wrapper" tabindex="0" aria-label="Tabela de classificação com rolagem horizontal se necessário">
            <table class="data-view__table">
              <caption class="sr-only">{{ captionText() }}</caption>
              <thead>
                <tr>
                  <th scope="col" class="th-position">Posição</th>
                  <th scope="col" class="th-participant">Participante</th>
                  @for (col of columns(); track col) {
                    <th scope="col" class="th-col">{{ col }}</th>
                  }
                </tr>
              </thead>
              <tbody>
                @for (row of rows(); track row.position) {
                  <tr class="data-row">
                    <td class="td-position"><strong>{{ row.position }}º</strong></td>
                    <td class="td-participant">
                      <span class="participant-name">{{ row.participantName }}</span>
                    </td>
                    @for (val of row.values; track $index) {
                      <td class="td-value">{{ val }}</td>
                    }
                  </tr>
                }
              </tbody>
            </table>
          </div>

          <!-- Mobile Cards View (<600px) -->
          <div class="data-view__cards" role="list" aria-label="Lista de classificação em formato de cartões">
            @for (row of rows(); track row.position) {
              <article class="data-card" role="listitem">
                <header class="data-card__head">
                  <span class="data-card__position" aria-label="Posição">{{ row.position }}º</span>
                  <strong class="data-card__participant" aria-label="Participante">{{ row.participantName }}</strong>
                </header>
                <dl class="data-card__metrics">
                  @for (col of columns(); track col; let idx = $index) {
                    <div class="data-card__metric">
                      <dt>{{ col }}</dt>
                      <dd>{{ row.values[idx] !== undefined ? row.values[idx] : '-' }}</dd>
                    </div>
                  }
                </dl>
              </article>
            }
          </div>
        } @else {
          <div class="data-view__empty-rows">
            <p>Nenhuma linha de classificação cadastrada.</p>
          </div>
        }
      </section>
    } @else {
      <section class="responsive-data-view responsive-data-view--empty" aria-live="polite">
        <h2>Classificação sem dados</h2>
        <p>A publicação ainda não possui informações para exibir.</p>
      </section>
    }
  `,
  styles: `
    :host { display: block; min-width: 0; }
    .responsive-data-view {
      color: var(--bt-text);
      padding: var(--bt-space-6);
      border: 1px solid var(--bt-border);
      border-radius: var(--bt-radius-card);
      background: var(--bt-surface);
    }
    .responsive-data-view--preview {
      border-color: var(--bt-signal-soft);
      background: var(--bt-surface-raised);
    }
    .responsive-data-view--empty { min-height: 240px; }
    .data-view__head {
      display: flex;
      align-items: flex-start;
      justify-content: space-between;
      gap: var(--bt-space-4);
    }
    .eyebrow {
      margin: 0;
      color: var(--bt-signal-soft);
      font-size: var(--bt-type-label);
      letter-spacing: .08em;
      text-transform: uppercase;
    }
    h2 {
      margin: .3rem 0 0;
      color: var(--bt-display);
      font: 600 var(--bt-type-heading)/.9 'Barlow Condensed', sans-serif;
      text-transform: uppercase;
      overflow-wrap: anywhere;
    }
    .context {
      margin: .3rem 0 0;
      color: var(--bt-text-muted);
      overflow-wrap: anywhere;
    }
    .badges {
      display: flex;
      align-items: center;
      gap: .5rem;
      flex-wrap: wrap;
    }
    .version-badge {
      padding: .35rem .6rem;
      border-radius: var(--bt-radius-control);
      background: var(--bt-surface-raised);
      border: 1px solid var(--bt-border-strong);
      color: var(--bt-signal-soft);
      font-size: var(--bt-type-label);
      font-weight: 600;
    }
    .status {
      padding: .35rem .6rem;
      border-radius: var(--bt-radius-control);
      background: var(--bt-signal);
      color: var(--bt-ink);
      font-size: var(--bt-type-label);
      font-weight: 700;
    }
    .status--draft {
      background: var(--bt-warning, #ffc4c4);
      color: var(--bt-ink);
    }
    .preview-note {
      margin: var(--bt-space-4) 0 0;
      padding: var(--bt-space-3);
      border-left: 3px solid var(--bt-signal);
      color: var(--bt-text-muted);
      background: var(--bt-surface-raised);
    }
    .meta-panel {
      margin: var(--bt-space-4) 0 var(--bt-space-6);
      padding: var(--bt-space-4);
      border: 1px solid var(--bt-border);
      border-radius: var(--bt-radius-field);
      background: var(--bt-surface-raised);
      display: grid;
      gap: .4rem;
    }
    .disclaimer {
      margin: 0;
      color: var(--bt-signal-soft);
      font-size: var(--bt-type-label);
    }
    .source-info, .published-at {
      margin: 0;
      color: var(--bt-text-muted);
      font-size: var(--bt-type-label);
      overflow-wrap: anywhere;
    }
    .source-link {
      color: var(--bt-signal);
      text-decoration: underline;
      word-break: break-all;
    }
    .sr-only {
      position: absolute;
      width: 1px;
      height: 1px;
      padding: 0;
      margin: -1px;
      overflow: hidden;
      clip: rect(0, 0, 0, 0);
      white-space: nowrap;
      border: 0;
    }
    .data-view__table-wrapper {
      display: block;
      overflow-x: auto;
      margin-top: var(--bt-space-4);
      border: 1px solid var(--bt-border);
      border-radius: var(--bt-radius-card);
    }
    .data-view__table {
      width: 100%;
      border-collapse: collapse;
      text-align: left;
      font-size: .95rem;
    }
    .data-view__table thead {
      background: var(--bt-surface-raised);
      border-bottom: 2px solid var(--bt-border-strong);
    }
    .data-view__table th {
      padding: var(--bt-space-3) var(--bt-space-4);
      color: var(--bt-signal-soft);
      font-weight: 700;
      font-size: var(--bt-type-label);
      letter-spacing: .04em;
      text-transform: uppercase;
      white-space: nowrap;
    }
    .data-view__table td {
      padding: var(--bt-space-3) var(--bt-space-4);
      border-bottom: 1px solid var(--bt-border);
      color: var(--bt-text);
      white-space: nowrap;
    }
    .data-row:last-child td {
      border-bottom: none;
    }
    .data-row:hover {
      background: rgba(255, 255, 255, .03);
    }
    .th-position, .td-position {
      width: 60px;
      text-align: center;
    }
    .td-position strong {
      color: var(--bt-signal);
      font-size: 1.1rem;
    }
    .th-participant, .td-participant {
      font-weight: 600;
    }
    .th-col, .td-value {
      text-align: center;
      font-variant-numeric: tabular-nums;
    }
    .data-view__cards {
      display: none;
      gap: var(--bt-space-3);
      margin-top: var(--bt-space-4);
    }
    .data-card {
      padding: var(--bt-space-4);
      border: 1px solid var(--bt-border);
      border-radius: var(--bt-radius-card);
      background: var(--bt-surface-raised);
      display: grid;
      gap: var(--bt-space-3);
    }
    .data-card__head {
      display: flex;
      align-items: center;
      gap: var(--bt-space-3);
    }
    .data-card__position {
      display: grid;
      width: 32px;
      height: 32px;
      place-items: center;
      border-radius: 50%;
      background: var(--bt-signal-soft);
      color: var(--bt-ink);
      font-size: .875rem;
      font-weight: 700;
      flex: 0 0 auto;
    }
    .data-card__participant {
      font-size: 1.05rem;
      color: var(--bt-display);
      overflow-wrap: anywhere;
    }
    .data-card__metrics {
      display: grid;
      grid-template-columns: repeat(auto-fit, minmax(65px, 1fr));
      gap: .5rem;
      margin: 0;
      padding: .5rem;
      border-radius: var(--bt-radius-field);
      background: var(--bt-surface);
      border: 1px solid var(--bt-border);
    }
    .data-card__metric {
      display: grid;
      gap: .15rem;
      text-align: center;
    }
    .data-card__metric dt {
      color: var(--bt-signal-soft);
      font-size: var(--bt-type-label);
      font-weight: 600;
      text-transform: uppercase;
    }
    .data-card__metric dd {
      margin: 0;
      color: var(--bt-text);
      font-size: 1.1rem;
      font-weight: 700;
      font-variant-numeric: tabular-nums;
    }
    .data-view__empty-rows {
      margin-top: var(--bt-space-4);
      padding: var(--bt-space-4);
      border: 1px dashed var(--bt-border);
      border-radius: var(--bt-radius-field);
      color: var(--bt-text-muted);
    }
    @media (max-width: 599px) {
      .data-view__table-wrapper { display: none; }
      .data-view__cards { display: grid; }
      .responsive-data-view { padding: var(--bt-space-4); }
      .data-view__head { display: grid; }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ResponsiveDataViewComponent {
  readonly snapshot = input<SportsStandingsSnapshot | null>(null);
  readonly preview = input(false);
  readonly caption = input<string | null>(null);

  readonly rows = computed<readonly SportsStandingsRow[]>(() => this.snapshot()?.rows ?? []);
  readonly columns = computed<readonly string[]>(() => this.snapshot()?.columns ?? []);
  readonly hasData = computed(() => this.rows().length > 0);
  readonly captionText = computed(
    () => this.caption() || (this.snapshot() ? `Classificação oficial de ${this.snapshot()!.name}` : 'Classificação oficial transcrita'),
  );

  statusLabel(status: string): string {
    return status === 'PUBLICADO' ? 'Publicado' : 'Rascunho';
  }

  isUrl(value: string): boolean {
    return /^https?:\/\//u.test(value);
  }

  formatDate(value: string): string {
    try {
      return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'short', timeStyle: 'short' }).format(new Date(value));
    } catch {
      return value;
    }
  }
}
