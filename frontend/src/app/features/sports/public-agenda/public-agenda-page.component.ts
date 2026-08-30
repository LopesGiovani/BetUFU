import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  OnInit,
  ViewChild,
  computed,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { startWith } from 'rxjs';
import { AutoEventHeroComponent } from '../../../shared/sports/auto-event-hero.component';
import { BroadcastTickerComponent } from '../../../shared/sports/broadcast-ticker.component';
import { EventRowComponent } from '../../../shared/sports/event-row.component';
import { HighlightMotionControlComponent } from '../../../shared/sports/highlight-motion-control.component';
import { SportRibbonComponent } from '../../../shared/sports/sport-ribbon.component';
import { GrainientBackgroundComponent } from '../../../shared/visual/grainient-background.component';
import { AgendaPeriod, PublicAgendaQuery } from './public-agenda-api.adapter';
import { PublicAgendaStore, agendaPeriodLabel } from './public-agenda.store';

@Component({
  selector: 'app-public-agenda-page',
  standalone: true,
  imports: [
    ReactiveFormsModule,
    AutoEventHeroComponent,
    BroadcastTickerComponent,
    EventRowComponent,
    HighlightMotionControlComponent,
    SportRibbonComponent,
    GrainientBackgroundComponent,
  ],
  templateUrl: './public-agenda-page.component.html',
  styleUrl: './public-agenda-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class PublicAgendaPageComponent implements OnInit {
  readonly store = inject(PublicAgendaStore);
  private readonly destroyRef = inject(DestroyRef);
  private readonly router = inject(Router, { optional: true });
  private readonly route = inject(ActivatedRoute, { optional: true });
  private lastRequestedQueryKey: string | null = null;

  @ViewChild('filterToggle') private filterToggle?: ElementRef<HTMLButtonElement>;
  @ViewChild('agendaTitle') private agendaTitle?: ElementRef<HTMLElement>;

  readonly motionPaused = signal(false);
  readonly heroInteractionPaused = signal(false);
  readonly tickerInteractionPaused = signal(false);
  readonly pendingLoad = signal(false);
  readonly composedMotionPaused = computed(() =>
    this.motionPaused() || this.heroInteractionPaused() || this.tickerInteractionPaused(),
  );
  readonly filtersOpen = signal(false);
  readonly filterAnnouncement = signal('');
  readonly filterForm = new FormGroup({
    competitionId: new FormControl('', { nonNullable: true }),
    editionId: new FormControl('', { nonNullable: true }),
    sportId: new FormControl('', { nonNullable: true }),
    categoryId: new FormControl('', { nonNullable: true }),
    phaseId: new FormControl('', { nonNullable: true }),
    state: new FormControl('', { nonNullable: true }),
  });

  readonly agendaTitleText = computed(() => agendaPeriodLabel(this.store.query().period));
  readonly agendaZoneHeading = computed(() => {
    const zone = this.store.nextEvent()?.zoneLabel.trim();
    if (!zone) {
      return 'Horário do evento';
    }
    return /^(BRT|GMT-?3|UTC-?3)$/i.test(zone) ? 'Horário de Brasília' : `Horário ${zone}`;
  });
  readonly agendaPublicationLabel = computed(() => {
    if (this.store.isStale()) {
      return 'Dados da última atualização';
    }
    if (this.store.isLoading()) {
      return 'Atualizando agenda';
    }
    return this.store.publishedResponseReceived() ? 'Agenda publicada' : 'Aguardando publicação';
  });
  readonly noNextEventCopy = computed(() =>
    this.store.query().period === 'today'
      ? 'Os eventos publicados de hoje já aconteceram. Consulte a agenda abaixo para acompanhar o dia.'
      : 'Não há eventos publicados próximos neste período.',
  );
  readonly activeFilterCount = computed(() =>
    Number(Boolean(this.store.query().competitionId))
    + Number(Boolean(this.store.query().editionId))
    + Number(Boolean(this.store.query().sportId))
    + Number(Boolean(this.store.query().categoryId))
    + Number(Boolean(this.store.query().phaseId))
    + Number(Boolean(this.store.query().state)),
  );
  readonly tickerEvents = computed(() => {
    const nextId = this.store.nextEvent()?.id;
    return this.store.upcomingEvents().filter((event) => event.id !== nextId).slice(0, 4);
  });
  readonly dateLockup = computed(() => {
    const firstGroup = this.store.eventGroups()[0];
    if (firstGroup) {
      return {
        day: firstGroup.dayLabel,
        monthYear: firstGroup.monthYearLabel,
      };
    }
    const now = new Date();
    return {
      day: new Intl.DateTimeFormat('pt-BR', { day: '2-digit' }).format(now),
      monthYear: new Intl.DateTimeFormat('pt-BR', {
        month: 'long',
        year: 'numeric',
      }).format(now),
    };
  });

  ngOnInit(): void {
    if (!this.route) {
      void this.store.loadAgenda(this.queryFromUrl());
      return;
    }

    this.route.queryParamMap
      .pipe(startWith(this.route.snapshot.queryParamMap), takeUntilDestroyed(this.destroyRef))
      .subscribe((params) => {
        const query = this.queryFromParamMap(params);
        this.filterForm.reset({
          competitionId: query.competitionId ?? '',
          editionId: query.editionId ?? '',
          sportId: query.sportId ?? '',
          categoryId: query.categoryId ?? '',
          phaseId: query.phaseId ?? '',
          state: query.state ?? '',
        });
        const queryKey = this.queryKey(query);
        if (queryKey === this.lastRequestedQueryKey && this.store.status() !== 'initial') {
          return;
        }
        this.lastRequestedQueryKey = queryKey;
        const shouldAnnounce = this.pendingLoad();
        void this.store.loadAgenda(query).finally(() => {
          if (queryKey !== this.lastRequestedQueryKey) {
            return;
          }
          this.pendingLoad.set(false);
          if (shouldAnnounce) {
            this.announceAndFocus();
          }
        });
      });

    this.route.fragment
      .pipe(startWith(this.route.snapshot.fragment), takeUntilDestroyed(this.destroyRef))
      .subscribe((fragment) => this.scrollToFragment(fragment));
  }

  async selectPeriod(period: AgendaPeriod): Promise<void> {
    if (this.store.query().period === period) {
      return;
    }
    const query: PublicAgendaQuery = { ...this.store.query(), period };
    const waitingForLoad = await this.syncUrlWithPending(query);
    if (!waitingForLoad) {
      this.announceAndFocus();
    }
  }

  toggleFilters(): void {
    this.filtersOpen.update((open) => !open);
  }

  closeFilters(event?: Event): void {
    event?.preventDefault();
    this.filtersOpen.set(false);
    queueMicrotask(() => this.filterToggle?.nativeElement.focus());
  }

  onCompetitionChange(): void {
    const competitionId = this.filterForm.controls.competitionId.value;
    const editionId = this.filterForm.controls.editionId.value;
    const editionStillAvailable = this.store
      .editionsForCompetition(competitionId || undefined)
      .some((edition) => edition.id === editionId);
    if (!editionStillAvailable) {
      this.filterForm.controls.editionId.setValue('');
      this.filterForm.controls.categoryId.setValue('');
      this.filterForm.controls.phaseId.setValue('');
    }
  }

  onEditionChange(): void {
    const editionId = this.filterForm.controls.editionId.value || undefined;
    if (!this.store.categoriesForEdition(editionId).some((category) => category.id === this.filterForm.controls.categoryId.value)) {
      this.filterForm.controls.categoryId.setValue('');
      this.filterForm.controls.phaseId.setValue('');
    }
  }

  onCategoryChange(): void {
    const categoryId = this.filterForm.controls.categoryId.value || undefined;
    if (!this.store.phasesForCategory(categoryId).some((phase) => phase.id === this.filterForm.controls.phaseId.value)) {
      this.filterForm.controls.phaseId.setValue('');
    }
  }

  async applyFilters(): Promise<void> {
    const query: PublicAgendaQuery = {
      period: this.store.query().period,
      competitionId: this.filterForm.controls.competitionId.value || undefined,
      editionId: this.filterForm.controls.editionId.value || undefined,
      sportId: this.filterForm.controls.sportId.value || undefined,
      categoryId: this.filterForm.controls.categoryId.value || undefined,
      phaseId: this.filterForm.controls.phaseId.value || undefined,
      state: this.validVenueState(this.filterForm.controls.state.value),
    };
    const waitingForLoad = await this.syncUrlWithPending(query);
    this.filtersOpen.set(false);
    if (!waitingForLoad) {
      this.announceAndFocus();
    }
  }

  async clearFilters(): Promise<void> {
    this.filterForm.reset({ competitionId: '', editionId: '', sportId: '', categoryId: '', phaseId: '', state: '' });
    const query = { period: this.store.query().period };
    const waitingForLoad = await this.syncUrlWithPending(query);
    this.filtersOpen.set(false);
    if (!waitingForLoad) {
      this.announceAndFocus();
    }
  }

  async retry(): Promise<void> {
    await this.store.loadAgenda(this.store.query());
  }

  hasCompetitionOption(id: string): boolean {
    return this.store.competitions().some((competition) => competition.id === id);
  }

  hasEditionOption(id: string): boolean {
    return this.store
      .editionsForCompetition(this.filterForm.controls.competitionId.value || undefined)
      .some((edition) => edition.id === id);
  }

  periodCountLabel(period: AgendaPeriod): string {
    if (this.store.query().period !== period || this.store.isLoading()) {
      return period === 'today' ? 'Agenda do dia' : 'Em breve';
    }
    const count = this.store.totalElements();
    return `${String(count).padStart(2, '0')} ${count === 1 ? 'jogo' : 'jogos'}`;
  }

  isFeatured(eventId: string): boolean {
    return this.store.nextEvent()?.id === eventId;
  }

  private announceAndFocus(): void {
    const count = this.store.totalElements();
    this.filterAnnouncement.set(
      `${count} ${count === 1 ? 'evento encontrado' : 'eventos encontrados'}.`,
    );
    queueMicrotask(() => this.agendaTitle?.nativeElement.focus());
  }

  private queryFromUrl(): PublicAgendaQuery {
    return this.queryFromParamMap(this.route?.snapshot.queryParamMap);
  }

  private queryFromParamMap(params: ParamMap | undefined): PublicAgendaQuery {
    const period = params?.get('period');
    return {
      period: period === 'upcoming' ? 'upcoming' : 'today',
      competitionId: params?.get('competitionId') || undefined,
      editionId: params?.get('editionId') || undefined,
      sportId: params?.get('sportId') || undefined,
      categoryId: params?.get('categoryId') || undefined,
      phaseId: params?.get('phaseId') || undefined,
      state: this.validVenueState(params?.get('state')),
    };
  }

  private async syncUrl(query: PublicAgendaQuery): Promise<void> {
    if (this.router && this.route) {
      await this.router.navigate([], {
        relativeTo: this.route,
        queryParams: {
          period: query.period,
          competitionId: query.competitionId ?? null,
          editionId: query.editionId ?? null,
          sportId: query.sportId ?? null,
          categoryId: query.categoryId ?? null,
          phaseId: query.phaseId ?? null,
          state: query.state ?? null,
        },
      });
      return;
    }
    await this.store.loadAgenda(query);
  }

  private async syncUrlWithPending(query: PublicAgendaQuery): Promise<boolean> {
    const routeDriven = Boolean(this.router && this.route);
    const queryKey = this.queryKey(query);
    if (routeDriven && queryKey === this.lastRequestedQueryKey && this.store.status() !== 'initial') {
      return false;
    }

    this.pendingLoad.set(routeDriven);
    await this.syncUrl(query);
    if (!routeDriven) {
      this.pendingLoad.set(false);
    }
    return routeDriven;
  }

  private scrollToFragment(fragment: string | null): void {
    if (fragment !== 'agenda') {
      return;
    }
    queueMicrotask(() => {
      const target = document.getElementById(fragment);
      if (target && typeof target.scrollIntoView === 'function') {
        target.scrollIntoView({ block: 'start' });
      }
    });
  }

  private queryKey(query: PublicAgendaQuery): string {
    return [query.period, query.competitionId ?? '', query.editionId ?? '', query.sportId ?? '', query.categoryId ?? '', query.phaseId ?? '', query.state ?? ''].join('|');
  }

  private validVenueState(value: string | null | undefined): PublicAgendaQuery['state'] {
    return value === 'LOCAL_A_DEFINIR' || value === 'LOCAL_DEFINIDO' ? value : undefined;
  }
}
