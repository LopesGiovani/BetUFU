import { ChangeDetectionStrategy, Component, computed, effect, inject } from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
  ValidatorFn,
  Validators,
} from '@angular/forms';
import { RouterLink } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { AthleteProfileDraft, AthleteProfileStore } from './athlete-profile.store';

type SocialControlName = 'instagramUrl' | 'xUrl' | 'linkedinUrl' | 'stravaUrl' | 'youtubeUrl';

const SOCIAL_HOSTS: Readonly<Record<SocialControlName, ReadonlySet<string>>> = Object.freeze({
  instagramUrl: new Set(['instagram.com', 'www.instagram.com', 'instagr.am']),
  xUrl: new Set(['x.com', 'www.x.com', 'twitter.com', 'www.twitter.com']),
  linkedinUrl: new Set(['linkedin.com', 'www.linkedin.com']),
  stravaUrl: new Set(['strava.com', 'www.strava.com']),
  youtubeUrl: new Set(['youtube.com', 'www.youtube.com', 'youtu.be']),
});

const PLAIN_TEXT: ValidatorFn = (control: AbstractControl): ValidationErrors | null => {
  const value = typeof control.value === 'string' ? control.value : '';
  return /[<>]/u.test(value) ? { plainText: true } : null;
};

function socialUrl(platform: SocialControlName): ValidatorFn {
  return (control: AbstractControl): ValidationErrors | null => {
    const value = typeof control.value === 'string' ? control.value.trim() : '';
    if (!value) return null;
    let parsed: URL;
    try {
      parsed = new URL(value);
    } catch {
      return { socialUrl: true };
    }
    return parsed.protocol === 'https:' &&
      !parsed.username &&
      !parsed.password &&
      SOCIAL_HOSTS[platform].has(parsed.hostname.toLowerCase()) &&
      parsed.pathname !== '/'
      ? null
      : { socialUrl: true };
  };
}

@Component({
  selector: 'app-athlete-profile-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './athlete-profile-page.component.html',
  styleUrl: './athlete-profile-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class AthleteProfilePageComponent {
  readonly store = inject(AthleteProfileStore);
  private patchedVersion: number | null = null;

  readonly form = new FormGroup({
    course: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(120), PLAIN_TEXT],
    }),
    bio: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(500), PLAIN_TEXT],
    }),
    instagramUrl: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(255), socialUrl('instagramUrl')],
    }),
    xUrl: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(255), socialUrl('xUrl')],
    }),
    linkedinUrl: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(255), socialUrl('linkedinUrl')],
    }),
    stravaUrl: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(255), socialUrl('stravaUrl')],
    }),
    youtubeUrl: new FormControl('', {
      nonNullable: true,
      validators: [Validators.maxLength(255), socialUrl('youtubeUrl')],
    }),
  });

  readonly bioLength = computed(() => this.store.preview()?.bio.length ?? 0);
  readonly socialFields: readonly {
    control: SocialControlName;
    label: string;
    placeholder: string;
  }[] = [
    {
      control: 'instagramUrl',
      label: 'Instagram',
      placeholder: 'https://instagram.com/seu-perfil',
    },
    { control: 'xUrl', label: 'X / Twitter', placeholder: 'https://x.com/seu-perfil' },
    {
      control: 'linkedinUrl',
      label: 'LinkedIn',
      placeholder: 'https://linkedin.com/in/seu-perfil',
    },
    {
      control: 'stravaUrl',
      label: 'Strava',
      placeholder: 'https://strava.com/athletes/seu-perfil',
    },
    { control: 'youtubeUrl', label: 'YouTube', placeholder: 'https://youtube.com/@seu-perfil' },
  ];
  readonly previewLinks = computed(() => {
    const preview = this.store.preview();
    if (!preview) return [];
    return [
      { label: 'Instagram', href: preview.instagramUrl },
      { label: 'X / Twitter', href: preview.xUrl },
      { label: 'LinkedIn', href: preview.linkedinUrl },
      { label: 'Strava', href: preview.stravaUrl },
      { label: 'YouTube', href: preview.youtubeUrl },
    ].filter((item) => item.href.trim() !== '');
  });

  constructor() {
    effect(() => {
      const profile = this.store.profile();
      if (!profile) {
        this.patchedVersion = null;
        this.form.reset(this.emptyDraft(), { emitEvent: false });
        this.form.markAsPristine();
        return;
      }
      if (profile.lockVersion === this.patchedVersion) return;
      this.patchedVersion = profile.lockVersion;
      this.form.reset(
        {
          course: profile.course ?? '',
          bio: profile.bio ?? '',
          instagramUrl: profile.instagramUrl ?? '',
          xUrl: profile.xUrl ?? '',
          linkedinUrl: profile.linkedinUrl ?? '',
          stravaUrl: profile.stravaUrl ?? '',
          youtubeUrl: profile.youtubeUrl ?? '',
        },
        { emitEvent: false },
      );
      this.form.markAsPristine();
    });

    this.form.valueChanges.pipe(takeUntilDestroyed()).subscribe(() => this.syncDraft());
    void this.store.load();
  }

  get saveDisabled(): boolean {
    return (
      this.form.invalid || this.form.pristine || this.store.isSaving() || this.store.isRemoving()
    );
  }

  fieldError(controlName: keyof AthleteProfileDraft): string {
    const control = this.form.controls[controlName];
    if (!control.touched || !control.errors) return '';
    if (control.hasError('maxlength')) {
      return controlName === 'bio'
        ? 'A biografia pode ter no maximo 500 caracteres.'
        : controlName === 'course'
          ? 'O curso pode ter no maximo 120 caracteres.'
          : 'O link pode ter no maximo 255 caracteres.';
    }
    if (control.hasError('plainText')) return 'Use somente texto simples, sem tags HTML.';
    return 'Informe uma URL HTTPS valida da plataforma indicada, sem usuario ou senha.';
  }

  isInvalid(controlName: keyof AthleteProfileDraft): boolean {
    const control = this.form.controls[controlName];
    return control.invalid && control.touched;
  }

  async save(): Promise<void> {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.store.isSaving()) return;
    this.syncDraft();
    if (await this.store.save()) this.form.markAsPristine();
  }

  async remove(): Promise<void> {
    if (!this.store.hasOptionalContent() || this.store.isRemoving()) return;
    if (!window.confirm('Remover curso, biografia e links sociais do perfil publico?')) return;
    if (await this.store.remove()) this.form.markAsPristine();
  }

  async retry(): Promise<void> {
    await this.store.load();
  }

  private syncDraft(): void {
    this.store.setDraft(this.form.getRawValue());
  }

  private emptyDraft(): AthleteProfileDraft {
    return {
      course: '',
      bio: '',
      instagramUrl: '',
      xUrl: '',
      linkedinUrl: '',
      stravaUrl: '',
      youtubeUrl: '',
    };
  }
}
