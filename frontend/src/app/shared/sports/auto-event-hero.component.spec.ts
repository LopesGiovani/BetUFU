import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { PublicAgendaEventViewModel } from '../../features/sports/public-agenda/public-agenda-api.adapter';
import { AutoEventHeroComponent } from './auto-event-hero.component';

const EVENT: PublicAgendaEventViewModel = {
  id: 'evt-hero',
  competitionId: 'cmp-1',
  editionId: 'ed-1',
  competitionName: 'Jogos Universitários',
  editionName: 'Edição 2026',
  title: 'Engenharia contra Direito',
  eventName: 'Engenharia × Direito',
  scheduledAt: '2026-08-29T21:30:00Z',
  timestamp: 1,
  dateKey: '2026-08-29',
  dateLabel: 'sábado, 29 de agosto de 2026',
  shortDateLabel: '29 ago. 2026',
  dayLabel: '29',
  monthYearLabel: 'agosto de 2026',
  timeLabel: '18:30',
  zoneLabel: 'BRT',
  formatLabel: 'Confronto',
  venueLabel: 'Local a definir',
  contextLabel: 'Confronto · Jogos Universitários · Edição 2026',
  detailHref: '/eventos/evt-hero',
};

describe('AutoEventHeroComponent', () => {
  beforeEach(() => {
    vi.stubGlobal('IntersectionObserver', class {
      constructor(callback: IntersectionObserverCallback) {
        callback(
          [{ isIntersecting: true } as IntersectionObserverEntry],
          this as unknown as IntersectionObserver,
        );
      }
      observe(): void {}
      disconnect(): void {}
    });
    Object.defineProperty(window, 'matchMedia', {
      configurable: true,
      value: vi.fn().mockReturnValue({
        matches: false,
        addEventListener: vi.fn(),
        removeEventListener: vi.fn(),
      }),
    });
  });

  afterEach(() => {
    vi.useRealTimers();
    vi.unstubAllGlobals();
  });

  it('[M1] keeps inactive composition inert and outside tab order', async () => {
    await TestBed.configureTestingModule({ imports: [AutoEventHeroComponent] }).compileComponents();
    const fixture = TestBed.createComponent(AutoEventHeroComponent);
    fixture.componentRef.setInput('event', EVENT);
    fixture.componentRef.setInput('eventCount', 3);
    fixture.detectChanges();

    const slides = fixture.nativeElement.querySelectorAll('.hero-slide') as NodeListOf<HTMLElement>;
    const links = fixture.nativeElement.querySelectorAll('.hero-slide a') as NodeListOf<HTMLAnchorElement>;
    expect(slides).toHaveLength(2);
    expect(slides[0].getAttribute('aria-hidden')).toBe('false');
    expect(slides[0].hasAttribute('inert')).toBe(false);
    expect(links[0].tabIndex).toBe(0);
    expect(slides[1].getAttribute('aria-hidden')).toBe('true');
    expect(slides[1].hasAttribute('inert')).toBe(true);
    expect(links[1].tabIndex).toBe(-1);
    expect(fixture.componentInstance.isCompositionInteractive(0)).toBe(true);
    expect(fixture.componentInstance.isCompositionInteractive(1)).toBe(false);
  });

  it('preserva a composição enquanto a preferência manual estiver pausada', async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({ imports: [AutoEventHeroComponent] }).compileComponents();
    const fixture = TestBed.createComponent(AutoEventHeroComponent);
    fixture.componentRef.setInput('event', EVENT);
    fixture.componentRef.setInput('paused', true);
    fixture.detectChanges();

    vi.advanceTimersByTime(7_000);
    fixture.detectChanges();
    expect(fixture.componentInstance.activeIndex()).toBe(0);
  });

  it('mantém hora e fuso na composição direta ativa', async () => {
    await TestBed.configureTestingModule({ imports: [AutoEventHeroComponent] }).compileComponents();
    const fixture = TestBed.createComponent(AutoEventHeroComponent);
    fixture.componentRef.setInput('event', EVENT);
    fixture.componentInstance.activeIndex.set(1);
    fixture.detectChanges();

    const directSlide = fixture.nativeElement.querySelector('.hero-slide--direct') as HTMLElement;
    expect(directSlide.getAttribute('aria-hidden')).toBe('false');
    expect(directSlide.querySelector('time')?.textContent).toContain('18:30');
    expect(directSlide.textContent).toContain('BRT');
  });

  it('cancela uma troca em curso quando o ponteiro entra no hero', async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({ imports: [AutoEventHeroComponent] }).compileComponents();
    const fixture = TestBed.createComponent(AutoEventHeroComponent);
    fixture.componentRef.setInput('event', EVENT);
    fixture.detectChanges();

    vi.advanceTimersByTime(5_200);
    fixture.detectChanges();
    expect(fixture.componentInstance.switching()).toBe(true);

    fixture.componentInstance.hovered.set(true);
    TestBed.flushEffects();
    fixture.detectChanges();
    vi.advanceTimersByTime(700);
    fixture.detectChanges();

    expect(fixture.componentInstance.switching()).toBe(false);
    expect(fixture.componentInstance.activeIndex()).toBe(0);

    fixture.componentInstance.hovered.set(false);
    TestBed.flushEffects();
    fixture.detectChanges();
    vi.advanceTimersByTime(5_600);
    fixture.detectChanges();
    expect(fixture.componentInstance.activeIndex()).toBe(1);
  });
});
