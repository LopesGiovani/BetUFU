import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { signal } from '@angular/core';
import { describe, expect, it, vi } from 'vitest';
import { AthleteProfilePageComponent } from './athlete-profile-page.component';
import { AthleteProfileStore } from './athlete-profile.store';

const profile = {
  athleteId: 'a1',
  athleteName: 'Ana Santos',
  athleteSlug: 'ana-santos',
  course: 'Direito',
  bio: 'Linha 1\nLinha 2',
  instagramUrl: 'https://instagram.com/ana',
  xUrl: '',
  linkedinUrl: '',
  stravaUrl: '',
  youtubeUrl: '',
  lockVersion: 0,
  updatedAt: '2026-08-31T12:00:00Z',
};

describe('AthleteProfilePageComponent', () => {
  it('renderiza formulario owner e pre-visualizacao como texto simples', async () => {
    const store = {
      profile: signal(profile),
      preview: signal({
        course: profile.course,
        bio: profile.bio,
        instagramUrl: profile.instagramUrl,
        xUrl: '',
        linkedinUrl: '',
        stravaUrl: '',
        youtubeUrl: '',
      }),
      isLoading: signal(false),
      isDenied: signal(false),
      isNotLinked: signal(false),
      isSaving: signal(false),
      isRemoving: signal(false),
      isConflict: signal(false),
      isError: signal(false),
      hasOptionalContent: signal(true),
      errorMessage: signal(null),
      feedbackMessage: signal(null),
      load: vi.fn(),
      save: vi.fn(async () => true),
      remove: vi.fn(async () => true),
      setDraft: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [AthleteProfilePageComponent],
      providers: [{ provide: AthleteProfileStore, useValue: store }, provideRouter([])],
    }).compileComponents();
    const fixture = TestBed.createComponent(AthleteProfilePageComponent);
    fixture.detectChanges();
    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector('textarea')?.value).toContain('Linha 1');
    expect(root.textContent).toContain('Salvar perfil esportivo');
    expect(root.textContent).toContain('Instagram');
    expect(root.querySelector('script')).toBeNull();
  });

  it('marca URL javascript como invalida e nao habilita salvamento', async () => {
    const store = {
      profile: signal(profile),
      preview: signal({
        course: '',
        bio: '',
        instagramUrl: '',
        xUrl: '',
        linkedinUrl: '',
        stravaUrl: '',
        youtubeUrl: '',
      }),
      isLoading: signal(false),
      isDenied: signal(false),
      isNotLinked: signal(false),
      isSaving: signal(false),
      isRemoving: signal(false),
      isConflict: signal(false),
      isError: signal(false),
      hasOptionalContent: signal(false),
      errorMessage: signal(null),
      feedbackMessage: signal(null),
      load: vi.fn(),
      save: vi.fn(),
      remove: vi.fn(),
      setDraft: vi.fn(),
    };
    await TestBed.configureTestingModule({
      imports: [AthleteProfilePageComponent],
      providers: [{ provide: AthleteProfileStore, useValue: store }, provideRouter([])],
    }).compileComponents();
    const fixture = TestBed.createComponent(AthleteProfilePageComponent);
    fixture.detectChanges();
    const input = fixture.nativeElement.querySelector('#instagramUrl') as HTMLInputElement;
    input.value = 'javascript:alert(1)';
    input.dispatchEvent(new Event('input'));
    input.dispatchEvent(new Event('blur'));
    fixture.detectChanges();
    expect(fixture.componentInstance.form.controls.instagramUrl.invalid).toBe(true);
    expect(fixture.componentInstance.saveDisabled).toBe(true);
  });
});
