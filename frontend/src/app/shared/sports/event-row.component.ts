import { ChangeDetectionStrategy, Component, input } from '@angular/core';
import { PublicAgendaEventViewModel } from '../../features/sports/public-agenda/public-agenda-api.adapter';

@Component({
  selector: 'app-event-row',
  standalone: true,
  template: `
    <article
      class="event-row"
      [class.event-row--featured]="featured()"
      [id]="'agenda-event-' + event().id"
    >
      <time [attr.datetime]="event().scheduledAt">
        {{ event().timeLabel }}
        <span class="sr-only"> {{ event().zoneLabel }}</span>
      </time>
      <div class="event-row__main">
        <p>{{ featured() ? 'Evento em destaque · ' : '' }}{{ event().formatLabel }}</p>
        <h3>{{ event().eventName }}</h3>
        <div class="event-row__meta">
          <span>{{ event().competitionName }}</span>
          <span>{{ event().editionName }}</span>
        </div>
      </div>
      <div class="event-row__place">
        <strong>{{ event().venueLabel }}</strong>
        <span class="event-row__publication"><i aria-hidden="true"></i>Publicada</span>
      </div>
      <a [href]="event().detailHref" [attr.aria-label]="'Abrir evento ' + event().eventName">
        <svg viewBox="0 0 24 24" aria-hidden="true"><path d="M5 12h13M13 6l6 6-6 6" /></svg>
      </a>
    </article>
  `,
  styles: `
    :host { display: block; min-width: 0; }
    .event-row {
      display: grid;
      min-width: 0;
      min-height: 148px;
      grid-template-columns: 160px minmax(0, 1fr) minmax(180px, 230px) 56px;
      align-items: center;
      overflow: hidden;
      border: 1px solid var(--bt-border);
      border-radius: var(--bt-radius-feature);
      background: color-mix(in srgb, var(--bt-ink) 86%, transparent);
      color: var(--bt-text);
    }
    time {
      align-self: stretch;
      display: grid;
      padding: 0 var(--bt-space-4);
      place-content: center;
      border-right: 1px solid var(--bt-border-strong);
      background: color-mix(in srgb, var(--bt-wine) 58%, transparent);
      color: var(--bt-signal);
      font: 600 var(--bt-type-heading)/.8 'Barlow Condensed', sans-serif;
      font-variant-numeric: tabular-nums;
      text-align: center;
    }
    .event-row__main { min-width: 0; padding: var(--bt-space-6) var(--bt-space-8); }
    p { margin: 0; color: var(--bt-signal-soft); font-size: var(--bt-type-label); font-weight: 600; letter-spacing: .08em; text-transform: uppercase; }
    h3 { margin: var(--bt-space-2) 0 0; color: var(--bt-display); overflow-wrap: anywhere; word-break: normal; font-size: var(--bt-type-heading); line-height: .86; text-transform: uppercase; }
    .event-row__meta { display: flex; min-width: 0; flex-wrap: wrap; gap: var(--bt-space-2); margin-top: var(--bt-space-2); color: var(--bt-text-muted); font-size: var(--bt-type-label); }
    .event-row__meta span + span { padding-left: var(--bt-space-2); border-left: 1px solid var(--bt-border); }
    .event-row__place { display: grid; min-width: 0; gap: var(--bt-space-1); padding-right: var(--bt-space-4); color: var(--bt-text-muted); font-size: var(--bt-type-label); overflow-wrap: anywhere; }
    .event-row__place strong { color: var(--bt-text); overflow-wrap: anywhere; }
    .event-row__publication { display: inline-flex; align-items: center; gap: var(--bt-space-1); }
    .event-row__publication i { width: 7px; height: 7px; border-radius: 50%; background: var(--bt-success); }
    a {
      display: grid;
      width: var(--bt-min-touch-target);
      height: var(--bt-min-touch-target);
      place-items: center;
      border: 1px solid var(--bt-text);
      border-radius: 50%;
      color: var(--bt-text);
      text-decoration: none;
      transition: background 160ms ease, color 160ms ease, transform 160ms cubic-bezier(.16, 1, .3, 1);
    }
    a:hover { background: var(--bt-signal); color: var(--bt-ink); transform: translateX(2px); }
    svg { width: 22px; height: 22px; fill: none; stroke: currentColor; stroke-linecap: round; stroke-linejoin: round; stroke-width: 1.8; }
    .event-row--featured { background: color-mix(in srgb, var(--bt-ink) 86%, transparent); }
    @media (max-width: 720px) {
      .event-row { min-height: 132px; grid-template-columns: 92px minmax(0, 1fr) 56px; grid-template-areas: 'time main action' 'time place action'; border-radius: var(--bt-radius-card); }
      time { grid-area: time; }
      time { padding: 0 var(--bt-space-1); }
      .event-row__main { grid-area: main; padding: var(--bt-space-4) var(--bt-space-2) var(--bt-space-2) var(--bt-space-4); }
      .event-row__place { grid-area: place; display: flex; min-height: 36px; padding: var(--bt-space-1) var(--bt-space-2) var(--bt-space-2) var(--bt-space-4); align-items: center; justify-content: space-between; gap: var(--bt-space-2); border-top: 1px solid var(--bt-border); }
      .event-row__place strong { min-width: 0; }
      .event-row__publication { flex: 0 0 auto; }
      a { grid-area: action; }
      a { width: var(--bt-min-touch-target); height: var(--bt-min-touch-target); justify-self: end; }
    }
    @media (prefers-reduced-motion: reduce) { a { transition: none; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EventRowComponent {
  readonly event = input.required<PublicAgendaEventViewModel>();
  readonly featured = input(false);
}
