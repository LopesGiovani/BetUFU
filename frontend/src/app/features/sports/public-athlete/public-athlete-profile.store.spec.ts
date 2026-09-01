import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { AthleteProfileApiAdapter } from '../athlete-profile/athlete-profile-api.adapter';
import { PublicAthleteProfileStore } from './public-athlete-profile.store';

const publicProfile = {
  athleteId: 'a1',
  athleteName: 'Ana',
  athleteSlug: 'ana',
  course: 'Direito',
  bio: 'Bio publica',
  instagramUrl: 'https://instagram.com/ana',
  xUrl: null,
  linkedinUrl: null,
  stravaUrl: null,
  youtubeUrl: null,
  lockVersion: 2,
  updatedAt: '2026-08-31T12:00:00Z',
};

describe('PublicAthleteProfileStore', () => {
  let adapter: { fetchPublic: ReturnType<typeof vi.fn> };
  beforeEach(() => {
    adapter = { fetchPublic: vi.fn() };
    TestBed.configureTestingModule({
      providers: [
        PublicAthleteProfileStore,
        { provide: AthleteProfileApiAdapter, useValue: adapter },
      ],
    });
  });

  it('carrega somente a projecao publica por athleteId', async () => {
    adapter.fetchPublic.mockReturnValue(of(publicProfile));
    const store = TestBed.inject(PublicAthleteProfileStore);
    await store.load('a1');
    expect(store.isReady()).toBe(true);
    expect(store.profile()).toEqual(
      expect.objectContaining({ athleteId: 'a1', course: 'Direito', bio: 'Bio publica' }),
    );
    expect(store.profile()).not.toHaveProperty('lockVersion');
    expect(store.profile()).not.toHaveProperty('updatedAt');
    expect(adapter.fetchPublic).toHaveBeenCalledWith('a1');
  });

  it('converte 404 para ausencia publica sem expor detalhes da falha', async () => {
    adapter.fetchPublic.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 404, error: { correlationId: 'private' } })),
    );
    const store = TestBed.inject(PublicAthleteProfileStore);
    await store.load('missing');
    expect(store.isNotFound()).toBe(true);
    expect(store.profile()).toBeNull();
    expect(store.errorMessage()).toBeNull();
  });

  it('nao guarda estado de formulario ou mutacao do owner', () => {
    const store = TestBed.inject(PublicAthleteProfileStore);
    expect('draft' in store).toBe(false);
    expect('lockVersion' in store).toBe(false);
    expect('save' in store).toBe(false);
    expect('remove' in store).toBe(false);
  });
});
