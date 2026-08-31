import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom, retry, timer } from 'rxjs';
import { SportsStandingsSnapshot } from '../../../../api/generated/model/sports-standings-snapshot';
import { SportsStandingsRow } from '../../../../api/generated/model/sports-standings-row';
import { SportsStandingsApiAdapter, SportsStandingsUpdatePayload } from './sports-standings-api.adapter';

type SportsStandingsStatus = 'initial' | 'loading' | 'populated' | 'not-found' | 'denied' | 'error';

interface SportsStandingsState {
  status: SportsStandingsStatus;
  standingsId: string | null;
  data: SportsStandingsSnapshot | null;
  errorMessage: string | null;
  conflictMessage: string | null;
  feedbackMessage: string | null;
  isMutating: boolean;
}

const INITIAL: SportsStandingsState = {
  status: 'initial',
  standingsId: null,
  data: null,
  errorMessage: null,
  conflictMessage: null,
  feedbackMessage: null,
  isMutating: false,
};

@Injectable({ providedIn: 'root' })
export class SportsStandingsStore {
  private readonly adapter = inject(SportsStandingsApiAdapter);
  private readonly state = signal<SportsStandingsState>(INITIAL);
  private requestSequence = 0;

  readonly status = computed(() => this.state().status);
  readonly data = computed(() => this.state().data);
  readonly errorMessage = computed(() => this.state().errorMessage);
  readonly conflictMessage = computed(() => this.state().conflictMessage);
  readonly feedbackMessage = computed(() => this.state().feedbackMessage);
  readonly isMutating = computed(() => this.state().isMutating);
  readonly isLoading = computed(() => this.status() === 'loading');
  readonly isNotFound = computed(() => this.status() === 'not-found');
  readonly isDenied = computed(() => this.status() === 'denied');
  readonly isError = computed(() => this.status() === 'error');

  async load(standingsId: string): Promise<void> {
    const id = standingsId.trim();
    const requestId = ++this.requestSequence;
    this.state.set({ ...INITIAL, status: 'loading', standingsId: id || null });

    try {
      const data = await firstValueFrom(
        this.adapter.fetch(id).pipe(
          retry({
            count: 2,
            delay: (error: unknown, attempt) => {
              if (error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403 || error.status === 404)) {
                throw error;
              }
              return timer(attempt === 1 ? 250 : 500);
            },
          }),
        ),
      );

      if (requestId !== this.requestSequence) return;
      if (!data || data.standingsId !== id || !Array.isArray(data.rows) || !Array.isArray(data.columns)) {
        throw new Error('CONTRATO_STANDINGS_INVALIDO');
      }

      this.state.set({ ...INITIAL, status: 'populated', standingsId: id, data });
    } catch (error: unknown) {
      if (requestId !== this.requestSequence) return;
      const status = this.adapter.isUnauthorized(error) ? 'denied' : this.adapter.isNotFound(error) ? 'not-found' : 'error';
      this.state.set({
        ...INITIAL,
        status,
        standingsId: id || null,
        errorMessage:
          status === 'denied'
            ? 'Você não tem acesso a esta classificação operacional.'
            : status === 'not-found'
              ? 'Classificação não encontrada neste escopo.'
              : 'Não foi possível carregar chaveamento e classificação. Tente carregar chaveamento e classificação.',
      });
    }
  }

  async retry(): Promise<void> {
    const id = this.state().standingsId;
    if (id) await this.load(id);
  }

  async update(payload: SportsStandingsUpdatePayload): Promise<boolean> {
    const current = this.state();
    if (!current.data) return false;
    const standings = current.data;
    return this.mutate(async () => {
      const data = await firstValueFrom(this.adapter.update(standings.standingsId, standings.lockVersion, payload));
      this.state.update((state) => ({ ...state, data, conflictMessage: null }));
    }, 'Classificação salva como rascunho.');
  }

  async publish(sourceReference: string): Promise<boolean> {
    const current = this.state();
    if (!current.data || !sourceReference.trim()) return false;
    const standings = current.data;
    return this.mutate(async () => {
      const data = await firstValueFrom(this.adapter.publish(standings.standingsId, standings.lockVersion, sourceReference.trim()));
      this.state.update((state) => ({ ...state, data, conflictMessage: null }));
    }, 'Classificação publicada com fonte oficial.');
  }

  async correct(
    reason: string,
    sourceReference: string,
    columns: Array<string>,
    rows: Array<SportsStandingsRow>,
  ): Promise<boolean> {
    const current = this.state();
    if (!current.data || !reason.trim() || !sourceReference.trim()) return false;
    const standings = current.data;
    return this.mutate(async () => {
      const data = await firstValueFrom(
        this.adapter.correct(standings.standingsId, standings.lockVersion, reason.trim(), sourceReference.trim(), columns, rows),
      );
      this.state.update((state) => ({ ...state, data, conflictMessage: null }));
    }, 'Correção publicada como nova versão auditada.');
  }

  private async mutate(action: () => Promise<void>, successMessage: string): Promise<boolean> {
    this.state.update((state) => ({
      ...state,
      isMutating: true,
      errorMessage: null,
      conflictMessage: null,
      feedbackMessage: null,
    }));

    try {
      await action();
      this.state.update((state) => ({ ...state, isMutating: false, feedbackMessage: successMessage }));
      return true;
    } catch (error: unknown) {
      if (this.adapter.isUnauthorized(error)) {
        this.state.update((state) => ({
          ...INITIAL,
          status: 'denied',
          standingsId: state.standingsId,
          errorMessage: 'Seu acesso a esta classificação foi encerrado.',
        }));
      } else if (error instanceof HttpErrorResponse && error.status === 409) {
        this.state.update((state) => ({
          ...state,
          isMutating: false,
          conflictMessage:
            'Esta classificação foi alterada por outra pessoa. Recarregue para comparar a versão atual antes de tentar novamente.',
        }));
      } else {
        this.state.update((state) => ({
          ...state,
          isMutating: false,
          errorMessage: 'Não foi possível salvar esta alteração agora. Tente novamente.',
        }));
      }
      return false;
    }
  }
}
