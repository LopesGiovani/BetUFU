import { ChangeDetectionStrategy, Component, input, output, signal } from '@angular/core';
import { PublicAgendaEventViewModel } from '../../features/sports/public-agenda/public-agenda-api.adapter';

@Component({
  selector: 'app-broadcast-ticker',
  standalone: true,
  template: `
    @if (events().length > 0) {
      <div
        class="ticker"
        [class.is-paused]="paused() || interactionPaused()"
        (pointerenter)="interactionPaused.set(true); interactionPausedChange.emit(true)"
        (pointerleave)="interactionPaused.set(false); interactionPausedChange.emit(false)"
        (focusin)="interactionPaused.set(true); interactionPausedChange.emit(true)"
        (focusout)="onFocusOut($event)"
        aria-label="Resumo dos próximos eventos publicados">
        <div class="ticker__track">
          <div class="ticker__set">
            @for (event of events(); track event.id; let index = $index) {
              <a [href]="event.detailHref">
                <strong
                  >{{ index === 0 ? 'Na sequência' : 'Depois' }} · {{ event.timeLabel }}</strong
                >
                <span>{{ event.eventName }} · {{ event.competitionName }}</span>
              </a>
            }
          </div>
          <div class="ticker__set" aria-hidden="true">
            @for (event of events(); track event.id) {
              <span>
                <strong>{{ event.timeLabel }}</strong>
                <span>{{ event.eventName }} · {{ event.competitionName }}</span>
              </span>
            }
          </div>
        </div>
      </div>
    }
  `,
  styles: `
    :host {
      display: block;
      min-width: 0;
    }
    .ticker {
      overflow: hidden;
      border-block: 1px solid var(--bt-border);
      background: var(--bt-canvas);
      color: var(--bt-text);
    }
    .ticker__track {
      display: flex;
      width: max-content;
      animation: ticker-move 24s linear infinite;
      transform: translate3d(0, 0, 0);
    }
    .ticker.is-paused .ticker__track {
      animation-play-state: paused;
    }
    .ticker__set {
      display: flex;
      align-items: stretch;
    }
    a,
    .ticker__set > span {
      display: inline-flex;
      min-height: 44px;
      padding: var(--bt-space-2) var(--bt-space-8);
      align-items: center;
      gap: var(--bt-space-2);
      color: var(--bt-text);
      font-family: 'Barlow Condensed', sans-serif;
      font-size: var(--bt-type-body);
      line-height: 1;
      letter-spacing: 0.06em;
      text-decoration: none;
      text-transform: uppercase;
      white-space: nowrap;
    }
    a:hover,
    a:focus-visible {
      background: var(--bt-surface);
    }
    strong {
      color: var(--bt-signal);
      font-weight: 600;
    }
    @keyframes ticker-move {
      to {
        transform: translate3d(-50%, 0, 0);
      }
    }
    @media (prefers-reduced-motion: reduce) {
      .ticker__track {
        width: 100%;
        animation: none;
      }
      .ticker__set:first-child {
        width: 100%;
        overflow-x: auto;
      }
      .ticker__set[aria-hidden='true'] {
        display: none;
      }
    }
    @media (max-width: 760px) {
      a,
      .ticker__set > span {
        min-height: var(--bt-min-touch-target);
        padding: var(--bt-space-2) var(--bt-space-6);
        font-size: var(--bt-type-body);
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class BroadcastTickerComponent {
  readonly events = input<readonly PublicAgendaEventViewModel[]>([]);
  readonly paused = input(false);
  readonly interactionPausedChange = output<boolean>();
  readonly interactionPaused = signal(false);

  onFocusOut(event: FocusEvent): void {
    const root = event.currentTarget as HTMLElement;
    const next = event.relatedTarget;
    const stillInside = next instanceof Node && root.contains(next);
    if (!stillInside) {
      this.interactionPaused.set(false);
      this.interactionPausedChange.emit(false);
    }
  }
}
