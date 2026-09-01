import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
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

  beforeEach(() => {
    adapter = { fetchMine: vi.fn(), updateMine: vi.fn(), clearMine: vi.fn() };
    TestBed.configureTestingModule({
      providers: [AthleteProfileStore, { provide: AthleteProfileApiAdapter, useValue: adapter }],
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
});
