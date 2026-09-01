import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { firstValueFrom } from 'rxjs';
import { AthleteProfileApiAdapter } from '../athlete-profile/athlete-profile-api.adapter';

export interface PublicAthleteTextProfile {
  athleteId: string;
  athleteName: string;
  athleteSlug: string;
  course: string | null;
  bio: string | null;
  instagramUrl: string | null;
  xUrl: string | null;
  linkedinUrl: string | null;
  stravaUrl: string | null;
  youtubeUrl: string | null;
}

type PublicAthleteProfileStatus = 'initial' | 'loading' | 'ready' | 'empty' | 'not-found' | 'error';

interface PublicAthleteProfileState {
  status: PublicAthleteProfileStatus;
  athleteId: string | null;
  profile: PublicAthleteTextProfile | null;
  errorMessage: string | null;
}

const INITIAL_STATE: PublicAthleteProfileState = Object.freeze({
  status: 'initial',
  athleteId: null,
  profile: null,
  errorMessage: null,
});

@Injectable({ providedIn: 'root' })
export class PublicAthleteProfileStore {
  private readonly adapter = inject(AthleteProfileApiAdapter);
  private readonly state = signal<PublicAthleteProfileState>(INITIAL_STATE);
  private requestSequence = 0;

  readonly status = computed(() => this.state().status);
  readonly profile = computed(() => this.state().profile);
  readonly errorMessage = computed(() => this.state().errorMessage);
  readonly isLoading = computed(() => this.status() === 'loading');
  readonly isReady = computed(() => this.status() === 'ready');
  readonly isEmpty = computed(() => this.status() === 'empty');
  readonly isNotFound = computed(() => this.status() === 'not-found');
  readonly isError = computed(() => this.status() === 'error');
  readonly hasPublishedContent = computed(() => {
    const profile = this.profile();
    return Boolean(
      profile &&
      [
        profile.course,
        profile.bio,
        profile.instagramUrl,
        profile.xUrl,
        profile.linkedinUrl,
        profile.stravaUrl,
        profile.youtubeUrl,
      ].some((value) => typeof value === 'string' && value.trim() !== ''),
    );
  });

  async load(athleteId: string): Promise<void> {
    const normalizedId = athleteId.trim();
    const requestId = ++this.requestSequence;
    this.state.set({
      status: 'loading',
      athleteId: normalizedId || null,
      profile: null,
      errorMessage: null,
    });
    try {
      const profile = await firstValueFrom(this.adapter.fetchPublic(normalizedId));
      if (requestId !== this.requestSequence) return;
      const publicProfile: PublicAthleteTextProfile = Object.freeze({
        athleteId: profile.athleteId,
        athleteName: profile.athleteName,
        athleteSlug: profile.athleteSlug,
        course: profile.course ?? null,
        bio: profile.bio ?? null,
        instagramUrl: profile.instagramUrl ?? null,
        xUrl: profile.xUrl ?? null,
        linkedinUrl: profile.linkedinUrl ?? null,
        stravaUrl: profile.stravaUrl ?? null,
        youtubeUrl: profile.youtubeUrl ?? null,
      });
      const hasContent = [
        publicProfile.course,
        publicProfile.bio,
        publicProfile.instagramUrl,
        publicProfile.xUrl,
        publicProfile.linkedinUrl,
        publicProfile.stravaUrl,
        publicProfile.youtubeUrl,
      ].some((value) => typeof value === 'string' && value.trim() !== '');
      this.state.set({
        status: hasContent ? 'ready' : 'empty',
        athleteId: normalizedId,
        profile: publicProfile,
        errorMessage: null,
      });
    } catch (error: unknown) {
      if (requestId !== this.requestSequence) return;
      if (error instanceof HttpErrorResponse && error.status === 404) {
        this.state.set({
          status: 'not-found',
          athleteId: normalizedId,
          profile: null,
          errorMessage: null,
        });
        return;
      }
      this.state.set({
        status: 'error',
        athleteId: normalizedId,
        profile: null,
        errorMessage:
          error instanceof Error && error.message.startsWith('CONTRATO_')
            ? 'O perfil publico recebido nao corresponde ao contrato esperado.'
            : 'Nao foi possivel carregar os dados textuais deste atleta.',
      });
    }
  }

  async retry(): Promise<void> {
    const athleteId = this.state().athleteId;
    if (athleteId) await this.load(athleteId);
  }
}
