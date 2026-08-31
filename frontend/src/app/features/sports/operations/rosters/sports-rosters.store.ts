import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { SportsRosterDraft } from '../../../../api/generated/model/sports-roster-draft';
import { SportsRosterMemberRequest } from '../../../../api/generated/model/sports-roster-member-request';
import { SportsRosterPublication } from '../../../../api/generated/model/sports-roster-publication';
import { SportsRostersApiAdapter } from './sports-rosters-api.adapter';

export type SportsRostersStatus = 'initial' | 'loading' | 'ready' | 'empty' | 'denied' | 'error';
export type SportsRosterSnapshot = SportsRosterDraft | SportsRosterPublication;

interface RosterState {
  status: SportsRostersStatus;
  selectedTeamId: string | null;
  draft: SportsRosterDraft | null;
  publication: SportsRosterPublication | null;
  preview: SportsRosterSnapshot | null;
  errorMessage: string | null;
  conflictMessage: string | null;
  feedbackMessage: string | null;
  isMutating: boolean;
}

const INITIAL_STATE: RosterState = {
  status: 'initial',
  selectedTeamId: null,
  draft: null,
  publication: null,
  preview: null,
  errorMessage: null,
  conflictMessage: null,
  feedbackMessage: null,
  isMutating: false,
};

@Injectable({ providedIn: 'root' })
export class SportsRostersStore {
  private readonly adapter = inject(SportsRostersApiAdapter);
  private readonly state = signal<RosterState>(INITIAL_STATE);
  private requestSequence = 0;

  readonly status = computed(() => this.state().status);
  readonly selectedTeamId = computed(() => this.state().selectedTeamId);
  readonly draft = computed(() => this.state().draft);
  readonly publication = computed(() => this.state().publication);
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
  readonly hasDraft = computed(() => this.draft() !== null);
  readonly hasPublication = computed(() => this.publication() !== null);

  async load(teamId: string | null): Promise<void> {
    const requestId = ++this.requestSequence;
    if (!teamId) {
      this.state.set({ ...INITIAL_STATE, status: 'empty' });
      return;
    }
    this.state.update((current) => ({
      ...current,
      status: 'loading',
      selectedTeamId: teamId,
      errorMessage: null,
      conflictMessage: null,
      feedbackMessage: null,
      preview: null,
    }));
    try {
      const draft = await firstValueFrom(this.adapter.fetchDraft(teamId));
      let publication: SportsRosterPublication | null = null;
      try {
        publication = await firstValueFrom(this.adapter.fetchPublication(teamId));
      } catch (error) {
        if (this.adapter.isUnauthorized(error)) throw error;
        // A team may legitimately have no published snapshot yet.
      }
      if (requestId !== this.requestSequence) return;
      this.state.set({
        ...INITIAL_STATE,
        status: draft.members.length === 0 && publication === null ? 'empty' : 'ready',
        selectedTeamId: teamId,
        draft,
        publication,
      });
    } catch (error: unknown) {
      if (requestId !== this.requestSequence) return;
      if (this.adapter.isUnauthorized(error)) {
        this.clearPrivateState('denied');
      } else {
        this.state.set({
          ...this.state(),
          status: 'error',
          errorMessage: 'O elenco deste escopo nao esta disponivel agora. Tente novamente antes de editar ou publicar.',
        });
      }
    }
  }

  selectTeam(teamId: string): boolean {
    if (!teamId.trim()) return false;
    this.state.update((state) => ({ ...state, selectedTeamId: teamId, preview: null }));
    return true;
  }

  previewSnapshot(snapshot: SportsRosterSnapshot | null = this.draft() ?? this.publication()): void {
    this.state.update((state) => ({ ...state, preview: snapshot }));
  }

  closePreview(): void {
    this.state.update((state) => ({ ...state, preview: null }));
  }

  async updateDraft(members: readonly SportsRosterMemberRequest[]): Promise<boolean> {
    const draft = this.draft();
    const teamId = this.selectedTeamId();
    if (!draft || !teamId) return false;
    return this.mutate(
      () => firstValueFrom(this.adapter.updateDraft(teamId, draft.lockVersion, members)),
      'Rascunho de elenco salvo.',
    );
  }

  async publish(): Promise<boolean> {
    const draft = this.draft();
    const teamId = this.selectedTeamId();
    if (!draft || !teamId) return false;
    return this.mutate(
      () => firstValueFrom(this.adapter.publish(teamId, draft.lockVersion)),
      'Elenco publicado em um snapshot imutavel.',
    );
  }

  async correct(reason: string, members: readonly SportsRosterMemberRequest[]): Promise<boolean> {
    const draft = this.draft();
    const teamId = this.selectedTeamId();
    if (!draft || !teamId || !reason.trim()) return false;
    return this.mutate(
      () => firstValueFrom(this.adapter.correct(teamId, draft.lockVersion, reason.trim(), members)),
      'Correcao publicada como nova versao auditada.',
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
      await this.load(this.selectedTeamId());
      this.state.update((state) => ({ ...state, feedbackMessage: successMessage, isMutating: false }));
      return true;
    } catch (error: unknown) {
      if (this.adapter.isUnauthorized(error)) {
        this.clearPrivateState('denied');
      } else if (this.adapter.isConflict(error)) {
        this.state.update((state) => ({
          ...state,
          isMutating: false,
          conflictMessage: 'Este elenco foi alterado por outra pessoa. Seu rascunho local foi preservado; recarregue para comparar antes de tentar novamente.',
        }));
      } else {
        this.state.update((state) => ({
          ...state,
          isMutating: false,
          errorMessage: 'Nao foi possivel salvar este elenco agora. Tente novamente.',
        }));
      }
      return false;
    }
  }

  private clearPrivateState(status: SportsRostersStatus): void {
    this.state.set({
      ...INITIAL_STATE,
      status,
      errorMessage: status === 'denied' ? 'Seu acesso a este elenco foi encerrado. Nenhum rascunho privado foi mantido.' : null,
    });
  }
}
