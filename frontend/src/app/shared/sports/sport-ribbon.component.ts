import { ChangeDetectionStrategy, Component, input } from '@angular/core';

@Component({
  selector: 'app-sport-ribbon',
  standalone: true,
  template: `
    <div class="ribbon">
      <span class="ribbon__mark" aria-hidden="true">B</span>
      <span class="ribbon__copy">
        <strong>Agenda pública</strong>
        <span>
          {{
            eventCount() > 0
              ? eventCount() + (eventCount() === 1 ? ' evento publicado' : ' eventos publicados')
              : filtered()
                ? 'Nenhum evento encontrado'
                : 'Nenhum evento publicado'
          }}
        </span>
        <small>Competições do campus em um só lugar</small>
      </span>
      <span class="ribbon__status">
        <i aria-hidden="true"></i>
        {{
          stale()
            ? 'Dados desatualizados'
            : loading()
              ? 'Agenda em atualização'
              : published()
                ? 'Agenda publicada'
                : 'Aguardando publicação'
        }}
      </span>
    </div>
  `,
  styles: `
    :host {
      position: relative;
      z-index: 6;
      display: block;
      width: var(--bt-container);
      margin: 0 auto calc(-1 * var(--bt-space-6));
    }
    .ribbon {
      display: grid;
      min-height: 84px;
      padding: var(--bt-space-2) var(--bt-space-4);
      grid-template-columns: auto minmax(0, 1fr) auto;
      align-items: center;
      gap: var(--bt-space-4);
      border: 1px solid var(--bt-border-strong);
      border-radius: var(--bt-radius-feature);
      background: color-mix(in srgb, var(--bt-surface) 96%, transparent);
      color: var(--bt-text);
      box-shadow: 0 18px 52px rgba(9, 8, 13, 0.42);
    }
    .ribbon__mark {
      display: grid;
      width: 50px;
      height: 50px;
      place-items: center;
      border-radius: var(--bt-radius-field);
      background: var(--bt-signal-deep);
      color: var(--bt-on-signal-deep);
      font:
        600 min(var(--bt-type-heading), 1.625rem)/1 'Barlow Condensed',
        sans-serif;
    }
    .ribbon__copy {
      display: grid;
      min-width: 0;
      gap: var(--bt-space-1);
      line-height: 1.2;
    }
    .ribbon__copy strong {
      color: var(--bt-text);
      font-size: var(--bt-type-label);
      letter-spacing: 0.12em;
      text-transform: uppercase;
    }
    .ribbon__copy span {
      color: var(--bt-signal-soft);
      font-size: var(--bt-type-body);
      font-weight: 600;
    }
    .ribbon__copy small {
      color: var(--bt-text-muted);
      font-size: var(--bt-type-label);
    }
    .ribbon__status {
      display: flex;
      min-height: var(--bt-min-touch-target);
      padding: 0 var(--bt-space-4);
      align-items: center;
      gap: var(--bt-space-2);
      border: 1px solid var(--bt-border-strong);
      border-radius: var(--bt-radius-control);
      background: color-mix(in srgb, var(--bt-wine) 62%, transparent);
      color: var(--bt-text);
      font-size: var(--bt-type-label);
      font-weight: 600;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }
    .ribbon__status i {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: var(--bt-signal);
      box-shadow: 0 0 0 5px color-mix(in srgb, var(--bt-signal) 13%, transparent);
    }
    @media (max-width: 760px) {
      :host {
        width: min(calc(100% - var(--bt-space-8)), 1240px);
        margin-bottom: calc(-1 * var(--bt-space-6));
      }
      .ribbon {
        padding: var(--bt-space-2);
        grid-template-columns: auto minmax(0, 1fr);
        gap: var(--bt-space-2);
        border-radius: var(--bt-radius-feature);
      }
      .ribbon__mark {
        width: 46px;
        height: 46px;
        border-radius: var(--bt-radius-field);
      }
      .ribbon__status {
        grid-column: 1 / -1;
        justify-content: center;
      }
    }
    @media (max-width: 430px) {
      .ribbon__copy strong {
        letter-spacing: 0.08em;
      }
      .ribbon__copy span {
        font-size: var(--bt-type-body);
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class SportRibbonComponent {
  readonly published = input(false);
  readonly eventCount = input(0);
  readonly filtered = input(false);
  readonly stale = input(false);
  readonly loading = input(false);
}
