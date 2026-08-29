import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, effect, inject, signal } from '@angular/core';
import { firstValueFrom, retry, timer } from 'rxjs';
import {
  AgendaPeriod,
  PublicAgendaApiAdapter,
  PublicAgendaEventViewModel,
  PublicAgendaFilterOption,
  PublicAgendaQuery,
} from './public-agenda-api.adapter';

type PublicAgendaStatus = 'initial' | 'loading' | 'populated' | 'empty' | 'stale' | 'error';

interface PublicAgendaState {
  status: PublicAgendaStatus;
  events: readonly PublicAgendaEventViewModel[];
  previousEvents: readonly PublicAgendaEventViewModel[];
  competitions: readonly PublicAgendaFilterOption[];
  editions: readonly PublicAgendaFilterOption[];
  totalElements: number;
  query: PublicAgendaQuery;
  publishedResponseReceived: boolean;
  errorMessage: string | null;
  errorCorrelationId: string | null;
  isRetrying: boolean;
}

export interface PublicAgendaEventGroup {
  dateKey: string;
  dateLabel: string;
  dayLabel: string;
  monthYearLabel: string;
  events: readonly PublicAgendaEventViewModel[];
}

const INITIAL_QUERY: PublicAgendaQuery = Object.freeze({ period: 'today' });
const INITIAL_STATE: PublicAgendaState = Object.freeze({
  status: 'initial',
  events: Object.freeze([]),
  previousEvents: Object.freeze([]),
  competitions: Object.freeze([]),
  editions: Object.freeze([]),
  totalElements: 0,
  query: INITIAL_QUERY,
  publishedResponseReceived: false,
  errorMessage: null,
  errorCorrelationId: null,
  isRetrying: false,
});

@Injectable({ providedIn: 'root' })
export class PublicAgendaStore {
  private readonly adapter = inject(PublicAgendaApiAdapter);
  private readonly state = signal<PublicAgendaState>(INITIAL_STATE);
  private readonly clock = signal(Date.now());
  private requestSequence = 0;

  readonly status = computed(() => this.state().status);
  readonly query = computed(() => this.state().query);
  readonly events = computed(() => this.state().events);
  readonly displayEvents = computed(() =>
    this.state().events.length > 0 ? this.state().events : this.state().previousEvents,
  );
  readonly hasEvents = computed(() => this.displayEvents().length > 0);
  readonly competitions = computed(() => this.state().competitions);
  readonly editions = computed(() => this.state().editions);
  readonly totalElements = computed(() => this.state().totalElements);
  readonly errorMessage = computed(() => this.state().errorMessage);
  readonly errorCorrelationId = computed(() => this.state().errorCorrelationId);
  readonly isRetrying = computed(() => this.state().isRetrying);
  readonly publishedResponseReceived = computed(() => this.state().publishedResponseReceived);

  readonly isLoading = computed(() => this.state().status === 'loading');
  readonly isPopulated = computed(() => this.state().status === 'populated');
  readonly isEmpty = computed(() => this.state().status === 'empty');
  readonly isStale = computed(() => this.state().status === 'stale');
  readonly isError = computed(() => this.state().status === 'error');
  readonly hasActiveFilters = computed(() =>
    Boolean(this.state().query.competitionId || this.state().query.editionId),
  );

  readonly upcomingEvents = computed(() => {
    const now = this.clock();
    return Object.freeze(this.displayEvents().filter((event) => event.timestamp >= now));
  });
  readonly nextEvent = computed(() => this.upcomingEvents()[0] ?? null);

  readonly eventGroups = computed<readonly PublicAgendaEventGroup[]>(() => {
    const groups = new Map<string, PublicAgendaEventViewModel[]>();
    for (const event of this.displayEvents()) {
      const current = groups.get(event.dateKey) ?? [];
      current.push(event);
      groups.set(event.dateKey, current);
    }
    return Object.freeze([...groups.entries()].map(([dateKey, events]) => Object.freeze({
      dateKey,
      dateLabel: events[0].dateLabel,
      dayLabel: events[0].dayLabel,
      monthYearLabel: events[0].monthYearLabel,
      events: Object.freeze(events),
    })));
  });

  constructor() {
    effect((onCleanup) => {
      const nextBoundary = this.upcomingEvents()[0]?.timestamp;
      if (nextBoundary === undefined) {
        return;
      }

      const delay = Math.min(Math.max(nextBoundary - Date.now() + 1, 1), 2_147_483_647);
      const timerId = setTimeout(() => this.clock.set(Date.now()), delay);
      onCleanup(() => clearTimeout(timerId));
    });
  }

  editionsForCompetition(competitionId: string | undefined): readonly PublicAgendaFilterOption[] {
    if (!competitionId) {
      return this.editions();
    }
    return this.editions().filter((edition) => edition.parentId === competitionId);
  }

  async loadAgenda(query: PublicAgendaQuery = this.state().query): Promise<void> {
    const requestId = ++this.requestSequence;
    const current = this.state();
    const activeEvents = current.events.length > 0 ? current.events : current.previousEvents;

    this.state.set({
      ...current,
      status: 'loading',
      previousEvents: activeEvents,
      query: Object.freeze({ ...query }),
      errorMessage: null,
      errorCorrelationId: null,
      isRetrying: false,
    });

    try {
      const request = this.requestFor(query);
      const snapshot = await firstValueFrom(
        this.adapter.fetchAgenda(request).pipe(
          retry({
            count: 2,
            delay: (_error, retryCount) => {
              if (requestId === this.requestSequence) {
                this.state.update((state) => ({ ...state, isRetrying: true }));
              }
              return timer(retryCount === 1 ? 250 : 500);
            },
          }),
        ),
      );
      if (requestId !== this.requestSequence) {
        return;
      }

      let competitions = snapshot.competitions;
      let editions = snapshot.editions;
      if (snapshot.hasNext || query.competitionId || query.editionId) {
        const options = await this.loadAllFilterOptions(query, requestId, snapshot);
        if (requestId !== this.requestSequence) {
          return;
        }
        competitions = options.competitions;
        editions = options.editions;
      }

      this.state.set({
        status: snapshot.events.length === 0 ? 'empty' : 'populated',
        events: snapshot.events,
        previousEvents: snapshot.events,
        competitions: competitions.length > 0 ? competitions : current.competitions,
        editions: editions.length > 0 ? editions : current.editions,
        totalElements: snapshot.totalElements,
        query: Object.freeze({ ...query }),
        publishedResponseReceived: true,
        errorMessage: null,
        errorCorrelationId: null,
        isRetrying: false,
      });
    } catch (error: unknown) {
      if (requestId !== this.requestSequence) {
        return;
      }
      const sanitized = this.sanitizeError(error);
      const hasSnapshot = activeEvents.length > 0;
      this.state.set({
        ...current,
        status: hasSnapshot ? 'stale' : 'error',
        events: Object.freeze([]),
        previousEvents: activeEvents,
        query: Object.freeze({ ...query }),
        errorMessage: sanitized.message,
        errorCorrelationId: sanitized.correlationId,
        isRetrying: false,
      });
    }
  }

  private async loadAllFilterOptions(
    query: PublicAgendaQuery,
    requestId: number,
    firstSnapshot: {
      competitions: readonly PublicAgendaFilterOption[];
      editions: readonly PublicAgendaFilterOption[];
      hasNext: boolean;
    },
  ): Promise<{ competitions: readonly PublicAgendaFilterOption[]; editions: readonly PublicAgendaFilterOption[] }> {
    const baseQuery: PublicAgendaQuery = { period: query.period };
    const competitions = new Map(firstSnapshot.competitions.map((option) => [option.id, option]));
    const editions = new Map(firstSnapshot.editions.map((option) => [option.id, option]));
    const hasQueryFilters = Boolean(query.competitionId || query.editionId);
    let page = hasQueryFilters ? 0 : 1;
    let hasNext = hasQueryFilters || firstSnapshot.hasNext;

    while (hasNext && requestId === this.requestSequence) {
      const snapshot = await firstValueFrom(this.adapter.fetchAgenda(
        this.requestFor(baseQuery, page),
      ));
      for (const option of snapshot.competitions) competitions.set(option.id, option);
      for (const option of snapshot.editions) editions.set(option.id, option);
      hasNext = snapshot.hasNext;
      page += 1;
    }

    return {
      competitions: Object.freeze([...competitions.values()]),
      editions: Object.freeze([...editions.values()]),
    };
  }

  private requestFor(query: PublicAgendaQuery, page = 0): {
    competitionId?: string;
    editionId?: string;
    from: string;
    to?: string;
    page: number;
    size: number;
  } {
    const now = new Date();
    if (query.period === 'today') {
      const start = new Date(now);
      start.setHours(0, 0, 0, 0);
      const end = new Date(now);
      end.setHours(23, 59, 59, 999);
      return {
        competitionId: query.competitionId,
        editionId: query.editionId,
        from: start.toISOString(),
        to: end.toISOString(),
        page,
        size: 50,
      };
    }

    return {
      competitionId: query.competitionId,
      editionId: query.editionId,
      from: now.toISOString(),
      page,
      size: 50,
    };
  }

  private sanitizeError(error: unknown): { message: string; correlationId: string } {
    let correlationId = 'N/D';
    if (error instanceof HttpErrorResponse && error.error && typeof error.error === 'object') {
      const candidate = (error.error as Record<string, unknown>)['correlationId'];
      if (typeof candidate === 'string' && candidate.trim()) {
        correlationId = candidate.trim();
      }
    }

    if (error instanceof Error && error.message.startsWith('CONTRATO_')) {
      return {
        message: 'A agenda recebida não corresponde ao contrato público esperado. Tente novamente.',
        correlationId,
      };
    }

    return {
      message: correlationId === 'N/D'
        ? 'Não foi possível carregar estes dados. Verifique a conexão e tente novamente.'
        : `Não foi possível carregar estes dados. Verifique a conexão e tente novamente. Código de referência: ${correlationId}.`,
      correlationId,
    };
  }
}

export function agendaPeriodLabel(period: AgendaPeriod): string {
  return period === 'today' ? 'Jogos de hoje' : 'Próximos eventos';
}
