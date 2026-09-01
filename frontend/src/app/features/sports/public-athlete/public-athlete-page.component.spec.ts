import { TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { signal } from '@angular/core';
import { describe, expect, it, vi } from 'vitest';
import { PublicAthletePageComponent } from './public-athlete-page.component';
import { PublicAthleteProfileStore } from './public-athlete-profile.store';
import { SportsPublicViewStore } from '../public-view/sports-public-view.store';

describe('PublicAthletePageComponent', () => {
  it('carrega detalhe e perfil por URL direta sem exibir PII', async () => {
    const store = { isLoading: signal(false), isNotFound: signal(false), isError: signal(false), errorMessage: signal(null), data: signal({ athleteId: 'a1', name: 'Ana', slug: 'ana', teams: [], events: [], stats: { totalParticipations: 2, totalWins: 1, totalLosses: 1 } }), retry: vi.fn(), load: vi.fn() };
    const profileStore = { isLoading: signal(false), isReady: signal(true), hasPublishedContent: signal(true), profile: signal({ athleteId: 'a1', athleteName: 'Ana', athleteSlug: 'ana', course: 'Direito', bio: 'Bio publica', instagramUrl: 'https://instagram.com/ana', xUrl: null, linkedinUrl: null, stravaUrl: null, youtubeUrl: null, lockVersion: 0, updatedAt: '2026-08-31T12:00:00Z' }), load: vi.fn() };
    await TestBed.configureTestingModule({ imports: [PublicAthletePageComponent], providers: [{ provide: SportsPublicViewStore, useValue: store }, { provide: PublicAthleteProfileStore, useValue: profileStore }, { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'a1' } } } }] }).compileComponents();
    const fixture = TestBed.createComponent(PublicAthletePageComponent);
    fixture.detectChanges();
    expect(store.load).toHaveBeenCalledWith('athlete', 'a1');
    expect(profileStore.load).toHaveBeenCalledWith('a1');
    expect(fixture.nativeElement.textContent).toContain('participacoes publicas');
    expect(fixture.nativeElement.textContent).toContain('Bio publica');
    expect(fixture.nativeElement.textContent).not.toContain('matricula');
  });

  it('renderiza bio como texto e nao interpreta markup', async () => {
    const store = { isLoading: signal(false), isNotFound: signal(false), isError: signal(false), errorMessage: signal(null), data: signal({ athleteId: 'a1', name: 'Ana', slug: 'ana', teams: [], events: [], stats: { totalParticipations: 0, totalWins: 0, totalLosses: 0 } }), retry: vi.fn(), load: vi.fn() };
    const profileStore = { isLoading: signal(false), isReady: signal(true), hasPublishedContent: signal(true), profile: signal({ athleteId: 'a1', athleteName: 'Ana', athleteSlug: 'ana', course: null, bio: '<img src=x onerror=alert(1)>', instagramUrl: null, xUrl: null, linkedinUrl: null, stravaUrl: null, youtubeUrl: null, lockVersion: 0, updatedAt: '2026-08-31T12:00:00Z' }), load: vi.fn() };
    await TestBed.configureTestingModule({ imports: [PublicAthletePageComponent], providers: [{ provide: SportsPublicViewStore, useValue: store }, { provide: PublicAthleteProfileStore, useValue: profileStore }, { provide: ActivatedRoute, useValue: { snapshot: { paramMap: { get: () => 'a1' } } } }] }).compileComponents();
    const fixture = TestBed.createComponent(PublicAthletePageComponent);
    fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('img')).toBeNull();
    expect(fixture.nativeElement.querySelector('.public-profile__bio')?.textContent).toContain('<img');
  });
});
