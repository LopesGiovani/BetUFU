import {
  AfterViewInit,
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnDestroy,
  ViewChild,
  computed,
  effect,
  input,
  signal,
} from '@angular/core';
import { PublicAgendaEventViewModel } from '../../features/sports/public-agenda/public-agenda-api.adapter';

const CYCLE_MS = 3_000;
const SWITCH_DURATION_MS = 780;

@Component({
  selector: 'app-auto-event-hero',
  standalone: true,
  template: `
    <section
      #heroRoot
      class="event-hero"
      (pointerenter)="hovered.set(true)"
      (pointerleave)="hovered.set(false)"
      (focusin)="focusWithin.set(true)"
      (focusout)="onFocusOut($event)">
      <div class="event-hero__court" aria-hidden="true"></div>
      @if (currentEvent(); as activeEvent) {
        <h1 class="sr-only">Hoje em quadra — {{ activeEvent.eventName }}</h1>

        <div class="hero-content">
          <div class="sports-nav">
            <div class="sports-nav__context">
              <span class="sports-nav__item is-active">Todos</span>
              <span class="sports-nav__item">Agenda pública</span>
            </div>
            <span class="sports-nav__position" aria-live="polite">{{ eventPosition() }}</span>
          </div>

          <div
            class="hero-stage"
            [class.is-switching]="switching()"
            [attr.data-active-composition]="activeIndex()"
            role="region"
            aria-label="Destaque do próximo evento publicado"
            aria-live="off">
            <section
              class="hero-slide hero-slide--manifesto"
              [class.is-active]="isCompositionInteractive(0)"
              [attr.aria-hidden]="!isCompositionInteractive(0)"
              [attr.inert]="!isCompositionInteractive(0) ? '' : null">
              <p class="manifesto-title" aria-hidden="true">Hoje em</p>
              <div class="manifesto-label">
                <span>Próximo evento</span>
                <span>{{ activeEvent.shortDateLabel }}</span>
              </div>
              <div class="manifesto-beam">
                <strong>Quadra.</strong>
                <div class="manifesto-clock">
                  <time [attr.datetime]="activeEvent.scheduledAt">{{ activeEvent.timeLabel }}</time>
                  <span>{{ activeEvent.zoneLabel }}</span>
                </div>
              </div>
              <div class="manifesto-context">
                <strong
                  >{{ eventCountDisplay() }}
                  {{ eventCount() === 1 ? 'evento publicado' : 'eventos publicados' }}</strong
                >
                <span>Eventos publicados · programação do campus</span>
              </div>
              <div class="manifesto-match">
                <h2>{{ activeEvent.eventName }}</h2>
                <p>{{ activeEvent.contextLabel }} · {{ activeEvent.venueLabel }}</p>
                <a [href]="activeEvent.detailHref" [tabIndex]="isCompositionInteractive(0) ? 0 : -1"
                  >Abrir evento</a
                >
              </div>
            </section>

            <section
              class="hero-slide hero-slide--direct"
              [class.is-active]="isCompositionInteractive(1)"
              [attr.aria-hidden]="!isCompositionInteractive(1)"
              [attr.inert]="!isCompositionInteractive(1) ? '' : null">
              <p class="direct-title" aria-hidden="true">
                <span>Hoje em</span>
                <strong>quadra.</strong>
              </p>
              <aside class="direct-board">
                <div class="direct-board__label">Próximo evento</div>
                <time [attr.datetime]="activeEvent.scheduledAt">{{ activeEvent.timeLabel }}</time>
                <h2>{{ activeEvent.eventName }}</h2>
                <p>
                  {{ activeEvent.contextLabel }} · {{ activeEvent.venueLabel }} ·
                  {{ activeEvent.zoneLabel }}
                </p>
                <a [href]="activeEvent.detailHref" [tabIndex]="isCompositionInteractive(1) ? 0 : -1"
                  >Abrir evento</a
                >
              </aside>
            </section>
          </div>
        </div>
      }
    </section>
  `,
  styles: `
    :host {
      display: block;
      min-width: 0;
    }
    .event-hero {
      position: relative;
      min-width: 0;
      overflow: hidden;
      padding: var(--bt-space-8) 0 var(--bt-space-12);
      background: var(--bt-ink);
      color: var(--bt-hero-cream);
      isolation: isolate;
    }
    .hero-content {
      position: relative;
      z-index: 1;
      width: var(--bt-container);
      margin-inline: auto;
    }
    .sports-nav {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: var(--bt-space-4);
      margin-bottom: var(--bt-space-8);
    }
    .sports-nav__context {
      display: flex;
      min-width: 0;
      align-items: center;
      gap: var(--bt-space-4);
      overflow-x: auto;
      scrollbar-width: none;
    }
    .sports-nav__context::-webkit-scrollbar {
      display: none;
    }
    .sports-nav__position {
      flex: 0 0 auto;
      color: var(--bt-hero-muted);
      font-size: var(--bt-type-label);
      font-weight: 600;
      letter-spacing: 0.1em;
      text-transform: uppercase;
    }
    .sports-nav__item {
      display: inline-flex;
      min-height: var(--bt-min-touch-target);
      padding: 0 var(--bt-space-2);
      flex: 0 0 auto;
      align-items: center;
      border-bottom: 1px solid transparent;
      color: var(--bt-text-muted);
      font-size: var(--bt-type-label);
      font-weight: 600;
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }
    .sports-nav__item.is-active {
      border-bottom-color: var(--bt-signal);
      color: var(--bt-hero-cream);
    }
    .event-hero__court {
      position: absolute;
      top: 118px;
      right: -8vw;
      width: min(76vw, 1080px);
      height: 540px;
      border: 1px solid var(--bt-border);
      opacity: 0.62;
    }
    .event-hero__court::after {
      content: '';
      position: absolute;
      top: 50%;
      left: 50%;
      width: 180px;
      height: 180px;
      border: 1px solid var(--bt-border);
      border-radius: 50%;
      transform: translate(-50%, -50%);
    }
    .hero-stage {
      position: relative;
      display: grid;
      min-width: 0;
      min-height: 460px;
      isolation: isolate;
      overflow: hidden;
    }
    .hero-slide {
      grid-area: 1 / 1;
      display: grid;
      min-width: 0;
      opacity: 0;
      pointer-events: none;
      transform: translate3d(0, 20px, 0) scale(0.985);
      transition:
        opacity 240ms ease,
        transform 680ms cubic-bezier(0.16, 1, 0.3, 1);
    }
    .hero-slide.is-active {
      z-index: 1;
      opacity: 1;
      pointer-events: auto;
      transform: translate3d(0, 0, 0) scale(1);
    }
    .hero-slide--manifesto {
      grid-template-columns: minmax(0, 1.18fr) minmax(360px, 0.82fr);
      grid-template-areas: 'title label' 'beam beam' 'context match';
      align-items: stretch;
      border-block: 1px solid var(--bt-border);
    }
    .manifesto-title {
      grid-area: title;
      align-self: end;
      margin: 0;
      padding: var(--bt-space-8) var(--bt-space-8) var(--bt-space-6) 0;
      color: var(--bt-hero-cream);
      font:
        600 var(--bt-type-display)/0.72 'Barlow Condensed',
        sans-serif;
      letter-spacing: -0.025em;
      text-transform: uppercase;
    }
    .manifesto-label {
      grid-area: label;
      display: flex;
      padding: var(--bt-space-8) 0 var(--bt-space-6) var(--bt-space-8);
      align-items: flex-end;
      justify-content: space-between;
      gap: var(--bt-space-4);
      border-left: 1px solid var(--bt-border);
      color: var(--bt-hero-muted);
      font-size: var(--bt-type-label);
      font-weight: 600;
      letter-spacing: 0.11em;
      text-transform: uppercase;
    }
    .manifesto-label span:first-child {
      display: flex;
      align-items: center;
      gap: var(--bt-space-2);
    }
    .manifesto-beam {
      grid-area: beam;
      display: grid;
      min-width: 0;
      grid-template-columns: minmax(0, 1.18fr) minmax(360px, 0.82fr);
      overflow: hidden;
      background: var(--bt-signal-deep);
      color: var(--bt-hero-on-deep);
    }
    .manifesto-beam > strong {
      align-self: center;
      padding: var(--bt-space-1) var(--bt-space-8) var(--bt-space-2) 0;
      font:
        600 var(--bt-type-display)/0.72 'Barlow Condensed',
        sans-serif;
      letter-spacing: -0.025em;
      text-transform: uppercase;
    }
    .manifesto-clock {
      display: flex;
      padding: var(--bt-space-4) 0 var(--bt-space-4) var(--bt-space-8);
      align-items: flex-end;
      justify-content: space-between;
      gap: var(--bt-space-4);
      border-left: 1px solid var(--bt-ink);
    }
    .manifesto-clock time {
      font:
        600 min(var(--bt-type-display), 7.625rem)/0.72 'Barlow Condensed',
        sans-serif;
      letter-spacing: -0.02em;
      font-variant-numeric: tabular-nums;
    }
    .manifesto-clock span {
      padding: 0 var(--bt-space-4) var(--bt-space-1) 0;
      font-size: var(--bt-type-label);
      font-weight: 600;
      letter-spacing: 0.12em;
      text-transform: uppercase;
    }
    .manifesto-context {
      grid-area: context;
      display: flex;
      padding: var(--bt-space-6) var(--bt-space-8) var(--bt-space-1) 0;
      align-items: flex-start;
      justify-content: space-between;
      gap: var(--bt-space-6);
      color: var(--bt-hero-muted);
      font-size: var(--bt-type-label);
    }
    .manifesto-context strong {
      color: var(--bt-hero-cream);
      font-size: var(--bt-type-label);
      letter-spacing: 0.08em;
      text-transform: uppercase;
    }
    .manifesto-match {
      grid-area: match;
      padding: var(--bt-space-6) 0 var(--bt-space-1) var(--bt-space-8);
      border-left: 1px solid var(--bt-border);
    }
    h2 {
      margin: 0;
      color: var(--bt-hero-cream);
      font:
        600 var(--bt-type-heading)/0.9 'Barlow Condensed',
        sans-serif;
      overflow-wrap: anywhere;
      text-transform: uppercase;
    }
    .manifesto-match p,
    .direct-board p {
      margin: var(--bt-space-2) 0 var(--bt-space-4);
      color: var(--bt-hero-muted);
    }
    a {
      display: inline-grid;
      min-height: var(--bt-min-touch-target, 44px);
      padding: 0 var(--bt-space-6);
      place-items: center;
      border-radius: var(--bt-radius-control);
      background: var(--bt-signal);
      color: var(--bt-ink);
      font-weight: 600;
      text-decoration: none;
      transition:
        background 200ms ease,
        color 200ms ease;
    }
    a:hover {
      background: var(--bt-signal-soft);
      color: var(--bt-ink);
    }
    .hero-slide--direct {
      grid-template-columns: minmax(0, 1.35fr) minmax(300px, 0.65fr);
      gap: var(--bt-space-12);
      align-items: end;
      padding: var(--bt-space-8) 0 var(--bt-space-6);
      border-block: 1px solid var(--bt-border);
    }
    .direct-title {
      margin: 0;
      color: var(--bt-hero-cream);
      font:
        600 var(--bt-type-display)/0.72 'Barlow Condensed',
        sans-serif;
      letter-spacing: -0.025em;
      text-transform: uppercase;
    }
    .direct-title span {
      display: block;
    }
    .direct-title strong {
      display: inline-block;
      margin-top: var(--bt-space-2);
      padding: 0 var(--bt-space-2) var(--bt-space-2);
      background: var(--bt-signal-deep);
      color: var(--bt-hero-on-deep);
      box-shadow:
        18px 0 0 var(--bt-signal-deep),
        -18px 0 0 var(--bt-signal-deep);
      transform: rotate(-2deg);
      transform-origin: left center;
    }
    .direct-board {
      min-width: 0;
      padding: var(--bt-space-6);
      border: 1px solid var(--bt-border-strong);
      background: color-mix(in srgb, var(--bt-ink) 88%, transparent);
    }
    .direct-board__label {
      display: flex;
      margin: calc(-1 * var(--bt-space-6)) calc(-1 * var(--bt-space-6)) var(--bt-space-12);
      padding: var(--bt-space-2) var(--bt-space-4);
      align-items: center;
      gap: var(--bt-space-2);
      background: var(--bt-signal-deep);
      color: var(--bt-hero-on-deep);
      font-size: var(--bt-type-label);
      font-weight: 600;
      letter-spacing: 0.12em;
      text-transform: uppercase;
    }
    .direct-board time {
      display: block;
      color: var(--bt-signal);
      font:
        600 min(var(--bt-type-display), 4.625rem)/0.8 'Barlow Condensed',
        sans-serif;
      font-variant-numeric: tabular-nums;
    }
    .direct-board h2 {
      margin-top: var(--bt-space-6);
      font-size: var(--bt-type-heading);
    }
    @media (max-width: 760px) {
      .event-hero {
        padding: var(--bt-space-6) 0 var(--bt-space-8);
      }
      .sports-nav {
        display: grid;
        margin-bottom: var(--bt-space-6);
        justify-items: start;
      }
      .event-hero__court {
        top: 160px;
        right: -260px;
        width: 600px;
        height: 430px;
      }
      .hero-stage {
        min-height: 0;
      }
      .hero-slide--manifesto {
        grid-template-columns: 1fr;
        grid-template-areas: 'title' 'label' 'beam' 'match' 'context';
      }
      .manifesto-title {
        padding: var(--bt-space-8) 0 var(--bt-space-4);
      }
      .manifesto-label {
        padding: var(--bt-space-2) 0;
        border-top: 1px solid var(--bt-border);
        border-left: 0;
      }
      .manifesto-beam {
        grid-template-columns: 1fr;
      }
      .manifesto-beam > strong {
        padding: var(--bt-space-2) var(--bt-space-4) var(--bt-space-1);
      }
      .manifesto-clock {
        padding: var(--bt-space-2) var(--bt-space-4);
        border-top: 1px solid var(--bt-ink);
        border-left: 0;
      }
      .manifesto-clock time {
        font-size: var(--bt-type-display);
      }
      .manifesto-match {
        padding: var(--bt-space-6) 0;
        border-top: 1px solid var(--bt-border);
        border-left: 0;
      }
      .manifesto-match h2 {
        font-size: var(--bt-type-heading);
      }
      .manifesto-context {
        padding: var(--bt-space-4) 0 var(--bt-space-1);
        border-top: 1px solid var(--bt-border);
        flex-direction: column;
        gap: var(--bt-space-2);
      }
      .hero-slide--direct {
        grid-template-columns: 1fr;
        gap: var(--bt-space-12);
        min-height: 0;
        padding: var(--bt-space-8) 0 var(--bt-space-4);
      }
      .direct-title {
        font-size: var(--bt-type-display);
      }
      .direct-title strong {
        box-shadow:
          8px 0 0 var(--bt-signal-deep),
          -8px 0 0 var(--bt-signal-deep);
      }
      .direct-board {
        padding: var(--bt-space-4);
      }
      .direct-board__label {
        margin: calc(-1 * var(--bt-space-4)) calc(-1 * var(--bt-space-4)) var(--bt-space-8);
      }
      .direct-board time {
        font-size: var(--bt-type-display);
      }
      .direct-board h2 {
        font-size: var(--bt-type-heading);
      }
    }
    @media (prefers-reduced-motion: reduce) {
      .hero-slide {
        transition: none;
        transform: none;
      }
      a {
        transition: none;
      }
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AutoEventHeroComponent implements AfterViewInit, OnDestroy {
  readonly events = input<readonly PublicAgendaEventViewModel[]>([]);
  readonly event = input<PublicAgendaEventViewModel | null>(null);
  readonly eventCount = input(0);
  readonly eventCountDisplay = computed(() => String(this.eventCount()).padStart(2, '0'));
  readonly currentEventIndex = signal(0);
  readonly currentEvent = computed(() => {
    const list = this.events();
    if (list.length > 0) {
      return list[this.currentEventIndex() % list.length];
    }
    return this.event();
  });
  readonly eventPosition = computed(() => {
    const count = this.events().length;
    if (count === 0) {
      return '1 de 1';
    }
    return `${(this.currentEventIndex() % count) + 1} de ${count}`;
  });

  @ViewChild('heroRoot', { static: true })
  private heroRoot?: ElementRef<HTMLElement>;

  readonly activeIndex = signal(0);
  readonly switching = signal(false);
  readonly hovered = signal(false);
  readonly focusWithin = signal(false);
  private readonly visible = signal(false);
  private readonly documentHidden = signal(
    typeof document !== 'undefined' ? document.hidden : false,
  );
  private readonly reducedMotion = signal(false);
  private readonly motionPaused = computed(
    () =>
      this.hovered() ||
      this.focusWithin() ||
      !this.visible() ||
      this.documentHidden() ||
      this.reducedMotion(),
  );

  private intersectionObserver?: IntersectionObserver;
  private mediaQuery?: MediaQueryList;
  private finishTimer?: ReturnType<typeof setTimeout>;

  private readonly cycle = effect((onCleanup) => {
    this.activeIndex();
    this.currentEventIndex();
    this.events();
    if (this.motionPaused()) {
      this.cancelSwitch();
      if (this.reducedMotion()) {
        this.activeIndex.set(0);
      }
      return;
    }

    const timerId = setTimeout(() => this.switchComposition(), CYCLE_MS);
    onCleanup(() => clearTimeout(timerId));
  });

  ngAfterViewInit(): void {
    this.mediaQuery =
      typeof window !== 'undefined'
        ? window.matchMedia('(prefers-reduced-motion: reduce)')
        : undefined;
    if (this.mediaQuery) {
      this.reducedMotion.set(this.mediaQuery.matches);
      this.mediaQuery.addEventListener('change', this.onMotionPreferenceChange);
    }

    const root = this.heroRoot?.nativeElement;
    if (root && typeof IntersectionObserver !== 'undefined') {
      this.intersectionObserver = new IntersectionObserver(([entry]) => {
        this.visible.set(entry?.isIntersecting ?? false);
      });
      this.intersectionObserver.observe(root);
    } else {
      this.visible.set(true);
    }
    document.addEventListener('visibilitychange', this.onVisibilityChange);
  }

  ngOnDestroy(): void {
    this.cancelSwitch();
    this.intersectionObserver?.disconnect();
    this.mediaQuery?.removeEventListener('change', this.onMotionPreferenceChange);
    document.removeEventListener('visibilitychange', this.onVisibilityChange);
    this.cycle.destroy();
  }

  isCompositionInteractive(index: number): boolean {
    return this.activeIndex() === index;
  }

  onFocusOut(event: FocusEvent): void {
    const root = event.currentTarget as HTMLElement;
    const next = event.relatedTarget;
    this.focusWithin.set(next instanceof Node && root.contains(next));
  }

  private switchComposition(): void {
    this.cancelSwitch();
    this.switching.set(true);
    const events = this.events();
    if (events.length > 1) {
      this.currentEventIndex.update((index) => (index + 1) % events.length);
    }
    this.activeIndex.update((index) => (index === 0 ? 1 : 0));
    this.finishTimer = setTimeout(() => this.switching.set(false), SWITCH_DURATION_MS);
  }

  private cancelSwitch(): void {
    if (this.finishTimer) {
      clearTimeout(this.finishTimer);
      this.finishTimer = undefined;
    }
    this.switching.set(false);
  }

  private readonly onVisibilityChange = (): void => {
    this.documentHidden.set(document.hidden);
  };

  private readonly onMotionPreferenceChange = (event: MediaQueryListEvent): void => {
    this.reducedMotion.set(event.matches);
  };
}
