import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom, retry, timer } from 'rxjs';
import { SportsStandingsSnapshot } from '../../../api/generated/model/sports-standings-snapshot';
import { PublicStandingsApiAdapter } from './public-standings-api.adapter';

type PublicStandingsStatus = 'initial' | 'loading' | 'populated' | 'not-found' | 'error';

interface PublicStandingsState {
  status: PublicStandingsStatus;
  standingsId: string | null;
  data: SportsStandingsSnapshot | null;
  errorMessage: string | null;
}

const INITIAL: PublicStandingsState = {
  status: 'initial',
  standingsId: null,
  data: null,
  errorMessage: null,
};

@Injectable({ providedIn: 'root' })
export class PublicStandingsStore {
  private readonly adapter = inject(PublicStandingsApiAdapter);
  private readonly state = signal<PublicStandingsState>(INITIAL);
  private requestSequence = 0;

  readonly status = computed(() => this.state().status);
  readonly data = computed(() => this.state().data);
  readonly errorMessage = computed(() => this.state().errorMessage);
  readonly isLoading = computed(() => this.status() === 'loading');
  readonly isNotFound = computed(() => this.status() === 'not-found');
  readonly isError = computed(() => this.status() === 'error');

  async load(standingsId: string): Promise<void> {
    const id = standingsId.trim();
    const requestId = ++this.requestSequence;
    this.state.set({ status: 'loading', standingsId: id || null, data: null, errorMessage: null });

    try {
      const data = await firstValueFrom(
        this.adapter.fetch(id).pipe(
          retry({
            count: 2,
            delay: (error: unknown, attempt) => {
              if (error instanceof HttpErrorResponse && error.status === 404) throw error;
              return timer(attempt === 1 ? 250 : 500);
            },
          }),
        ),
      );

      if (requestId !== this.requestSequence) return;
      if (!data || data.standingsId !== id || !Array.isArray(data.rows) || !Array.isArray(data.columns)) {
        throw new Error('CONTRATO_STANDINGS_INVALIDO');
      }

      this.state.set({ status: 'populated', standingsId: id, data, errorMessage: null });
    } catch (error: unknown) {
      if (requestId !== this.requestSequence) return;
      const notFound = this.adapter.isNotFound(error);
      this.state.set({
        status: notFound ? 'not-found' : 'error',
        standingsId: id || null,
        data: null,
        errorMessage: notFound
          ? 'Esta classificação ainda não possui publicação pública.'
          : 'Não foi possível carregar a classificação pública. Tente novamente.',
      });
    }
  }

  async retry(): Promise<void> {
    const id = this.state().standingsId;
    if (id) await this.load(id);
  }
}
