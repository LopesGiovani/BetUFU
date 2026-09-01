import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { SportsEventDetail } from '../../../../api/generated/model/sports-event-detail';
import { SportsResultVersion } from '../../../../api/generated/model/sports-result-version';
import {
  SportsEventCreatePayload,
  SportsEventResultApiAdapter,
  SportsEventUpdatePayload,
  SportsResultScoreItemRequest,
} from './sports-event-result-api.adapter';

export type SportsEventResultStatus =
  'initial' | 'loading' | 'ready' | 'empty' | 'denied' | 'error';
export type SportsEventResultSnapshot = SportsEventDetail | SportsResultVersion;

export interface EventResultState {
  status: SportsEventResultStatus;
  selectedEditionId: string | null;
  events: readonly SportsEventDetail[];
  selectedEventId: string | null;
  selectedEvent: SportsEventDetail | null;
  resultVersions: readonly SportsResultVersion[];
  preview: SportsEventResultSnapshot | null;
  errorMessage: string | null;
  conflictMessage: string | null;
  feedbackMessage: string | null;
  isMutating: boolean;
}

const INITIAL_STATE: EventResultState = {
  status: 'initial',
  selectedEditionId: null,
  events: Object.freeze([]),
  selectedEventId: null,
  selectedEvent: null,
  resultVersions: Object.freeze([]),
  preview: null,
  errorMessage: null,
  conflictMessage: null,
  feedbackMessage: null,
  isMutating: false,
};

@Injectable({ providedIn: 'root' })
export class SportsEventResultStore {
  private readonly adapter = inject(SportsEventResultApiAdapter);
  private readonly state = signal<EventResultState>(INITIAL_STATE);
  private requestSequence = 0;

  readonly status = computed(() => this.state().status);
  readonly selectedEditionId = computed(() => this.state().selectedEditionId);
  readonly events = computed(() => this.state().events);
  readonly selectedEventId = computed(() => this.state().selectedEventId);
  readonly selectedEvent = computed(() => this.state().selectedEvent);
  readonly resultVersions = computed(() => this.state().resultVersions);
  readonly confirmedVersion = computed(() => {
    const versions = this.state().resultVersions;
    const event = this.state().selectedEvent;
    if (event?.confirmedVersionId) {
      const matching = versions.find((v) => v.versionId === event.confirmedVersionId);
      if (matching) return matching;
    }
    return versions.find((v) => v.status === 'CONFIRMADO' || v.status === 'CORRIGIDO') ?? null;
  });
  readonly preview = computed(() => this.state().preview);
  readonly errorMessage = computed(() => this.state().errorMessage);
  readonly conflictMessage = computed(() => this.state().conflictMessage);
  readonly feedbackMessage = computed(() => this.state().feedbackMessage);
  readonly isMutating = computed(() => this.state().isMutating);

  readonly isLoading = computed(() => this.status() === 'loading');
  readonly isReady = computed(() => this.status() === 'ready');
  readonly isEmpty = computed(() => this.status() === 'empty');
  readonly isDenied = computed(() => this.status() === 'denied');
  readonly isError = computed(() => this.status() === 'error');
  readonly hasEvents = computed(() => this.events().length > 0);
  readonly hasSelectedEvent = computed(() => this.selectedEvent() !== null);

  async loadEdition(editionId: string | null, preferredEventId?: string | null): Promise<void> {
    const requestId = ++this.requestSequence;
    if (!editionId) {
      this.state.set({ ...INITIAL_STATE, status: 'empty' });
      return;
    }

    this.state.update((current) => ({
      ...current,
      status: 'loading',
      selectedEditionId: editionId,
      errorMessage: null,
      conflictMessage: null,
      feedbackMessage: null,
      preview: null,
    }));

    try {
      const events = await firstValueFrom(this.adapter.fetchEvents(editionId));
      if (requestId !== this.requestSequence) return;

      if (events.length === 0) {
        this.state.set({
          ...INITIAL_STATE,
          status: 'empty',
          selectedEditionId: editionId,
          events: Object.freeze([]),
        });
        return;
      }

      const targetEventId =
        preferredEventId && events.some((e) => e.eventId === preferredEventId)
          ? preferredEventId
          : events[0].eventId;

      let detail: SportsEventDetail | null = null;
      let versions: readonly SportsResultVersion[] = Object.freeze([]);

      try {
        detail = await firstValueFrom(this.adapter.fetchEventDetail(targetEventId));
        versions = await firstValueFrom(this.adapter.fetchResultVersions(targetEventId));
      } catch (innerError) {
        if (this.adapter.isUnauthorized(innerError)) throw innerError;
        // If event detail fetch failed but not auth, fallback to event from list
        detail = events.find((e) => e.eventId === targetEventId) ?? null;
      }

      if (requestId !== this.requestSequence) return;

      this.state.set({
        ...INITIAL_STATE,
        status: 'ready',
        selectedEditionId: editionId,
        events,
        selectedEventId: targetEventId,
        selectedEvent: detail,
        resultVersions: versions,
      });
    } catch (error: unknown) {
      if (requestId !== this.requestSequence) return;
      if (this.adapter.isUnauthorized(error)) {
        this.clearPrivateState('denied');
      } else {
        this.state.set({
          ...this.state(),
          status: 'error',
          errorMessage:
            'Os eventos e resultados deste escopo não estão disponíveis agora. Tente novamente antes de registrar, confirmar ou corrigir um resultado.',
        });
      }
    }
  }

  async selectEvent(eventId: string): Promise<void> {
    const current = this.state();
    if (!eventId || current.selectedEventId === eventId) return;

    this.state.update((s) => ({
      ...s,
      selectedEventId: eventId,
      preview: null,
      conflictMessage: null,
      feedbackMessage: null,
    }));

    try {
      const [detail, versions] = await Promise.all([
        firstValueFrom(this.adapter.fetchEventDetail(eventId)),
        firstValueFrom(this.adapter.fetchResultVersions(eventId)).catch((err) => {
          if (this.adapter.isUnauthorized(err)) throw err;
          return Object.freeze([]) as readonly SportsResultVersion[];
        }),
      ]);

      this.state.update((s) => ({
        ...s,
        selectedEvent: detail,
        resultVersions: versions,
      }));
    } catch (error: unknown) {
      if (this.adapter.isUnauthorized(error)) {
        this.clearPrivateState('denied');
      } else {
        this.state.update((s) => ({
          ...s,
          errorMessage:
            'Não foi possível carregar os detalhes do evento selecionado. Tente novamente.',
        }));
      }
    }
  }

  previewSnapshot(
    snapshot: SportsEventResultSnapshot | null = this.selectedEvent() ?? this.confirmedVersion(),
  ): void {
    this.state.update((s) => ({ ...s, preview: snapshot }));
  }

  closePreview(): void {
    this.state.update((s) => ({ ...s, preview: null }));
  }

  clearMessages(): void {
    this.state.update((s) => ({ ...s, conflictMessage: null, feedbackMessage: null }));
  }

  async reloadAfterConflict(): Promise<void> {
    this.clearMessages();
    await this.reloadCurrentEvent();
  }

  async reloadCurrentEvent(): Promise<void> {
    const editionId = this.selectedEditionId();
    const eventId = this.selectedEventId();
    if (!editionId) return;
    await this.loadEdition(editionId, eventId);
  }

  async createEvent(editionId: string, payload: SportsEventCreatePayload): Promise<boolean> {
    return this.mutate(async () => {
      const created = await firstValueFrom(this.adapter.createEvent(editionId, payload));
      await this.loadEdition(editionId, created.eventId);
    }, 'Evento esportivo cadastrado com sucesso.');
  }

  async updateEventDraft(payload: SportsEventUpdatePayload): Promise<boolean> {
    const event = this.selectedEvent();
    const editionId = this.selectedEditionId();
    if (!event || !editionId) return false;

    return this.mutate(async () => {
      await firstValueFrom(
        this.adapter.updateEventDraft(event.eventId, event.lockVersion, payload),
      );
      await this.loadEdition(editionId, event.eventId);
    }, 'Rascunho de evento salvo.');
  }

  async publishEvent(): Promise<boolean> {
    const event = this.selectedEvent();
    const editionId = this.selectedEditionId();
    if (!event || !editionId) return false;

    return this.mutate(async () => {
      await firstValueFrom(this.adapter.publishEvent(event.eventId, event.lockVersion));
      await this.loadEdition(editionId, event.eventId);
    }, 'Evento publicado na agenda esportiva.');
  }

  async suspendEvent(reason?: string | null): Promise<boolean> {
    const event = this.selectedEvent();
    const editionId = this.selectedEditionId();
    if (!event || !editionId) return false;

    return this.mutate(async () => {
      await firstValueFrom(this.adapter.suspendEvent(event.eventId, event.lockVersion, reason));
      await this.loadEdition(editionId, event.eventId);
    }, 'Evento suspenso temporariamente.');
  }

  async postponeEvent(
    scheduledAt: string,
    zoneId: string,
    reason?: string | null,
  ): Promise<boolean> {
    const event = this.selectedEvent();
    const editionId = this.selectedEditionId();
    if (!event || !editionId) return false;

    return this.mutate(async () => {
      await firstValueFrom(
        this.adapter.postponeEvent(event.eventId, event.lockVersion, scheduledAt, zoneId, reason),
      );
      await this.loadEdition(editionId, event.eventId);
    }, 'Evento adiado e reagendado.');
  }

  async cancelEvent(reason: string): Promise<boolean> {
    const event = this.selectedEvent();
    const editionId = this.selectedEditionId();
    if (!event || !editionId || !reason.trim()) return false;

    return this.mutate(async () => {
      await firstValueFrom(
        this.adapter.cancelEvent(event.eventId, event.lockVersion, reason.trim()),
      );
      await this.loadEdition(editionId, event.eventId);
    }, 'Evento cancelado de forma irreversível.');
  }

  async informResult(
    sourceReference: string,
    scores: readonly SportsResultScoreItemRequest[],
  ): Promise<boolean> {
    const event = this.selectedEvent();
    const editionId = this.selectedEditionId();
    if (!event || !editionId || !sourceReference.trim()) return false;

    return this.mutate(async () => {
      await firstValueFrom(
        this.adapter.informResult(event.eventId, event.lockVersion, sourceReference.trim(), scores),
      );
      await this.loadEdition(editionId, event.eventId);
    }, 'Rascunho de resultado registrado (INFORMADO).');
  }

  async reviewResult(
    sourceReference: string,
    scores: readonly SportsResultScoreItemRequest[],
  ): Promise<boolean> {
    const event = this.selectedEvent();
    const editionId = this.selectedEditionId();
    if (!event || !editionId || !sourceReference.trim()) return false;

    return this.mutate(async () => {
      await firstValueFrom(
        this.adapter.reviewResult(event.eventId, event.lockVersion, sourceReference.trim(), scores),
      );
      await this.loadEdition(editionId, event.eventId);
    }, 'Resultado enviado para revisão (AGUARDANDO_REVISAO).');
  }

  async confirmResult(
    sourceReference: string,
    scores: readonly SportsResultScoreItemRequest[],
  ): Promise<boolean> {
    const event = this.selectedEvent();
    const editionId = this.selectedEditionId();
    if (!event || !editionId || !sourceReference.trim()) return false;

    return this.mutate(async () => {
      await firstValueFrom(
        this.adapter.confirmResult(
          event.eventId,
          event.lockVersion,
          sourceReference.trim(),
          scores,
        ),
      );
      await this.loadEdition(editionId, event.eventId);
    }, 'Resultado confirmado com fonte oficial (CONFIRMADO).');
  }

  async correctResult(
    reason: string,
    sourceReference: string,
    scores: readonly SportsResultScoreItemRequest[],
  ): Promise<boolean> {
    const event = this.selectedEvent();
    const editionId = this.selectedEditionId();
    if (!event || !editionId || !reason.trim() || !sourceReference.trim()) return false;

    return this.mutate(async () => {
      await firstValueFrom(
        this.adapter.correctResult(
          event.eventId,
          event.lockVersion,
          reason.trim(),
          sourceReference.trim(),
          scores,
        ),
      );
      await this.loadEdition(editionId, event.eventId);
    }, 'Correção de resultado publicada como nova versão auditada (CORRIGIDO).');
  }

  private async mutate(action: () => Promise<void>, successMessage: string): Promise<boolean> {
    this.state.update((s) => ({
      ...s,
      isMutating: true,
      errorMessage: null,
      conflictMessage: null,
      feedbackMessage: null,
    }));

    try {
      await action();
      this.state.update((s) => ({ ...s, feedbackMessage: successMessage, isMutating: false }));
      return true;
    } catch (error: unknown) {
      if (this.adapter.isUnauthorized(error)) {
        this.clearPrivateState('denied');
      } else if (this.adapter.isConflict(error)) {
        this.state.update((s) => ({
          ...s,
          isMutating: false,
          conflictMessage:
            'Este registro foi alterado por outra pessoa. Recarregue para comparar a versão atual antes de tentar novamente.',
        }));
      } else {
        this.state.update((s) => ({
          ...s,
          isMutating: false,
          errorMessage: 'Não foi possível salvar esta alteração agora. Tente novamente.',
        }));
      }
      return false;
    }
  }

  private clearPrivateState(status: SportsEventResultStatus): void {
    this.state.set({
      ...INITIAL_STATE,
      status,
      errorMessage:
        status === 'denied'
          ? 'Seu acesso a estes eventos e resultados foi encerrado. Nenhum rascunho privado foi mantido.'
          : null,
    });
  }
}
