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

const SECOND_EVENT: PublicAgendaEventViewModel = {
  ...EVENT,
  id: 'evt-hero-2',
  title: 'Medicina contra Computação',
  eventName: 'Medicina × Computação',
  scheduledAt: '2026-08-29T23:30:00Z',
  timeLabel: '20:30',
  detailHref: '/eventos/evt-hero-2',
};

describe('AutoEventHeroComponent', () => {
  beforeEach(() => {
    vi.stubGlobal(
      'IntersectionObserver',
      class {
        constructor(callback: IntersectionObserverCallback) {
          callback(
            [{ isIntersecting: true } as IntersectionObserverEntry],
            this as unknown as IntersectionObserver,
          );
        }
        observe(): void {}
        disconnect(): void {}
      },
    );
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
    const links = fixture.nativeElement.querySelectorAll(
      '.hero-slide a',
    ) as NodeListOf<HTMLAnchorElement>;
    expect(slides).toHaveLength(2);
    expect(slides[0].getAttribute('aria-hidden')).toBe('false');
    expect(slides[0].hasAttribute('inert')).toBe(false);
    expect(links[0].tabIndex).toBe(0);
    expect(links[0].getAttribute('href')).toBe('/eventos/evt-hero');
    expect(slides[1].getAttribute('aria-hidden')).toBe('true');
    expect(slides[1].hasAttribute('inert')).toBe(true);
    expect(links[1].tabIndex).toBe(-1);
    expect(links[1].getAttribute('href')).toBe('/eventos/evt-hero');
    expect(fixture.componentInstance.isCompositionInteractive(0)).toBe(true);
    expect(fixture.componentInstance.isCompositionInteractive(1)).toBe(false);
  });

  it('rotaciona o evento e alterna a composição a cada 3 segundos', async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({ imports: [AutoEventHeroComponent] }).compileComponents();
    const fixture = TestBed.createComponent(AutoEventHeroComponent);
    fixture.componentRef.setInput('event', EVENT);
    fixture.componentRef.setInput('events', [EVENT, SECOND_EVENT]);
    fixture.componentRef.setInput('eventCount', 2);
    fixture.detectChanges();

    expect(fixture.componentInstance.currentEvent()?.id).toBe('evt-hero');
    expect(fixture.componentInstance.activeIndex()).toBe(0);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('1 de 2');

    vi.advanceTimersByTime(3_000);
    TestBed.flushEffects();
    fixture.detectChanges();

    expect(fixture.componentInstance.currentEvent()?.id).toBe('evt-hero-2');
    expect(fixture.componentInstance.activeIndex()).toBe(1);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Medicina × Computação');
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('2 de 2');

    vi.advanceTimersByTime(3_000);
    TestBed.flushEffects();
    fixture.detectChanges();

    expect(fixture.componentInstance.currentEvent()?.id).toBe('evt-hero');
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

  it('pausa a rotação durante hover e retoma ao sair do hero', async () => {
    vi.useFakeTimers();
    await TestBed.configureTestingModule({ imports: [AutoEventHeroComponent] }).compileComponents();
    const fixture = TestBed.createComponent(AutoEventHeroComponent);
    fixture.componentRef.setInput('event', EVENT);
    fixture.componentRef.setInput('events', [EVENT, SECOND_EVENT]);
    fixture.detectChanges();

    fixture.componentInstance.hovered.set(true);
    TestBed.flushEffects();
    fixture.detectChanges();
    vi.advanceTimersByTime(4_000);
    fixture.detectChanges();

    expect(fixture.componentInstance.currentEventIndex()).toBe(0);
    expect(fixture.componentInstance.activeIndex()).toBe(0);

    fixture.componentInstance.hovered.set(false);
    TestBed.flushEffects();
    fixture.detectChanges();
    vi.advanceTimersByTime(3_000);
    TestBed.flushEffects();
    fixture.detectChanges();

    expect(fixture.componentInstance.currentEventIndex()).toBe(1);
    expect(fixture.componentInstance.activeIndex()).toBe(1);
  });

  it('não renderiza o controle manual de pausa', async () => {
    await TestBed.configureTestingModule({ imports: [AutoEventHeroComponent] }).compileComponents();
    const fixture = TestBed.createComponent(AutoEventHeroComponent);
    fixture.componentRef.setInput('event', EVENT);
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('app-highlight-motion-control')).toBeNull();
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('Pausar destaques');
  });
});
