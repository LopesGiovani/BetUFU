import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  inject,
  signal,
} from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { PublicMarketStore } from './public-market.store';

@Component({
  selector: 'app-public-market-page',
  standalone: true,
  imports: [RouterLink],
  templateUrl: './public-market-page.component.html',
  changeDetection: ChangeDetectionStrategy.OnPush,
  styles: [
    `
      :host { display: block; min-width: 0; background: var(--bt-canvas, #120b12); color: var(--bt-text, #fff7f4); }
      .market-page { width: min(100% - 32px, 1120px); margin: 0 auto; padding: 48px 0 64px; }
      .market-topline, .market-meta, .market-actions, .explanation-head, .history-head { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
      .market-topline { margin-bottom: 32px; flex-wrap: wrap; }
      .back-link { color: var(--bt-text-muted, #d8c4ca); text-decoration: underline; text-underline-offset: 4px; }
      .eyebrow, .label { color: var(--bt-signal-soft, #ff9ca0); font-size: 14px; font-weight: 600; letter-spacing: .08em; text-transform: uppercase; }
      h1, h2, h3, p { margin-top: 0; }
      h1, h2, h3 { font-family: 'Barlow Condensed', sans-serif; text-transform: uppercase; letter-spacing: -.02em; }
      h1 { margin-bottom: 8px; font-size: clamp(36px, 8vw, 76px); line-height: .9; }
      h2 { margin-bottom: 16px; font-size: clamp(28px, 5vw, 48px); line-height: .95; }
      h3 { margin-bottom: 8px; font-size: 28px; line-height: 1; }
      .market-topline p, .market-meta p, .state-panel p, .explanation p { color: var(--bt-text-muted, #d8c4ca); }
      .market-topline p { margin: 0; max-width: 60ch; }
      .market-meta { justify-content: flex-start; flex-wrap: wrap; margin-bottom: 24px; }
      .status, .confidence { display: inline-flex; min-height: 32px; padding: 6px 10px; align-items: center; border: 1px solid var(--bt-border, #5d4250); border-radius: 999px; font-size: 14px; font-weight: 600; }
      .status--suspended, .confidence--low { border-color: var(--bt-warning, #f5c95c); color: var(--bt-warning, #f5c95c); }
      .status--closed, .status--locked, .status--cancelled { border-color: var(--bt-danger, #ff7a82); color: var(--bt-danger, #ff7a82); }
      .market-card, .explanation, .history { padding: 24px; border: 1px solid var(--bt-border, #5d4250); border-radius: 16px; background: color-mix(in srgb, var(--bt-surface, #21131d) 90%, transparent); }
      .market-card { margin-bottom: 24px; }
      .market-card__header { margin-bottom: 24px; }
      .market-card__header p { margin-bottom: 0; }
      .selections { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; }
      .selection { min-width: 0; padding: 20px; border: 1px solid var(--bt-border, #5d4250); border-radius: 12px; background: var(--bt-ink, #09080d); }
      .selection--current { border-color: var(--bt-signal, #ff626b); box-shadow: inset 0 3px 0 var(--bt-signal, #ff626b); }
      .selection__odd { display: block; margin-top: 12px; color: var(--bt-signal, #ff626b); font: 600 clamp(42px, 8vw, 76px)/.8 'Barlow Condensed', sans-serif; font-variant-numeric: tabular-nums; }
      .selection__name { display: block; min-height: 24px; font-weight: 600; overflow-wrap: anywhere; }
      .market-actions { margin-top: 24px; justify-content: flex-start; flex-wrap: wrap; }
      .action { min-height: 44px; padding: 10px 16px; border: 1px solid var(--bt-signal, #ff626b); border-radius: 999px; background: var(--bt-signal, #ff626b); color: var(--bt-ink, #09080d); cursor: pointer; font: inherit; font-weight: 600; }
      .action--quiet { border-color: var(--bt-border-strong, #876574); background: transparent; color: var(--bt-text, #fff7f4); }
      .explanation, .history { margin-top: 24px; }
      .explanation-head, .history-head { align-items: flex-start; }
      .explanation-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(180px, 1fr)); gap: 16px; margin-top: 24px; }
      .explanation-grid div { padding: 16px; border-left: 2px solid var(--bt-signal, #ff626b); background: color-mix(in srgb, var(--bt-ink, #09080d) 60%, transparent); }
      .explanation-grid dt { color: var(--bt-text-muted, #d8c4ca); font-size: 14px; }
      .explanation-grid dd { margin: 4px 0 0; font-weight: 600; font-variant-numeric: tabular-nums; overflow-wrap: anywhere; }
      .history table { width: 100%; border-collapse: collapse; }
      .history caption { margin-bottom: 12px; text-align: left; color: var(--bt-text-muted, #d8c4ca); }
      .history th, .history td { padding: 12px 8px; border-bottom: 1px solid var(--bt-border, #5d4250); text-align: left; }
      .history th { color: var(--bt-signal-soft, #ff9ca0); font-size: 14px; }
      .history td { font-variant-numeric: tabular-nums; }
      .state-panel { min-height: 280px; padding: 48px 24px; border: 1px solid var(--bt-border, #5d4250); border-radius: 16px; background: var(--bt-surface, #21131d); }
      .state-panel--error { border-color: var(--bt-danger, #ff7a82); }
      .state-panel--empty { border-color: var(--bt-warning, #f5c95c); }
      .skeleton { display: grid; gap: 16px; }
      .skeleton span { display: block; height: 24px; border-radius: 8px; background: color-mix(in srgb, var(--bt-text-muted, #d8c4ca) 20%, transparent); }
      .skeleton span:first-child { width: 55%; height: 64px; }
      .skeleton span:last-child { width: 80%; }
      :focus-visible { outline: 3px solid var(--bt-focus, #f5c95c); outline-offset: 3px; }
      @media (max-width: 600px) {
        .market-page { width: min(100% - 24px, 1120px); padding-top: 32px; }
        .market-card, .explanation, .history { padding: 16px; border-radius: 12px; }
        .market-topline, .history-head { align-items: flex-start; flex-direction: column; }
        .selection__odd { font-size: 56px; }
        .history { overflow-x: auto; }
        .history table { min-width: 480px; }
      }
      @media (prefers-reduced-motion: reduce) { *, *::before, *::after { scroll-behavior: auto !important; transition: none !important; animation: none !important; } }
    `,
  ],
})
export class PublicMarketPageComponent implements OnInit {
  readonly store = inject(PublicMarketStore);
  private readonly route = inject(ActivatedRoute);
  readonly explanationOpen = signal(false);

  ngOnInit(): void {
    void this.store.loadMarket(this.route.snapshot.paramMap.get('marketId') ?? '');
  }

  toggleExplanation(): void {
    this.explanationOpen.update((open) => !open);
  }

  async retry(): Promise<void> {
    await this.store.retry();
  }

  statusClass(): string {
    return `status--${this.store.snapshot()?.state.toLowerCase() ?? 'unknown'}`;
  }

  formatOdd(value: number): string {
    return value.toFixed(2);
  }

  formatPercent(value: number): string {
    return `${(value * 100).toFixed(1)}%`;
  }
}
