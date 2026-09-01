import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AthleteMediaApiAdapter } from './athlete-media-api.adapter';
import { AthleteProfileApiAdapter } from './athlete-profile-api.adapter';
import { AthleteProfileStore } from './athlete-profile.store';

const profile = (version = 0) => ({
  athleteId: '11111111-1111-4111-8111-111111111111',
  athleteName: 'Ana Santos',
  athleteSlug: 'ana-santos',
  course: 'Engenharia',
  bio: 'Atleta\nuniversitaria',
  instagramUrl: 'https://instagram.com/ana',
  xUrl: null,
  linkedinUrl: null,
  stravaUrl: null,
  youtubeUrl: null,
  lockVersion: version,
  updatedAt: '2026-08-31T12:00:00Z',
});

describe('AthleteProfileStore', () => {
  let adapter: {
    fetchMine: ReturnType<typeof vi.fn>;
    updateMine: ReturnType<typeof vi.fn>;
    clearMine: ReturnType<typeof vi.fn>;
  };
  let mediaAdapter: {
    fetchMine: ReturnType<typeof vi.fn>;
    fetchPublicPhoto: ReturnType<typeof vi.fn>;
    uploadMine: ReturnType<typeof vi.fn>;
    removeMine: ReturnType<typeof vi.fn>;
    createPreviewUrl: ReturnType<typeof vi.fn>;
    revokeUrl: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    adapter = { fetchMine: vi.fn(), updateMine: vi.fn(), clearMine: vi.fn() };
    mediaAdapter = {
      fetchMine: vi.fn().mockReturnValue(of(media(false))),
      fetchPublicPhoto: vi.fn(),
      uploadMine: vi.fn(),
      removeMine: vi.fn(),
      createPreviewUrl: vi.fn().mockReturnValue('blob:preview'),
      revokeUrl: vi.fn(),
    };
    TestBed.configureTestingModule({
      providers: [
        AthleteProfileStore,
        { provide: AthleteProfileApiAdapter, useValue: adapter },
        { provide: AthleteMediaApiAdapter, useValue: mediaAdapter },
      ],
    });
  });

  it('carrega perfil owner e cria um rascunho separado para o formulario', async () => {
    adapter.fetchMine.mockReturnValue(of(profile()));
    const store = TestBed.inject(AthleteProfileStore);
    await store.load();
    expect(store.isReady()).toBe(true);
    expect(store.draft()).toEqual(
      expect.objectContaining({ course: 'Engenharia', bio: 'Atleta\nuniversitaria' }),
    );
    expect(store.lockVersion()).toBe(0);
  });

  it('envia lockVersion e atualiza a versao depois do save', async () => {
    adapter.fetchMine.mockReturnValue(of(profile()));
    adapter.updateMine.mockReturnValue(of(profile(1)));
    const store = TestBed.inject(AthleteProfileStore);
    await store.load();
    store.setDraft({ ...store.draft()!, course: 'Direito' });
    await expect(store.save()).resolves.toBe(true);
    expect(adapter.updateMine).toHaveBeenCalledWith(
      expect.objectContaining({ course: 'Direito', lockVersion: 0 }),
    );
    expect(store.lockVersion()).toBe(1);
  });

  it('limpa perfil, rascunho, preview e versao em 401/403', async () => {
    adapter.fetchMine.mockReturnValue(of(profile()));
    adapter.updateMine.mockReturnValue(throwError(() => new HttpErrorResponse({ status: 403 })));
    const store = TestBed.inject(AthleteProfileStore);
    await store.load();
    store.setDraft({ ...store.draft()!, course: 'Tentativa' });
    await expect(store.save()).resolves.toBe(false);
    expect(store.isDenied()).toBe(true);
    expect(store.profile()).toBeNull();
    expect(store.draft()).toBeNull();
    expect(store.preview()).toBeNull();
    expect(store.lockVersion()).toBeNull();
    expect(store.errorMessage()).toBeNull();
  });

  it('mantem rascunho quando ocorre conflito 409', async () => {
    adapter.fetchMine.mockReturnValue(of(profile()));
    adapter.updateMine.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 409, error: { correlationId: 'c-409' } })),
    );
    const store = TestBed.inject(AthleteProfileStore);
    await store.load();
    store.setDraft({ ...store.draft()!, course: 'Direito' });
    await expect(store.save()).resolves.toBe(false);
    expect(store.isConflict()).toBe(true);
    expect(store.draft()?.course).toBe('Direito');
  });

  it('mantem preview local ate receber e exibir somente a foto processada', async () => {
    adapter.fetchMine.mockReturnValue(of(profile()));
    mediaAdapter.fetchMine.mockReturnValue(of(media(true)));
    mediaAdapter.fetchPublicPhoto
      .mockReturnValueOnce(of('blob:processed-current'))
      .mockReturnValueOnce(of('blob:processed-new'));
    mediaAdapter.uploadMine.mockReturnValue(
      of({ kind: 'progress', progress: 45 }, { kind: 'complete', media: media(true, 2) }),
    );
    const store = TestBed.inject(AthleteProfileStore);
    await store.load();

    const file = new File(['photo'], 'avatar.png', { type: 'image/png' });
    expect(store.selectPhoto(file)).toBe(true);
    expect(store.photoUrl()).toBe('blob:preview');
    await expect(store.uploadPhoto()).resolves.toBe(true);

    expect(mediaAdapter.uploadMine).toHaveBeenCalledWith(file);
    expect(store.photoUrl()).toBe('blob:processed-new');
    expect(store.selectedPhoto()).toBeNull();
    expect(store.photoProgress()).toBeNull();
    expect(mediaAdapter.revokeUrl).toHaveBeenCalledWith('blob:preview');
    expect(mediaAdapter.revokeUrl).toHaveBeenCalledWith('blob:processed-current');
  });

  it('limpa File, URLs, progresso e mutacao quando upload recebe 401/403', async () => {
    adapter.fetchMine.mockReturnValue(of(profile()));
    mediaAdapter.fetchMine.mockReturnValue(of(media(true)));
    mediaAdapter.fetchPublicPhoto.mockReturnValue(of('blob:processed-current'));
    mediaAdapter.uploadMine.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 403 })),
    );
    const store = TestBed.inject(AthleteProfileStore);
    await store.load();
    store.selectPhoto(new File(['photo'], 'avatar.png', { type: 'image/png' }));

    await expect(store.uploadPhoto()).resolves.toBe(false);

    expect(store.isDenied()).toBe(true);
    expect(store.selectedPhoto()).toBeNull();
    expect(store.photoUrl()).toBeNull();
    expect(store.photoProgress()).toBeNull();
    expect(store.photoErrorMessage()).toBeNull();
    expect(store.isPhotoUploading()).toBe(false);
    expect(mediaAdapter.revokeUrl).toHaveBeenCalledWith('blob:preview');
    expect(mediaAdapter.revokeUrl).toHaveBeenCalledWith('blob:processed-current');
  });

  it('recusa tipo ou tamanho invalido sem trocar a foto vigente', async () => {
    adapter.fetchMine.mockReturnValue(of(profile()));
    mediaAdapter.fetchMine.mockReturnValue(of(media(true)));
    mediaAdapter.fetchPublicPhoto.mockReturnValue(of('blob:processed-current'));
    const store = TestBed.inject(AthleteProfileStore);
    await store.load();

    expect(store.selectPhoto(new File(['text'], 'avatar.txt', { type: 'text/plain' }))).toBe(false);
    expect(store.photoUrl()).toBe('blob:processed-current');
    expect(store.photoErrorTitle()).toBe('Foto nao selecionada');
    expect(store.photoErrorMessage()).toContain('Sua foto publica atual nao foi alterada.');
    expect(mediaAdapter.uploadMine).not.toHaveBeenCalled();
  });
});

const media = (present: boolean, mediaVersion = present ? 1 : 0) => ({
  athleteId: '11111111-1111-4111-8111-111111111111',
  present,
  contentType: present ? ('image/png' as const) : null,
  byteSize: present ? 1024 : null,
  width: present ? 320 : null,
  height: present ? 320 : null,
  mediaVersion,
  updatedAt: '2026-09-01T12:00:00Z',
});
