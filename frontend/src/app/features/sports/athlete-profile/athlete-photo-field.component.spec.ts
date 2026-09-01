import { signal } from '@angular/core';
import { TestBed } from '@angular/core/testing';
import { describe, expect, it, vi } from 'vitest';
import { AthletePhotoFieldComponent } from './athlete-photo-field.component';
import { AthleteProfileStore } from './athlete-profile.store';

describe('AthletePhotoFieldComponent', () => {
  it('oferece seletor nativo, preview acessivel e acoes owner explicitas', async () => {
    const store = createStore({ photoUrl: 'blob:processed-current', hasPhoto: true });
    await TestBed.configureTestingModule({
      imports: [AthletePhotoFieldComponent],
      providers: [{ provide: AthleteProfileStore, useValue: store }],
    }).compileComponents();
    const fixture = TestBed.createComponent(AthletePhotoFieldComponent);
    fixture.componentRef.setInput('athleteName', 'Ana Santos');
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    const input = root.querySelector('input[type="file"]') as HTMLInputElement;
    expect(input.accept).toBe('image/jpeg,image/png');
    expect(root.textContent).toContain('JPEG ou PNG, ate 5 MiB');
    expect(root.querySelector('img')?.getAttribute('alt')).toBe('Foto de Ana Santos');

    const file = new File(['photo'], 'avatar.png', { type: 'image/png' });
    Object.defineProperty(input, 'files', { configurable: true, value: [file] });
    input.dispatchEvent(new Event('change'));
    expect(store.selectPhoto).toHaveBeenCalledWith(file);
  });

  it('confirma remocao sem apagar a foto em um unico clique', async () => {
    const store = createStore({ photoUrl: 'blob:processed-current', hasPhoto: true });
    await TestBed.configureTestingModule({
      imports: [AthletePhotoFieldComponent],
      providers: [{ provide: AthleteProfileStore, useValue: store }],
    }).compileComponents();
    const fixture = TestBed.createComponent(AthletePhotoFieldComponent);
    fixture.componentRef.setInput('athleteName', 'Ana Santos');
    fixture.detectChanges();

    const buttons = [...fixture.nativeElement.querySelectorAll('button')] as HTMLButtonElement[];
    buttons.find((button) => button.textContent?.trim() === 'Remover foto')?.click();
    fixture.detectChanges();
    expect(store.removePhoto).not.toHaveBeenCalled();
    expect(fixture.nativeElement.textContent).toContain('Manter foto');
  });
});

function createStore(options: { photoUrl: string | null; hasPhoto: boolean }) {
  return {
    photoUrl: signal(options.photoUrl),
    hasPhoto: signal(options.hasPhoto),
    selectedPhoto: signal<File | null>(null),
    photoProgress: signal<number | null>(null),
    photoErrorTitle: signal<string | null>(null),
    photoErrorMessage: signal<string | null>(null),
    photoFeedbackMessage: signal<string | null>(null),
    isPhotoLoading: signal(false),
    isPhotoUploading: signal(false),
    isPhotoRemoving: signal(false),
    selectPhoto: vi.fn(),
    uploadPhoto: vi.fn(async () => true),
    removePhoto: vi.fn(async () => true),
    releasePhotoResources: vi.fn(),
  };
}
