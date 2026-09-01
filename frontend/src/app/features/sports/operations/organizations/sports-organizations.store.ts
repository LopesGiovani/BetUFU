import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom, forkJoin } from 'rxjs';
import { SportsOrganizationSummary } from '../../../../api/generated/model/sports-organization-summary';
import { SportsTeamSummary } from '../../../../api/generated/model/sports-team-summary';
import { SportsOrganizationsApiAdapter } from './sports-organizations-api.adapter';

export type SportsOrganizationsStatus = 'initial' | 'loading' | 'ready' | 'empty' | 'error';

interface OrganizationsState {
  status: SportsOrganizationsStatus;
  organizations: readonly SportsOrganizationSummary[];
  teams: readonly SportsTeamSummary[];
  errorMessage: string | null;
  conflictMessage: string | null;
  feedbackMessage: string | null;
  isMutating: boolean;
}

const INITIAL_STATE: OrganizationsState = {
  status: 'initial',
  organizations: Object.freeze([]),
  teams: Object.freeze([]),
  errorMessage: null,
  conflictMessage: null,
  feedbackMessage: null,
  isMutating: false,
};

@Injectable({ providedIn: 'root' })
export class SportsOrganizationsStore {
  private readonly adapter = inject(SportsOrganizationsApiAdapter);
  private readonly state = signal<OrganizationsState>(INITIAL_STATE);
  private requestSequence = 0;

  readonly status = computed(() => this.state().status);
  readonly organizations = computed(() => this.state().organizations);
  readonly teams = computed(() => this.state().teams);
  readonly errorMessage = computed(() => this.state().errorMessage);
  readonly conflictMessage = computed(() => this.state().conflictMessage);
  readonly feedbackMessage = computed(() => this.state().feedbackMessage);
  readonly isMutating = computed(() => this.state().isMutating);
  readonly isLoading = computed(() => this.status() === 'loading');
  readonly isReady = computed(() => this.status() === 'ready');
  readonly isEmpty = computed(() => this.status() === 'empty');
  readonly isError = computed(() => this.status() === 'error');
  readonly publishedOrganizations = computed(() =>
    this.organizations().filter((organization) => organization.status === 'PUBLICADO'),
  );

  async load(competitionId: string | null): Promise<void> {
    const requestId = ++this.requestSequence;
    if (!competitionId) {
      this.state.set({ ...INITIAL_STATE, status: 'empty' });
      return;
    }

    this.state.update((current) => ({
      ...current,
      status: 'loading',
      errorMessage: null,
      conflictMessage: null,
      feedbackMessage: null,
    }));

    try {
      const result = await firstValueFrom(
        forkJoin({
          organizations: this.adapter.fetchOrganizations(),
          teams: this.adapter.fetchTeams(competitionId),
        }),
      );
      if (requestId !== this.requestSequence) return;
      this.state.set({
        ...INITIAL_STATE,
        status: result.organizations.length === 0 && result.teams.length === 0 ? 'empty' : 'ready',
        organizations: result.organizations,
        teams: result.teams,
      });
    } catch {
      if (requestId !== this.requestSequence) return;
      this.state.set({
        ...this.state(),
        status: 'error',
        errorMessage:
          'Os cadastros deste escopo nao estao disponiveis agora. Tente novamente antes de criar ou alterar uma equipe.',
      });
    }
  }

  async createOrganization(name: string, slug: string, competitionId: string): Promise<boolean> {
    return this.mutate(
      () => firstValueFrom(this.adapter.createOrganization(name, slug)),
      'Organizacao salva como rascunho.',
      competitionId,
    );
  }

  async updateOrganization(
    organizationId: string,
    expectedLockVersion: number,
    name: string,
    slug: string,
    competitionId: string,
  ): Promise<boolean> {
    return this.mutate(
      () =>
        firstValueFrom(
          this.adapter.updateOrganization(organizationId, expectedLockVersion, name, slug),
        ),
      'Organizacao atualizada.',
      competitionId,
    );
  }

  async publishOrganization(
    organizationId: string,
    expectedLockVersion: number,
    competitionId: string,
  ): Promise<boolean> {
    return this.mutate(
      () => firstValueFrom(this.adapter.publishOrganization(organizationId, expectedLockVersion)),
      'Organizacao publicada.',
      competitionId,
    );
  }

  async createTeam(
    editionId: string,
    sportId: string,
    categoryId: string,
    organizationId: string,
    name: string,
    slug: string,
    competitionId: string,
  ): Promise<boolean> {
    return this.mutate(
      () =>
        firstValueFrom(
          this.adapter.createTeam(editionId, sportId, categoryId, organizationId, name, slug),
        ),
      'Equipe salva como rascunho.',
      competitionId,
    );
  }

  async updateTeam(
    teamId: string,
    expectedLockVersion: number,
    name: string,
    slug: string,
    competitionId: string,
  ): Promise<boolean> {
    return this.mutate(
      () => firstValueFrom(this.adapter.updateTeam(teamId, expectedLockVersion, name, slug)),
      'Equipe atualizada.',
      competitionId,
    );
  }

  async publishTeam(
    teamId: string,
    expectedLockVersion: number,
    competitionId: string,
  ): Promise<boolean> {
    return this.mutate(
      () => firstValueFrom(this.adapter.publishTeam(teamId, expectedLockVersion)),
      'Equipe publicada.',
      competitionId,
    );
  }

  clearMessages(): void {
    this.state.update((state) => ({ ...state, conflictMessage: null, feedbackMessage: null }));
  }

  private async mutate(
    action: () => Promise<unknown>,
    successMessage: string,
    competitionId: string,
  ): Promise<boolean> {
    this.state.update((state) => ({
      ...state,
      isMutating: true,
      errorMessage: null,
      conflictMessage: null,
      feedbackMessage: null,
    }));
    try {
      await action();
      await this.load(competitionId);
      this.state.update((state) => ({
        ...state,
        feedbackMessage: successMessage,
        isMutating: false,
      }));
      return true;
    } catch (error: unknown) {
      if (this.adapter.isConflict(error)) {
        this.state.update((state) => ({
          ...state,
          isMutating: false,
          conflictMessage:
            'Este registro foi alterado por outra pessoa. Atualize a lista antes de tentar novamente.',
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
