import { Injectable, inject } from '@angular/core';
import { Observable, defer, map } from 'rxjs';
import {
  ListPublishedSportsEventsRequestParams,
  SportsPublicService,
} from '../../../api/generated/api/sports-public.service';
import { PublicSportsEventPage } from '../../../api/generated/model/public-sports-event-page';
import { PublicSportsEventSummary } from '../../../api/generated/model/public-sports-event-summary';
import { SportsVenueState } from '../../../api/generated/model/sports-venue-state';

export type AgendaPeriod = 'today' | 'upcoming';
export interface PublicAgendaQuery {
  period: AgendaPeriod;
  competitionId?: string;
  editionId?: string;
  sportId?: string;
  categoryId?: string;
  phaseId?: string;
  state?: SportsVenueState;
}
export interface PublicAgendaFilterOption {
  id: string;
  label: string;
  parentId?: string;
}
export interface PublicAgendaEventViewModel {
  id: string;
  competitionId: string;
  editionId: string;
  sportId?: string;
  categoryId?: string;
  phaseId?: string;
  competitionName: string;
  editionName: string;
  title: string;
  eventName: string;
  scheduledAt: string;
  timestamp: number;
  dateKey: string;
  dateLabel: string;
  shortDateLabel: string;
  dayLabel: string;
  monthYearLabel: string;
  timeLabel: string;
  zoneLabel: string;
  formatLabel: string;
  venueLabel: string;
  contextLabel: string;
  detailHref: string;
}
export interface PublicAgendaSnapshot {
  events: readonly PublicAgendaEventViewModel[];
  totalElements: number;
  hasNext: boolean;
  competitions: readonly PublicAgendaFilterOption[];
  editions: readonly PublicAgendaFilterOption[];
  sports: readonly PublicAgendaFilterOption[];
  categories: readonly PublicAgendaFilterOption[];
  phases: readonly PublicAgendaFilterOption[];
  states: readonly PublicAgendaFilterOption[];
}

@Injectable({ providedIn: 'root' })
export class PublicAgendaApiAdapter {
  private readonly sportsPublicService = inject(SportsPublicService);
  fetchAgenda(request: ListPublishedSportsEventsRequestParams): Observable<PublicAgendaSnapshot> {
    return defer(() => this.sportsPublicService.listPublishedSportsEvents(request)).pipe(
      map((response) => this.adapt(response)),
    );
  }
  adapt(response: PublicSportsEventPage | null | undefined): PublicAgendaSnapshot {
    if (!response || !Array.isArray(response.items))
      throw new Error('CONTRATO_AGENDA_PUBLICA_INVALIDO: resposta ausente ou sem itens.');
    if (
      !Number.isInteger(response.totalElements) ||
      response.totalElements < 0 ||
      typeof response.hasNext !== 'boolean'
    )
      throw new Error('CONTRATO_AGENDA_PUBLICA_INVALIDO: paginação inválida.');
    const events = response.items
      .map((event) => this.adaptEvent(event))
      .sort((left, right) => left.timestamp - right.timestamp || left.id.localeCompare(right.id));
    const competitions = this.uniqueOptions(
      events.map((event) => ({ id: event.competitionId, label: event.competitionName })),
    );
    const editions = this.uniqueOptions(
      events.map((event) => ({
        id: event.editionId,
        label: event.editionName,
        parentId: event.competitionId,
      })),
    );
    const sports = this.uniqueOptions(
      response.items.flatMap((event) =>
        event.sportId && event.sportName ? [{ id: event.sportId, label: event.sportName }] : [],
      ),
    );
    const categories = this.uniqueOptions(
      response.items.flatMap((event) =>
        event.categoryId && event.categoryName
          ? [{ id: event.categoryId, label: event.categoryName, parentId: event.editionId }]
          : [],
      ),
    );
    const phases = this.uniqueOptions(
      response.items.flatMap((event) =>
        event.phaseId && event.phaseName
          ? [{ id: event.phaseId, label: event.phaseName, parentId: event.categoryId ?? undefined }]
          : [],
      ),
    );
    const states = this.uniqueOptions(
      response.items.map((event) => ({
        id: event.venueState,
        label: event.venueState === 'LOCAL_DEFINIDO' ? 'Local definido' : 'Local a definir',
      })),
    );
    return Object.freeze({
      events: Object.freeze(events),
      totalElements: response.totalElements,
      hasNext: response.hasNext,
      competitions,
      editions,
      sports,
      categories,
      phases,
      states,
    });
  }
  private adaptEvent(event: PublicSportsEventSummary): PublicAgendaEventViewModel {
    const required = [
      event.eventId,
      event.competitionId,
      event.editionId,
      event.competitionName,
      event.editionName,
      event.title,
      event.scheduledAt,
      event.zoneId,
      event.venueState,
    ];
    if (required.some((value) => typeof value !== 'string' || value.trim() === ''))
      throw new Error('CONTRATO_AGENDA_PUBLICA_INVALIDO: evento incompleto.');
    const date = new Date(event.scheduledAt);
    if (Number.isNaN(date.getTime()) || !Array.isArray(event.slots))
      throw new Error('CONTRATO_AGENDA_PUBLICA_INVALIDO: horário ou participantes inválidos.');
    const timeZone = this.validTimeZone(event.zoneId);
    const dateParts = this.dateParts(date, timeZone);
    const participants = [...event.slots]
      .sort((left, right) => left.slotNo - right.slotNo)
      .map((slot) => slot.displayLabel?.trim())
      .filter((label): label is string => Boolean(label));
    const eventName =
      participants.length === 2 ? `${participants[0]} × ${participants[1]}` : event.title.trim();
    const formatLabel = event.format === 'HEAD_TO_HEAD' ? 'Confronto' : 'Agenda básica';
    const contextParts = [
      formatLabel,
      event.sportName?.trim(),
      event.categoryName?.trim(),
      event.phaseName?.trim(),
      event.competitionName.trim(),
      event.editionName.trim(),
    ].filter((value): value is string => Boolean(value));
    return Object.freeze({
      id: event.eventId,
      competitionId: event.competitionId,
      editionId: event.editionId,
      sportId: event.sportId ?? undefined,
      categoryId: event.categoryId ?? undefined,
      phaseId: event.phaseId ?? undefined,
      competitionName: event.competitionName.trim(),
      editionName: event.editionName.trim(),
      title: event.title.trim(),
      eventName,
      scheduledAt: event.scheduledAt,
      timestamp: date.getTime(),
      dateKey: `${dateParts.year}-${dateParts.month}-${dateParts.day}`,
      dateLabel: this.format(date, timeZone, {
        weekday: 'long',
        day: '2-digit',
        month: 'long',
        year: 'numeric',
      }),
      shortDateLabel: this.format(date, timeZone, {
        day: '2-digit',
        month: 'short',
        year: 'numeric',
      }),
      dayLabel: dateParts.day,
      monthYearLabel: this.format(date, timeZone, { month: 'long', year: 'numeric' }),
      timeLabel: this.format(date, timeZone, { hour: '2-digit', minute: '2-digit' }),
      zoneLabel: this.zoneLabel(date, timeZone),
      formatLabel,
      venueLabel: this.venueLabel(event),
      contextLabel: contextParts.join(' · '),
      detailHref: `#agenda-event-${encodeURIComponent(event.eventId)}`,
    });
  }
  private venueLabel(event: PublicSportsEventSummary): string {
    if (event.venueState === 'LOCAL_A_DEFINIR') return 'Local a definir';
    if (event.venueState !== 'LOCAL_DEFINIDO' || !event.venueName?.trim())
      throw new Error('CONTRATO_AGENDA_PUBLICA_INVALIDO: local do evento inválido.');
    return event.venueSpaceName?.trim()
      ? `${event.venueName.trim()} · ${event.venueSpaceName.trim()}`
      : event.venueName.trim();
  }
  private uniqueOptions(
    options: readonly PublicAgendaFilterOption[],
  ): readonly PublicAgendaFilterOption[] {
    const byId = new Map<string, PublicAgendaFilterOption>();
    for (const option of options)
      if (!byId.has(option.id)) byId.set(option.id, Object.freeze({ ...option }));
    return Object.freeze(
      [...byId.values()].sort((left, right) => left.label.localeCompare(right.label, 'pt-BR')),
    );
  }
  private validTimeZone(timeZone: string): string {
    try {
      new Intl.DateTimeFormat('pt-BR', { timeZone }).format(0);
      return timeZone;
    } catch {
      throw new Error('CONTRATO_AGENDA_PUBLICA_INVALIDO: fuso horário inválido.');
    }
  }
  private format(date: Date, timeZone: string, options: Intl.DateTimeFormatOptions): string {
    return new Intl.DateTimeFormat('pt-BR', { ...options, timeZone }).format(date);
  }
  private dateParts(date: Date, timeZone: string): { year: string; month: string; day: string } {
    const parts = new Intl.DateTimeFormat('en-CA', {
      timeZone,
      year: 'numeric',
      month: '2-digit',
      day: '2-digit',
    }).formatToParts(date);
    const part = (type: Intl.DateTimeFormatPartTypes): string =>
      parts.find((candidate) => candidate.type === type)?.value ?? '00';
    return { year: part('year'), month: part('month'), day: part('day') };
  }
  private zoneLabel(date: Date, timeZone: string): string {
    const parts = new Intl.DateTimeFormat('pt-BR', {
      timeZone,
      hour: '2-digit',
      timeZoneName: 'short',
    }).formatToParts(date);
    return parts.find((part) => part.type === 'timeZoneName')?.value ?? timeZone;
  }
}
