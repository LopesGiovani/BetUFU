import {
  ChangeDetectionStrategy,
  Component,
  input,
  output,
} from '@angular/core';

@Component({
  selector: 'app-highlight-motion-control',
  standalone: true,
  template: `
    <button
      type="button"
      class="motion-control"
      [attr.aria-pressed]="paused()"
      (click)="pausedChange.emit(!paused())"
    >
      <svg viewBox="0 0 24 24" aria-hidden="true">
        @if (paused()) {
          <path d="M8 5v14l11-7L8 5Z" />
        } @else {
          <path d="M7 5h4v14H7V5Zm6 0h4v14h-4V5Z" />
        }
      </svg>
      <span>{{ paused() ? 'Retomar destaques' : 'Pausar destaques' }}</span>
    </button>
  `,
  styles: `
    :host { display: inline-flex; flex: 0 0 auto; }
    .motion-control {
      display: inline-flex;
      min-height: var(--bt-min-touch-target);
      padding: 0 var(--bt-space-4);
      align-items: center;
      gap: var(--bt-space-2);
      border: 1px solid var(--bt-border-strong);
      border-radius: 999px;
      background: transparent;
      color: var(--bt-text-muted);
      cursor: pointer;
      font-size: var(--bt-type-label);
      font-weight: 600;
      transition: border-color 160ms ease, background 160ms ease, color 160ms ease;
    }
    .motion-control:hover { border-color: var(--bt-signal); color: var(--bt-text); }
    .motion-control[aria-pressed='true'] { background: var(--bt-signal); color: var(--bt-ink); }
    svg { width: 15px; height: 15px; flex: 0 0 auto; fill: currentColor; }
    @media (prefers-reduced-motion: reduce) { .motion-control { transition: none; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HighlightMotionControlComponent {
  readonly paused = input(false);
  readonly pausedChange = output<boolean>();
}
