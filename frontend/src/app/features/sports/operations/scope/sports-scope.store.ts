import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom, forkJoin } from 'rxjs';
import { SportsCompetitionSummary } from '../../../../api/generated/model/sports-competition-summary';
import {
  SportsCatalogApiAdapter,
  SportsCatalogCapability,
} from '../catalog/sports-catalog-api.adapter';

export type SportsScopeStatus = 'initial' | 'loading' | 'ready' | 'empty' | 'denied' | 'error';

interface SportsScopeState {
  status: SportsScopeStatus;
  competitions: readonly SportsCompetitionSummary[];
  selectedCompetitionId: string | null;
  bootstrapAllowed: boolean;
  deepLinkRejected: boolean;
  errorMessage: string | null;
}

const INITIAL_STATE: SportsScopeState = {
  status: 'initial',
  competitions: Object.freeze([]),
  selectedCompetitionId: null,
  bootstrapAllowed: false,
  deepLinkRejected: false,
  errorMessage: null,
};

@Injectable({ providedIn: 'root' })
export class SportsScopeStore {
  private readonly adapter = inject(SportsCatalogApiAdapter);
  private readonly state = signal<SportsScopeState>(INITIAL_STATE);
  private requestSequence = 0;

  readonly status = computed(() => this.state().status);
  readonly competitions = computed(() => this.state().competitions);
  readonly selectedCompetitionId = computed(() => this.state().selectedCompetitionId);
  readonly selectedCompetition = computed(() =>
    this.competitions().find((competition) => competition.competitionId === this.selectedCompetitionId()) ?? null,
  );
  readonly bootstrapAllowed = computed(() => this.state().bootstrapAllowed);
  readonly deepLinkRejected = computed(() => this.state().deepLinkRejected);
  readonly errorMessage = computed(() => this.state().errorMessage);
  readonly hasSelection = computed(() => this.selectedCompetition() !== null);
  readonly isLoading = computed(() => this.status() === 'loading');
  readonly isReady = computed(() => this.status() === 'ready');
  readonly isEmpty = computed(() => this.status() === 'empty');
  readonly isDenied = computed(() => this.status() === 'denied');
  readonly isError = computed(() => this.status() === 'error');

  async load(requestedCompetitionId?: string | null): Promise<void> {
    const requestId = ++this.requestSequence;
    this.state.set({ ...this.state(), status: 'loading', errorMessage: null, deepLinkRejected: false });

    try {
      const { scope, capabilities } = await firstValueFrom(forkJoin({
        scope: this.adapter.fetchScope(),
        capabilities: this.adapter.fetchCapabilities(),
      }));
      if (requestId !== this.requestSequence) return;

      const canManage = capabilities.includes('MANAGE_SPORTS_CATALOG');
      const canBootstrap = capabilities.includes('BOOTSTRAP_SPORTS_COMPETITION');
      if (!canManage) {
        this.state.set({ ...INITIAL_STATE, status: 'denied' });
        return;
      }

      const requested = requestedCompetitionId?.trim() || null;
      const requestedIsAuthorized = requested !== null
        && scope.competitions.some((competition) => competition.competitionId === requested);
      const selectedCompetitionId = requestedIsAuthorized
        ? requested
        : scope.competitions.length === 1
          ? scope.competitions[0].competitionId
          : null;

      this.state.set({
        status: scope.competitions.length === 0 ? 'empty' : 'ready',
        competitions: [...scope.competitions].sort((left, right) =>
          left.name.localeCompare(right.name, 'pt-BR') || left.competitionId.localeCompare(right.competitionId),
        ),
        selectedCompetitionId,
        bootstrapAllowed: canBootstrap && scope.bootstrapAllowed && scope.competitions.length === 0,
        deepLinkRejected: requested !== null && !requestedIsAuthorized,
        errorMessage: null,
      });
    } catch {
      if (requestId !== this.requestSequence) return;
      this.state.set({
        ...this.state(),
        status: 'error',
        errorMessage: 'Nao foi possivel carregar as competicoes. Tente carregar novamente antes de iniciar o catalogo.',
      });
    }
  }

  selectCompetition(competitionId: string): boolean {
    const exists = this.competitions().some((competition) => competition.competitionId === competitionId);
    if (!exists) return false;
    this.state.update((state) => ({ ...state, selectedCompetitionId: competitionId, deepLinkRejected: false }));
    return true;
  }

  clearSelection(): void {
    this.state.update((state) => ({ ...state, selectedCompetitionId: null }));
  }
}
