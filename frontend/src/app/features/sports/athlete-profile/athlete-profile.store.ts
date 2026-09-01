import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AthletePublicProfile } from '../../../api/generated/model/athlete-public-profile';
import { AthletePublicProfileUpdateRequest } from '../../../api/generated/model/athlete-public-profile-update-request';
import { AthleteProfileApiAdapter } from './athlete-profile-api.adapter';

export interface AthleteProfileDraft {
  course: string;
  bio: string;
  instagramUrl: string;
  xUrl: string;
  linkedinUrl: string;
  stravaUrl: string;
  youtubeUrl: string;
}

type AthleteProfileStatus = 'initial' | 'loading' | 'ready' | 'saving' | 'removing' | 'not-linked' | 'denied' | 'conflict' | 'error';

interface AthleteProfileState {
  status: AthleteProfileStatus;
  profile: AthletePublicProfile | null;
  draft: AthleteProfileDraft | null;
  errorMessage: string | null;
  errorCorrelationId: string | null;
  feedbackMessage: string | null;
}

const INITIAL_STATE: AthleteProfileState = Object.freeze({
  status: 'initial', profile: null, draft: null, errorMessage: null,
  errorCorrelationId: null, feedbackMessage: null,
});

@Injectable({ providedIn: 'root' })
export class AthleteProfileStore {
  private readonly adapter = inject(AthleteProfileApiAdapter);
  private readonly state = signal<AthleteProfileState>(INITIAL_STATE);
  private requestSequence = 0;

  readonly status = computed(() => this.state().status);
  readonly profile = computed(() => this.state().profile);
  readonly draft = computed(() => this.state().draft);
  readonly preview = computed(() => this.state().draft);
  readonly lockVersion = computed(() => this.state().profile?.lockVersion ?? null);
  readonly errorMessage = computed(() => this.state().errorMessage);
  readonly errorCorrelationId = computed(() => this.state().errorCorrelationId);
  readonly feedbackMessage = computed(() => this.state().feedbackMessage);
  readonly isLoading = computed(() => this.status() === 'loading');
  readonly isSaving = computed(() => this.status() === 'saving');
  readonly isRemoving = computed(() => this.status() === 'removing');
  readonly isReady = computed(() => this.status() === 'ready');
  readonly isNotLinked = computed(() => this.status() === 'not-linked');
  readonly isDenied = computed(() => this.status() === 'denied');
  readonly isConflict = computed(() => this.status() === 'conflict');
  readonly isError = computed(() => this.status() === 'error');
  readonly hasOptionalContent = computed(() => {
    const draft = this.draft();
    return draft ? Object.values(draft).some((value) => value.trim() !== '') : false;
  });

  async load(): Promise<void> {
    const requestId = ++this.requestSequence;
    this.state.set({ ...INITIAL_STATE, status: 'loading' });
    try {
      const profile = await firstValueFrom(this.adapter.fetchMine());
      if (requestId !== this.requestSequence) return;
      this.setProfile(profile, null);
    } catch (error: unknown) {
      if (requestId !== this.requestSequence) return;
      this.handleError(error, 'Nao foi possivel carregar seu perfil esportivo.');
    }
  }

  setDraft(draft: AthleteProfileDraft): void {
    if (!this.state().profile) return;
    this.state.update((state) => ({
      ...state,
      draft: { ...draft },
      feedbackMessage: null,
    }));
  }

  async save(): Promise<boolean> {
    const current = this.state();
    if (!current.profile || !current.draft || current.status === 'saving' || current.status === 'removing') return false;
    this.state.update((state) => ({
      ...state, status: 'saving', errorMessage: null, errorCorrelationId: null, feedbackMessage: null,
    }));
    const request: AthletePublicProfileUpdateRequest = {
      ...this.nullableDraft(current.draft),
      lockVersion: current.profile.lockVersion,
    };
    try {
      const profile = await firstValueFrom(this.adapter.updateMine(request));
      this.setProfile(profile, 'Perfil esportivo atualizado.');
      return true;
    } catch (error: unknown) {
      this.handleError(error, 'Nao foi possivel salvar seu perfil esportivo.', current);
      return false;
    }
  }

  async remove(): Promise<boolean> {
    const current = this.state();
    if (!current.profile || current.status === 'saving' || current.status === 'removing') return false;
    this.state.update((state) => ({
      ...state, status: 'removing', errorMessage: null, errorCorrelationId: null, feedbackMessage: null,
    }));
    try {
      const profile = await firstValueFrom(this.adapter.clearMine());
      this.setProfile(profile, 'Conteudo opcional removido.');
      return true;
    } catch (error: unknown) {
      this.handleError(error, 'Nao foi possivel remover o conteudo opcional.', current);
      return false;
    }
  }

  private setProfile(profile: AthletePublicProfile, feedbackMessage: string | null): void {
    this.state.set({
      status: 'ready',
      profile,
      draft: this.draftFrom(profile),
      errorMessage: null,
      errorCorrelationId: null,
      feedbackMessage,
    });
  }

  private handleError(error: unknown, fallback: string, previous?: AthleteProfileState): void {
    if (error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403)) {
      ++this.requestSequence;
      this.state.set({ ...INITIAL_STATE, status: 'denied' });
      return;
    }
    if (error instanceof HttpErrorResponse && error.status === 404) {
      this.state.set({ ...INITIAL_STATE, status: 'not-linked' });
      return;
    }
    const correlationId = this.correlationId(error);
    const message = error instanceof HttpErrorResponse && error.status === 409
      ? 'Este perfil foi atualizado em outra tela. Recarregue antes de salvar novamente.'
      : error instanceof Error && error.message.startsWith('CONTRATO_')
        ? 'Os dados recebidos nao correspondem ao contrato de perfil esportivo.'
        : correlationId
          ? `${fallback} Codigo de referencia: ${correlationId}.`
          : fallback;
    this.state.set({
      ...(previous ?? INITIAL_STATE),
      status: error instanceof HttpErrorResponse && error.status === 409 ? 'conflict' : 'error',
      errorMessage: message,
      errorCorrelationId: correlationId,
      feedbackMessage: null,
    });
  }

  private draftFrom(profile: AthletePublicProfile): AthleteProfileDraft {
    return {
      course: profile.course ?? '',
      bio: profile.bio ?? '',
      instagramUrl: profile.instagramUrl ?? '',
      xUrl: profile.xUrl ?? '',
      linkedinUrl: profile.linkedinUrl ?? '',
      stravaUrl: profile.stravaUrl ?? '',
      youtubeUrl: profile.youtubeUrl ?? '',
    };
  }

  private nullableDraft(draft: AthleteProfileDraft): Omit<AthletePublicProfileUpdateRequest, 'lockVersion'> {
    const optional = (value: string): string | null => value.trim() || null;
    return {
      course: optional(draft.course),
      bio: optional(draft.bio),
      instagramUrl: optional(draft.instagramUrl),
      xUrl: optional(draft.xUrl),
      linkedinUrl: optional(draft.linkedinUrl),
      stravaUrl: optional(draft.stravaUrl),
      youtubeUrl: optional(draft.youtubeUrl),
    };
  }

  private correlationId(error: unknown): string | null {
    if (error instanceof HttpErrorResponse && error.error && typeof error.error === 'object') {
      const value = (error.error as Record<string, unknown>)['correlationId'];
      if (typeof value === 'string' && value.trim()) return value.trim();
    }
    return null;
  }
}
