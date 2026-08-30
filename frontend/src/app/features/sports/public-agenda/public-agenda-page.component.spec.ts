import { HttpErrorResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { SportsPublicService } from '../../../api/generated/api/sports-public.service';
import { PublicSportsEventPage } from '../../../api/generated/model/public-sports-event-page';
import { PublicAgendaApiAdapter } from './public-agenda-api.adapter';
import { PublicAgendaPageComponent } from './public-agenda-page.component';
import { PublicAgendaStore } from './public-agenda.store';
import { AutoEventHeroComponent } from '../../../shared/sports/auto-event-hero.component';

function publishedPage(): PublicSportsEventPage {
  const first = new Date(Date.now() + 60 * 60 * 1_000).toISOString();
  const second = new Date(Date.now() + 2 * 60 * 60 * 1_000).toISOString();
  return {
    items: [
      {
        eventId: '11111111-1111-4111-8111-111111111111',
        competitionId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        editionId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        competitionName: 'Jogos Universitários',
        editionName: 'Edição 2026',
        title: 'Engenharia contra Direito',
        format: 'HEAD_TO_HEAD',
        scheduledAt: first,
        zoneId: 'America/Sao_Paulo',
        venueState: 'LOCAL_A_DEFINIR',
        slots: [
          { slotNo: 1, displayLabel: 'Engenharia' },
          { slotNo: 2, displayLabel: 'Direito' },
        ],
      },
      {
        eventId: '22222222-2222-4222-8222-222222222222',
        competitionId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
        editionId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
        competitionName: 'Jogos Universitários',
        editionName: 'Edição 2026',
        title: 'Medicina contra Computação',
        format: 'HEAD_TO_HEAD',
        scheduledAt: second,
        zoneId: 'America/Sao_Paulo',
        venueState: 'LOCAL_A_DEFINIR',
        slots: [
          { slotNo: 1, displayLabel: 'Medicina' },
          { slotNo: 2, displayLabel: 'Computação' },
        ],
      },
    ],
    page: 0,
    size: 50,
    totalElements: 2,
    totalPages: 1,
    hasNext: false,
  };
}

describe('PublicAgendaPageComponent', () => {
  let service: { listPublishedSportsEvents: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    service = { listPublishedSportsEvents: vi.fn() };
    vi.stubGlobal('ResizeObserver', class { observe(): void {} disconnect(): void {} });
    vi.stubGlobal('IntersectionObserver', class {
      constructor(callback: IntersectionObserverCallback) {
        callback([{ isIntersecting: true } as IntersectionObserverEntry], this as unknown as IntersectionObserver);
      }
      observe(): void {}
      disconnect(): void {}
    });
    vi.stubGlobal('requestAnimationFrame', vi.fn().mockReturnValue(1));
    vi.stubGlobal('cancelAnimationFrame', vi.fn());
    vi.spyOn(HTMLCanvasElement.prototype, 'getContext').mockReturnValue(null);
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
    vi.restoreAllMocks();
    vi.unstubAllGlobals();
  });

  async function render() {
    await TestBed.configureTestingModule({
      imports: [PublicAgendaPageComponent],
      providers: [
        PublicAgendaApiAdapter,
        PublicAgendaStore,
        { provide: SportsPublicService, useValue: service },
      ],
    }).compileComponents();
    const fixture = TestBed.createComponent(PublicAgendaPageComponent);
    fixture.detectChanges();
    await fixture.whenStable();
    fixture.detectChanges();
    return fixture;
  }

  it('renderiza a Arena V com eventos reais e sem inventar modalidades ausentes do contrato', async () => {
    service.listPublishedSportsEvents.mockReturnValue(of(publishedPage()));
    const fixture = await render();
    const root = fixture.nativeElement as HTMLElement;

    expect(service.listPublishedSportsEvents).toHaveBeenCalledTimes(1);
    expect(root.querySelector('h1')?.textContent).toContain('Hoje em quadra');
    expect(root.querySelectorAll('app-event-row')).toHaveLength(2);
    expect(root.textContent).toContain('Engenharia × Direito');
    expect(root.textContent).toContain('Jogos Universitários');
    expect(root.textContent).toContain('Local a definir');
    expect(root.textContent).not.toMatch(/Futsal|Vôlei|Xadrez/u);
    expect(root.querySelector('.ribbon__status')?.textContent).toContain('Agenda publicada');
  });

  it('exibe o vazio público literal quando a API retorna uma página válida sem eventos', async () => {
    service.listPublishedSportsEvents.mockReturnValue(of({
      items: [],
      page: 0,
      size: 50,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
    }));
    const fixture = await render();
    const root = fixture.nativeElement as HTMLElement;

    expect(root.textContent).toContain('Nenhum evento publicado');
    expect(root.textContent).toContain('Ainda não há eventos publicados para acompanhar.');
    expect(root.querySelectorAll('app-event-row')).toHaveLength(0);
  });

  it('preserva mensagem pública segura e correlação quando a leitura falha', async () => {
    service.listPublishedSportsEvents.mockReturnValue(
      throwError(() => new HttpErrorResponse({
        status: 503,
        error: { correlationId: 'corr-agenda-503' },
      })),
    );
    const fixture = await render();
    await vi.waitFor(() => expect(fixture.componentInstance.store.isError()).toBe(true), {
      timeout: 3_000,
    });
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Não foi possível carregar a agenda');
    expect(root.textContent).toContain('corr-agenda-503');
  });

  it('mantém filtros editáveis e usa cópia honesta quando o deep link não retorna opções', async () => {
    service.listPublishedSportsEvents.mockReturnValue(of({
      items: [],
      page: 0,
      size: 50,
      totalElements: 0,
      totalPages: 0,
      hasNext: false,
    }));
    const fixture = await render();

    await fixture.componentInstance.store.loadAgenda({
      period: 'today',
      competitionId: 'competition-from-link',
      editionId: 'edition-from-link',
    });
    fixture.componentInstance.filterForm.setValue({
      competitionId: 'competition-from-link',
      editionId: 'edition-from-link',
      sportId: '',
      categoryId: '',
      phaseId: '',
      state: '',
    });
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.textContent).toContain('Nenhum evento encontrado');
    expect(root.querySelector('.ribbon__copy')?.textContent).toContain('Nenhum evento encontrado');
    const filterButton = root.querySelector<HTMLButtonElement>('.filter-toggle');
    expect(filterButton).not.toBeNull();
    filterButton?.click();
    fixture.detectChanges();
    expect(root.textContent).toContain('Competição selecionada (detalhes indisponíveis)');
    expect(root.textContent).toContain('Edição selecionada (detalhes indisponíveis)');
  });

  it('preserva competição e edição ao trocar o período', async () => {
    service.listPublishedSportsEvents.mockReturnValue(of(publishedPage()));
    const fixture = await render();
    await fixture.componentInstance.store.loadAgenda({
      period: 'today',
      competitionId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
      editionId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    });

    await fixture.componentInstance.selectPeriod('upcoming');

    expect(service.listPublishedSportsEvents).toHaveBeenCalledWith(expect.objectContaining({
      competitionId: 'aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa',
      editionId: 'bbbbbbbb-bbbb-4bbb-8bbb-bbbbbbbbbbbb',
    }));
    expect(fixture.componentInstance.store.query().period).toBe('upcoming');
  });

  it('impede resposta antiga de sobrescrever a consulta mais recente', async () => {
    const firstResponse = new Subject<PublicSportsEventPage>();
    const secondResponse = new Subject<PublicSportsEventPage>();
    service.listPublishedSportsEvents
      .mockReturnValueOnce(firstResponse)
      .mockReturnValueOnce(secondResponse);
    await TestBed.configureTestingModule({
      providers: [
        PublicAgendaApiAdapter,
        PublicAgendaStore,
        { provide: SportsPublicService, useValue: service },
      ],
    }).compileComponents();
    const store = TestBed.inject(PublicAgendaStore);

    const oldRequest = store.loadAgenda({ period: 'today' });
    const newRequest = store.loadAgenda({ period: 'upcoming' });
    const latest = publishedPage();
    latest.items[0].title = 'Consulta mais recente';
    secondResponse.next(latest);
    firstResponse.next({ ...publishedPage(), items: [publishedPage().items[1]] });
    await Promise.all([oldRequest, newRequest]);

    expect(store.query().period).toBe('upcoming');
    expect(store.events()).toHaveLength(2);
    expect(store.events()[0].title).toBe('Consulta mais recente');
  });

  it('atualiza o próximo evento no horário sem depender de nova resposta da API', async () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-08-29T20:00:00.000Z'));
    const page = publishedPage();
    page.items[0].scheduledAt = new Date(Date.now() + 1_000).toISOString();
    page.items[1].scheduledAt = new Date(Date.now() - 1_000).toISOString();
    service.listPublishedSportsEvents.mockReturnValue(of(page));
    await TestBed.configureTestingModule({
      providers: [
        PublicAgendaApiAdapter,
        PublicAgendaStore,
        { provide: SportsPublicService, useValue: service },
      ],
    }).compileComponents();
    const store = TestBed.inject(PublicAgendaStore);

    await store.loadAgenda({ period: 'today' });
    TestBed.flushEffects();
    expect(store.nextEvent()?.id).toBe('11111111-1111-4111-8111-111111111111');
    expect(store.upcomingEvents()).toHaveLength(1);

    vi.advanceTimersByTime(1_001);
    expect(store.nextEvent()).toBeNull();
    expect(store.upcomingEvents()).toHaveLength(0);
  });

  it('não anuncia eventos passados como próximos no ticker', async () => {
    const page = publishedPage();
    page.items.forEach((item, index) => {
      item.scheduledAt = new Date(Date.now() - (index + 1) * 60_000).toISOString();
    });
    service.listPublishedSportsEvents.mockReturnValue(of(page));
    const fixture = await render();

    expect(fixture.componentInstance.store.nextEvent()).toBeNull();
    expect(fixture.componentInstance.tickerEvents()).toHaveLength(0);
    expect((fixture.nativeElement as HTMLElement).querySelector('.ticker')).toBeNull();
  });

  it('propaga a pausa de interação do ticker para o hero', async () => {
    service.listPublishedSportsEvents.mockReturnValue(of(publishedPage()));
    const fixture = await render();
    fixture.componentInstance.tickerInteractionPaused.set(true);
    fixture.detectChanges();

    const hero = fixture.debugElement.query(By.directive(AutoEventHeroComponent))
      .componentInstance as AutoEventHeroComponent;
    expect(hero.sharedPaused()).toBe(true);
    expect(hero.paused()).toBe(false);
  });
});
