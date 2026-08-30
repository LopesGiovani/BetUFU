import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { firstValueFrom, retry, timer } from 'rxjs';
import { AgendaPeriod, PublicAgendaApiAdapter, PublicAgendaEventViewModel, PublicAgendaFilterOption, PublicAgendaQuery } from './public-agenda-api.adapter';

type PublicAgendaStatus = 'initial' | 'loading' | 'populated' | 'empty' | 'stale' | 'error';
interface PublicAgendaState {
  status: PublicAgendaStatus; events: readonly PublicAgendaEventViewModel[]; previousEvents: readonly PublicAgendaEventViewModel[];
  competitions: readonly PublicAgendaFilterOption[]; editions: readonly PublicAgendaFilterOption[]; sports: readonly PublicAgendaFilterOption[];
  categories: readonly PublicAgendaFilterOption[]; phases: readonly PublicAgendaFilterOption[]; states: readonly PublicAgendaFilterOption[];
  totalElements: number; query: PublicAgendaQuery; publishedResponseReceived: boolean; errorMessage: string | null; errorCorrelationId: string | null; isRetrying: boolean;
}
export interface PublicAgendaEventGroup { dateKey: string; dateLabel: string; dayLabel: string; monthYearLabel: string; events: readonly PublicAgendaEventViewModel[]; }
const INITIAL_QUERY: PublicAgendaQuery = Object.freeze({ period: 'today' });
const INITIAL_STATE: PublicAgendaState = Object.freeze({ status: 'initial', events: Object.freeze([]), previousEvents: Object.freeze([]), competitions: Object.freeze([]), editions: Object.freeze([]), sports: Object.freeze([]), categories: Object.freeze([]), phases: Object.freeze([]), states: Object.freeze([]), totalElements: 0, query: INITIAL_QUERY, publishedResponseReceived: false, errorMessage: null, errorCorrelationId: null, isRetrying: false });

@Injectable({ providedIn: 'root' })
export class PublicAgendaStore {
  private readonly adapter = inject(PublicAgendaApiAdapter); private readonly state = signal<PublicAgendaState>(INITIAL_STATE); private readonly clock = signal(Date.now()); private requestSequence = 0;
  readonly status = computed(() => this.state().status); readonly query = computed(() => this.state().query); readonly events = computed(() => this.state().events);
  readonly displayEvents = computed(() => this.state().events.length > 0 ? this.state().events : this.state().previousEvents); readonly hasEvents = computed(() => this.displayEvents().length > 0);
  readonly competitions = computed(() => this.state().competitions); readonly editions = computed(() => this.state().editions); readonly sports = computed(() => this.state().sports); readonly categories = computed(() => this.state().categories); readonly phases = computed(() => this.state().phases); readonly states = computed(() => this.state().states);
  readonly totalElements = computed(() => this.state().totalElements); readonly errorMessage = computed(() => this.state().errorMessage); readonly errorCorrelationId = computed(() => this.state().errorCorrelationId); readonly isRetrying = computed(() => this.state().isRetrying); readonly publishedResponseReceived = computed(() => this.state().publishedResponseReceived);
  readonly isLoading = computed(() => this.status() === 'loading'); readonly isPopulated = computed(() => this.status() === 'populated'); readonly isEmpty = computed(() => this.status() === 'empty'); readonly isStale = computed(() => this.status() === 'stale'); readonly isError = computed(() => this.status() === 'error');
  readonly hasActiveFilters = computed(() => Boolean(this.query().competitionId || this.query().editionId || this.query().sportId || this.query().categoryId || this.query().phaseId || this.query().state));
  readonly upcomingEvents = computed(() => { const now = this.clock(); return Object.freeze(this.displayEvents().filter((event) => event.timestamp >= now)); }); readonly nextEvent = computed(() => this.upcomingEvents()[0] ?? null);
  readonly eventGroups = computed<readonly PublicAgendaEventGroup[]>(() => { const groups = new Map<string, PublicAgendaEventViewModel[]>(); for (const event of this.displayEvents()) groups.set(event.dateKey, [...(groups.get(event.dateKey) ?? []), event]); return Object.freeze([...groups.entries()].map(([dateKey, events]) => Object.freeze({ dateKey, dateLabel: events[0].dateLabel, dayLabel: events[0].dayLabel, monthYearLabel: events[0].monthYearLabel, events: Object.freeze(events) }))); });
  constructor() { effect((onCleanup) => { const boundary = this.upcomingEvents()[0]?.timestamp; if (boundary === undefined) return; const timerId = setTimeout(() => this.clock.set(Date.now()), Math.min(Math.max(boundary - Date.now() + 1, 1), 2_147_483_647)); onCleanup(() => clearTimeout(timerId)); }); }
  editionsForCompetition(id: string | undefined): readonly PublicAgendaFilterOption[] { return id ? this.editions().filter((item) => item.parentId === id) : this.editions(); }
  categoriesForEdition(id: string | undefined): readonly PublicAgendaFilterOption[] { return id ? this.categories().filter((item) => item.parentId === id) : this.categories(); }
  phasesForCategory(id: string | undefined): readonly PublicAgendaFilterOption[] { return id ? this.phases().filter((item) => item.parentId === id) : this.phases(); }

  async loadAgenda(query: PublicAgendaQuery = this.query()): Promise<void> {
    const requestId = ++this.requestSequence; const current = this.state(); const activeEvents = current.events.length > 0 ? current.events : current.previousEvents;
    this.state.set({ ...current, status: 'loading', previousEvents: activeEvents, query: Object.freeze({ ...query }), errorMessage: null, errorCorrelationId: null, isRetrying: false });
    try {
      const snapshot = await firstValueFrom(this.adapter.fetchAgenda(this.requestFor(query)).pipe(retry({ count: 2, delay: (_error, retryCount) => { if (requestId === this.requestSequence) this.state.update((state) => ({ ...state, isRetrying: true })); return timer(retryCount === 1 ? 250 : 500); } })));
      if (requestId !== this.requestSequence) return;
      let filterOptions = {
        competitions: snapshot.competitions,
        editions: snapshot.editions,
        sports: snapshot.sports,
        categories: snapshot.categories,
        phases: snapshot.phases,
        states: snapshot.states,
      };
      if (snapshot.hasNext || this.hasQueryFilter(query)) filterOptions = await this.loadAllFilterOptions(query, requestId, snapshot);
      if (requestId !== this.requestSequence) return;
      this.state.set({ status: snapshot.events.length === 0 ? 'empty' : 'populated', events: snapshot.events, previousEvents: snapshot.events, competitions: filterOptions.competitions.length ? filterOptions.competitions : current.competitions, editions: filterOptions.editions.length ? filterOptions.editions : current.editions, sports: filterOptions.sports.length ? filterOptions.sports : current.sports, categories: filterOptions.categories.length ? filterOptions.categories : current.categories, phases: filterOptions.phases.length ? filterOptions.phases : current.phases, states: filterOptions.states.length ? filterOptions.states : current.states, totalElements: snapshot.totalElements, query: Object.freeze({ ...query }), publishedResponseReceived: true, errorMessage: null, errorCorrelationId: null, isRetrying: false });
    } catch (error: unknown) {
      if (requestId !== this.requestSequence) return; const sanitized = this.sanitizeError(error); this.state.set({ ...current, status: activeEvents.length ? 'stale' : 'error', events: Object.freeze([]), previousEvents: activeEvents, query: Object.freeze({ ...query }), errorMessage: sanitized.message, errorCorrelationId: sanitized.correlationId, isRetrying: false });
    }
  }

  private async loadAllFilterOptions(query: PublicAgendaQuery, requestId: number, first: { competitions: readonly PublicAgendaFilterOption[]; editions: readonly PublicAgendaFilterOption[]; sports: readonly PublicAgendaFilterOption[]; categories: readonly PublicAgendaFilterOption[]; phases: readonly PublicAgendaFilterOption[]; states: readonly PublicAgendaFilterOption[]; hasNext: boolean; }): Promise<typeof first> {
    const baseQuery: PublicAgendaQuery = { period: query.period }; const maps = { competitions: new Map(first.competitions.map((item) => [item.id, item])), editions: new Map(first.editions.map((item) => [item.id, item])), sports: new Map(first.sports.map((item) => [item.id, item])), categories: new Map(first.categories.map((item) => [item.id, item])), phases: new Map(first.phases.map((item) => [item.id, item])), states: new Map(first.states.map((item) => [item.id, item])) };
    let page = this.hasQueryFilter(query) ? 0 : 1; let hasNext = this.hasQueryFilter(query) || first.hasNext;
    while (hasNext && requestId === this.requestSequence) {
      const snapshot = await firstValueFrom(this.adapter.fetchAgenda(this.requestFor(baseQuery, page)));
      for (const option of snapshot.competitions) maps.competitions.set(option.id, option);
      for (const option of snapshot.editions) maps.editions.set(option.id, option);
      for (const option of snapshot.sports) maps.sports.set(option.id, option);
      for (const option of snapshot.categories) maps.categories.set(option.id, option);
      for (const option of snapshot.phases) maps.phases.set(option.id, option);
      for (const option of snapshot.states) maps.states.set(option.id, option);
      hasNext = snapshot.hasNext;
      page += 1;
    }
    return { competitions: Object.freeze([...maps.competitions.values()]), editions: Object.freeze([...maps.editions.values()]), sports: Object.freeze([...maps.sports.values()]), categories: Object.freeze([...maps.categories.values()]), phases: Object.freeze([...maps.phases.values()]), states: Object.freeze([...maps.states.values()]), hasNext };
  }

  private requestFor(query: PublicAgendaQuery, page = 0): { competitionId?: string; editionId?: string; sportId?: string; categoryId?: string; phaseId?: string; state?: PublicAgendaQuery['state']; from: string; to?: string; page: number; size: number } {
    const now = new Date(); const result = { competitionId: query.competitionId, editionId: query.editionId, sportId: query.sportId, categoryId: query.categoryId, phaseId: query.phaseId, state: query.state, from: now.toISOString(), page, size: 50 };
    if (query.period === 'today') { const start = new Date(now); start.setHours(0, 0, 0, 0); const end = new Date(now); end.setHours(23, 59, 59, 999); return { ...result, from: start.toISOString(), to: end.toISOString() }; } return result;
  }
  private hasQueryFilter(query: PublicAgendaQuery): boolean { return Boolean(query.competitionId || query.editionId || query.sportId || query.categoryId || query.phaseId || query.state); }
  private sanitizeError(error: unknown): { message: string; correlationId: string } { let correlationId = 'N/D'; if (error instanceof HttpErrorResponse && error.error && typeof error.error === 'object') { const candidate = (error.error as Record<string, unknown>)['correlationId']; if (typeof candidate === 'string' && candidate.trim()) correlationId = candidate.trim(); } if (error instanceof Error && error.message.startsWith('CONTRATO_')) return { message: 'A agenda recebida não corresponde ao contrato público esperado. Tente novamente.', correlationId }; return { message: correlationId === 'N/D' ? 'Não foi possível carregar estes dados. Verifique a conexão e tente novamente.' : `Não foi possível carregar estes dados. Verifique a conexão e tente novamente. Código de referência: ${correlationId}.`, correlationId }; }
}
export function agendaPeriodLabel(period: AgendaPeriod): string { return period === 'today' ? 'Jogos de hoje' : 'Próximos eventos'; }
