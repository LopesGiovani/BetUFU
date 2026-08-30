import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { SportsAthleteSummary } from '../../../../api/generated/model/sports-athlete-summary';
import { SportsAthletesApiAdapter } from './sports-athletes-api.adapter';

export type SportsAthletesStatus = 'initial' | 'loading' | 'ready' | 'empty' | 'error';

interface AthletesState {
  status: SportsAthletesStatus;
  athletes: readonly SportsAthleteSummary[];
  errorMessage: string | null;
  conflictMessage: string | null;
  feedbackMessage: string | null;
  isMutating: boolean;
}

const INITIAL_STATE: AthletesState = {
  status: 'initial',
  athletes: Object.freeze([]),
  errorMessage: null,
  conflictMessage: null,
  feedbackMessage: null,
  isMutating: false,
};

@Injectable({ providedIn: 'root' })
export class SportsAthletesStore {
  private readonly adapter = inject(SportsAthletesApiAdapter);
  private readonly state = signal<AthletesState>(INITIAL_STATE);
  private requestSequence = 0;

  readonly status = computed(() => this.state().status);
  readonly athletes = computed(() => this.state().athletes);
  readonly errorMessage = computed(() => this.state().errorMessage);
  readonly conflictMessage = computed(() => this.state().conflictMessage);
  readonly feedbackMessage = computed(() => this.state().feedbackMessage);
  readonly isMutating = computed(() => this.state().isMutating);
  readonly isLoading = computed(() => this.status() === 'loading');
  readonly isReady = computed(() => this.status() === 'ready');
  readonly isEmpty = computed(() => this.status() === 'empty');
  readonly isError = computed(() => this.status() === 'error');
  readonly publishedAthletes = computed(() =>
    this.athletes().filter((athlete) => athlete.status === 'PUBLICADO'),
  );

  async load(): Promise<void> {
    const requestId = ++this.requestSequence;
    this.state.update((current) => ({
      ...current,
      status: 'loading',
      errorMessage: null,
      conflictMessage: null,
      feedbackMessage: null,
    }));

    try {
      const athletes = await firstValueFrom(this.adapter.fetchAthletes());
      if (requestId !== this.requestSequence) return;
      this.state.set({
        ...INITIAL_STATE,
        status: athletes.length === 0 ? 'empty' : 'ready',
        athletes,
      });
    } catch {
      if (requestId !== this.requestSequence) return;
      this.state.set({
        ...this.state(),
        status: 'error',
        errorMessage: 'Os atletas e vinculos deste escopo nao estao disponiveis agora. Tente novamente antes de cadastrar ou alterar um elenco.',
      });
    }
  }

  async createAthlete(
    teamId: string,
    name: string,
    slug: string,
    matricula?: string | null,
  ): Promise<boolean> {
    return this.mutate(
      () => firstValueFrom(this.adapter.createAthlete(teamId, name, slug, matricula)),
      'Atleta salvo como rascunho.',
    );
  }

  async updateAthlete(
    athleteId: string,
    expectedLockVersion: number,
    name: string,
    slug: string,
  ): Promise<boolean> {
    return this.mutate(
      () => firstValueFrom(this.adapter.updateAthlete(athleteId, expectedLockVersion, name, slug)),
      'Atleta atualizado.',
    );
  }

  async publishAthlete(
    athleteId: string,
    expectedLockVersion: number,
  ): Promise<boolean> {
    return this.mutate(
      () => firstValueFrom(this.adapter.publishAthlete(athleteId, expectedLockVersion)),
      'Atleta publicado.',
    );
  }

  clearMessages(): void {
    this.state.update((state) => ({ ...state, conflictMessage: null, feedbackMessage: null }));
  }

  private async mutate(action: () => Promise<unknown>, successMessage: string): Promise<boolean> {
    this.state.update((state) => ({
      ...state,
      isMutating: true,
      errorMessage: null,
      conflictMessage: null,
      feedbackMessage: null,
    }));
    try {
      await action();
      await this.load();
      this.state.update((state) => ({ ...state, feedbackMessage: successMessage, isMutating: false }));
      return true;
    } catch (error: unknown) {
      if (this.adapter.isConflict(error)) {
        this.state.update((state) => ({
          ...state,
          isMutating: false,
          conflictMessage: 'Este registro foi alterado por outra pessoa. Atualize a lista antes de tentar novamente.',
        }));
      } else {
        this.state.update((state) => ({
          ...state,
          isMutating: false,
          errorMessage: 'Nao foi possivel salvar este registro agora. Tente novamente.',
        }));
      }
      return false;
    }
  }
}
