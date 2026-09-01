import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, filter, firstValueFrom, map, tap } from 'rxjs';
import { AthletePublicProfile } from '../../../api/generated/model/athlete-public-profile';
import { AthletePublicProfileUpdateRequest } from '../../../api/generated/model/athlete-public-profile-update-request';
import { AthleteMediaApiAdapter, AthleteMediaUploadEvent } from './athlete-media-api.adapter';
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

type AthleteProfileStatus =
  | 'initial'
  | 'loading'
  | 'ready'
  | 'saving'
  | 'removing'
  | 'not-linked'
  | 'denied'
  | 'conflict'
  | 'error';

type PhotoStatus = 'idle' | 'loading' | 'uploading' | 'removing' | 'error';

interface AthleteProfileState {
  status: AthleteProfileStatus;
  profile: AthletePublicProfile | null;
  draft: AthleteProfileDraft | null;
  errorMessage: string | null;
  errorCorrelationId: string | null;
  feedbackMessage: string | null;
  media: import('../../../api/generated/model/athlete-media-response').AthleteMediaResponse | null;
  currentPhotoUrl: string | null;
  previewPhotoUrl: string | null;
  selectedPhoto: File | null;
  photoStatus: PhotoStatus;
  photoProgress: number | null;
  photoErrorTitle: string | null;
  photoErrorMessage: string | null;
  photoFeedbackMessage: string | null;
}

const INITIAL_STATE: AthleteProfileState = Object.freeze({
  status: 'initial',
  profile: null,
  draft: null,
  errorMessage: null,
  errorCorrelationId: null,
  feedbackMessage: null,
  media: null,
  currentPhotoUrl: null,
  previewPhotoUrl: null,
  selectedPhoto: null,
  photoStatus: 'idle',
  photoProgress: null,
  photoErrorTitle: null,
  photoErrorMessage: null,
  photoFeedbackMessage: null,
});

@Injectable({ providedIn: 'root' })
export class AthleteProfileStore {
  private readonly adapter = inject(AthleteProfileApiAdapter);
  private readonly mediaAdapter = inject(AthleteMediaApiAdapter);
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
  readonly media = computed(() => this.state().media);
  readonly selectedPhoto = computed(() => this.state().selectedPhoto);
  readonly currentPhotoUrl = computed(() => this.state().currentPhotoUrl);
  readonly previewPhotoUrl = computed(() => this.state().previewPhotoUrl);
  readonly photoUrl = computed(() => this.previewPhotoUrl() ?? this.currentPhotoUrl());
  readonly hasPhoto = computed(() => Boolean(this.state().media?.present && this.currentPhotoUrl()));
  readonly photoProgress = computed(() => this.state().photoProgress);
  readonly photoErrorTitle = computed(() => this.state().photoErrorTitle);
  readonly photoErrorMessage = computed(() => this.state().photoErrorMessage);
  readonly photoFeedbackMessage = computed(() => this.state().photoFeedbackMessage);
  readonly isPhotoLoading = computed(() => this.state().photoStatus === 'loading');
  readonly isPhotoUploading = computed(() => this.state().photoStatus === 'uploading');
  readonly isPhotoRemoving = computed(() => this.state().photoStatus === 'removing');

  async load(): Promise<void> {
    const requestId = ++this.requestSequence;
    this.state.set({ ...INITIAL_STATE, status: 'loading' });
    try {
      const profile = await firstValueFrom(this.adapter.fetchMine());
      if (requestId !== this.requestSequence) return;
      this.setProfile(profile, null);
      await this.loadPhoto(profile.athleteId, requestId);
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
    if (
      !current.profile ||
      !current.draft ||
      current.status === 'saving' ||
      current.status === 'removing'
    )
      return false;
    this.state.update((state) => ({
      ...state,
      status: 'saving',
      errorMessage: null,
      errorCorrelationId: null,
      feedbackMessage: null,
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
    if (!current.profile || current.status === 'saving' || current.status === 'removing')
      return false;
    this.state.update((state) => ({
      ...state,
      status: 'removing',
      errorMessage: null,
      errorCorrelationId: null,
      feedbackMessage: null,
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

  selectPhoto(file: File | null): boolean {
    const current = this.state();
    if (!current.profile || current.photoStatus === 'uploading' || current.photoStatus === 'removing') {
      return false;
    }
    this.revokePreviewUrl();
    this.state.update((state) => ({
      ...state,
      selectedPhoto: null,
      previewPhotoUrl: null,
      photoProgress: null,
      photoErrorTitle: null,
      photoErrorMessage: null,
      photoFeedbackMessage: null,
      photoStatus: 'idle',
    }));
    if (!file || !['image/jpeg', 'image/png'].includes(file.type) || file.size <= 0 || file.size > MAX_PHOTO_BYTES) {
      this.state.update((state) => ({
        ...state,
        photoStatus: 'error',
        photoErrorTitle: 'Foto nao selecionada',
        photoErrorMessage:
          'Escolha uma imagem nos formatos e limites informados. Sua foto publica atual nao foi alterada.',
      }));
      return false;
    }
    const previewPhotoUrl = this.mediaAdapter.createPreviewUrl(file);
    this.state.update((state) => ({
      ...state,
      selectedPhoto: file,
      previewPhotoUrl,
      photoStatus: 'idle',
    }));
    return true;
  }

  async uploadPhoto(): Promise<boolean> {
    const current = this.state();
    if (!current.selectedPhoto || this.isPhotoUploading() || this.isPhotoRemoving()) return false;
    const file = current.selectedPhoto;
    this.state.update((state) => ({
      ...state,
      photoStatus: 'uploading',
      photoProgress: 0,
      photoErrorTitle: null,
      photoErrorMessage: null,
      photoFeedbackMessage: null,
    }));
    try {
      const media = await firstValueFrom(
        this.mediaAdapter.uploadMine(file).pipe(
          mapUploadProgress((progress) =>
            this.state.update((state) => ({ ...state, photoProgress: progress })),
          ),
        ),
      );
      const processedUrl = await firstValueFrom(this.mediaAdapter.fetchPublicPhoto(media.athleteId));
      const previousUrl = this.state().currentPhotoUrl;
      const previewUrl = this.state().previewPhotoUrl;
      this.state.update((state) => ({
        ...state,
        media,
        currentPhotoUrl: processedUrl,
        previewPhotoUrl: null,
        selectedPhoto: null,
        photoStatus: 'idle',
        photoProgress: null,
        photoErrorTitle: null,
        photoErrorMessage: null,
        photoFeedbackMessage: 'Foto enviada.',
      }));
      this.revokeUrls(previousUrl, previewUrl);
      return true;
    } catch (error: unknown) {
      if (this.isDeniedError(error)) {
        this.denyAccess();
        return false;
      }
      const processingFailure =
        error instanceof HttpErrorResponse && [400, 413, 415, 422].includes(error.status);
      if (processingFailure) {
        this.revokePreviewUrl();
        this.state.update((state) => ({
          ...state,
          selectedPhoto: null,
          previewPhotoUrl: null,
          photoStatus: 'error',
          photoProgress: null,
          photoErrorTitle: 'Nao foi possivel processar a foto',
          photoErrorMessage:
            'O arquivo nao foi publicado e sua foto publica atual foi mantida. Escolha outra imagem nos formatos informados.',
          photoFeedbackMessage: null,
        }));
      } else {
        this.state.update((state) => ({
          ...state,
          photoStatus: 'error',
          photoProgress: null,
          photoErrorTitle: 'Nao foi possivel enviar a foto',
          photoErrorMessage:
            'Sua foto publica atual foi mantida. Verifique a conexao e tente enviar novamente.',
          photoFeedbackMessage: null,
        }));
      }
      return false;
    }
  }

  async removePhoto(): Promise<boolean> {
    const current = this.state();
    if (!current.media?.present || this.isPhotoUploading() || this.isPhotoRemoving()) return false;
    this.state.update((state) => ({
      ...state,
      photoStatus: 'removing',
      photoProgress: null,
      photoErrorTitle: null,
      photoErrorMessage: null,
      photoFeedbackMessage: null,
    }));
    try {
      const media = await firstValueFrom(this.mediaAdapter.removeMine());
      const previousUrl = this.state().currentPhotoUrl;
      const previewUrl = this.state().previewPhotoUrl;
      this.state.update((state) => ({
        ...state,
        media,
        currentPhotoUrl: null,
        previewPhotoUrl: null,
        selectedPhoto: null,
        photoStatus: 'idle',
        photoErrorTitle: null,
        photoErrorMessage: null,
        photoFeedbackMessage: 'Foto removida.',
      }));
      this.revokeUrls(previousUrl, previewUrl);
      return true;
    } catch (error: unknown) {
      if (this.isDeniedError(error)) {
        this.denyAccess();
        return false;
      }
      this.state.update((state) => ({
        ...state,
        photoStatus: 'error',
        photoErrorTitle: 'Nao foi possivel remover a foto',
        photoErrorMessage: 'Sua foto publica continua disponivel. Tente remover novamente.',
        photoFeedbackMessage: null,
      }));
      return false;
    }
  }

  releasePhotoResources(): void {
    const current = this.state();
    this.revokeUrls(current.currentPhotoUrl, current.previewPhotoUrl);
    this.state.update((state) => ({
      ...state,
      currentPhotoUrl: null,
      previewPhotoUrl: null,
      selectedPhoto: null,
      photoProgress: null,
      photoStatus: 'idle',
    }));
  }

  private setProfile(profile: AthletePublicProfile, feedbackMessage: string | null): void {
    const current = this.state();
    this.state.set({
      status: 'ready',
      profile,
      draft: this.draftFrom(profile),
      errorMessage: null,
      errorCorrelationId: null,
      feedbackMessage,
      media: current.media,
      currentPhotoUrl: current.currentPhotoUrl,
      previewPhotoUrl: current.previewPhotoUrl,
      selectedPhoto: current.selectedPhoto,
      photoStatus: current.photoStatus,
      photoProgress: current.photoProgress,
      photoErrorTitle: current.photoErrorTitle,
      photoErrorMessage: current.photoErrorMessage,
      photoFeedbackMessage: current.photoFeedbackMessage,
    });
  }

  private handleError(error: unknown, fallback: string, previous?: AthleteProfileState): void {
    if (error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403)) {
      this.denyAccess();
      return;
    }
    if (error instanceof HttpErrorResponse && error.status === 404) {
      this.releasePhotoResources();
      this.state.set({ ...INITIAL_STATE, status: 'not-linked' });
      return;
    }
    const correlationId = this.correlationId(error);
    const message =
      error instanceof HttpErrorResponse && error.status === 409
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

  private async loadPhoto(athleteId: string, requestId: number): Promise<void> {
    this.state.update((state) => ({
      ...state,
      photoStatus: 'loading',
      photoErrorTitle: null,
      photoErrorMessage: null,
      photoFeedbackMessage: null,
    }));
    try {
      const media = await firstValueFrom(this.mediaAdapter.fetchMine());
      if (requestId !== this.requestSequence) return;
      if (!media.present) {
        const previousUrl = this.state().currentPhotoUrl;
        this.state.update((state) => ({
          ...state,
          media,
          currentPhotoUrl: null,
          photoStatus: 'idle',
        }));
        this.revokeUrls(previousUrl, null);
        return;
      }
      const currentPhotoUrl = await firstValueFrom(this.mediaAdapter.fetchPublicPhoto(athleteId));
      if (requestId !== this.requestSequence) {
        this.mediaAdapter.revokeUrl(currentPhotoUrl);
        return;
      }
      const previousUrl = this.state().currentPhotoUrl;
      this.state.update((state) => ({ ...state, media, currentPhotoUrl, photoStatus: 'idle' }));
      this.revokeUrls(previousUrl, null);
    } catch (error: unknown) {
      if (this.isDeniedError(error)) {
        this.denyAccess();
        return;
      }
      this.state.update((state) => ({
        ...state,
        photoStatus: 'error',
        photoErrorTitle: 'Nao foi possivel carregar a foto',
        photoErrorMessage: 'Sua foto publica atual foi mantida. Tente novamente mais tarde.',
      }));
    }
  }

  private denyAccess(): void {
    ++this.requestSequence;
    const current = this.state();
    this.revokeUrls(current.currentPhotoUrl, current.previewPhotoUrl);
    this.state.set({ ...INITIAL_STATE, status: 'denied' });
  }

  private revokePreviewUrl(): void {
    const previewUrl = this.state().previewPhotoUrl;
    if (previewUrl) this.mediaAdapter.revokeUrl(previewUrl);
  }

  private revokeUrls(...urls: (string | null)[]): void {
    new Set(urls.filter((url): url is string => Boolean(url))).forEach((url) =>
      this.mediaAdapter.revokeUrl(url),
    );
  }

  private isDeniedError(error: unknown): boolean {
    return error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403);
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

  private nullableDraft(
    draft: AthleteProfileDraft,
  ): Omit<AthletePublicProfileUpdateRequest, 'lockVersion'> {
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

const MAX_PHOTO_BYTES = 5 * 1024 * 1024;

function mapUploadProgress(onProgress: (progress: number) => void) {
  return (source: Observable<AthleteMediaUploadEvent>) =>
    source.pipe(
      tap((event) => {
        if (event.kind === 'progress') onProgress(event.progress);
      }),
      filter(
        (event): event is Extract<AthleteMediaUploadEvent, { kind: 'complete' }> =>
          event.kind === 'complete',
      ),
      map((event) => event.media),
    );
}
