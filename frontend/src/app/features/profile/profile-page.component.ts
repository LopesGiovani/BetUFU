import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnInit,
  ViewChild,
  computed,
  effect,
  inject,
  signal,
} from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { ProfileStore } from './profile.store';

@Component({
  selector: 'app-profile-page',
  standalone: true,
  imports: [ReactiveFormsModule, RouterLink],
  templateUrl: './profile-page.component.html',
  styleUrl: './profile-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ProfilePageComponent implements OnInit {
  readonly store = inject(ProfileStore);

  @ViewChild('nicknameInput') nicknameInput?: ElementRef<HTMLInputElement>;
  @ViewChild('identityValueRef') identityValueRef?: ElementRef<HTMLElement>;

  readonly form = new FormGroup({
    nickname: new FormControl('', {
      nonNullable: true,
      validators: [
        Validators.required,
        Validators.minLength(3),
        Validators.maxLength(20),
        Validators.pattern(/^[A-Za-z0-9_-]{3,20}$/),
      ],
    }),
  });

  readonly isSubmitted = signal<boolean>(false);
  readonly successMessage = signal<string | null>(null);
  readonly copyStatus = signal<'idle' | 'copied' | 'error'>('idle');
  readonly copyFeedbackMessage = signal<string | null>(null);
  readonly favoriteForm = new FormGroup({
    organizationId: new FormControl('', { nonNullable: true }),
  });

  readonly isMatriculaRegistrada = computed(() => {
    return this.store.displayProfile()?.matriculaStatus === 'REGISTRADA';
  });

  constructor() {
    effect(() => {
      const currentNickname = this.store.nickname();
      if (currentNickname !== null && this.form.controls.nickname.pristine) {
        this.form.controls.nickname.setValue(currentNickname, { emitEvent: false });
      }
      const favoriteOrganizationId = this.store.favoriteOrganizationId() ?? '';
      if (this.favoriteForm.controls.organizationId.pristine) {
        this.favoriteForm.controls.organizationId.setValue(favoriteOrganizationId, {
          emitEvent: false,
        });
      }
    });
  }

  ngOnInit(): void {
    void this.store.loadProfile();
    void this.store.loadFavoriteOrganizations();
  }

  async onRetry(): Promise<void> {
    await this.store.loadProfile();
  }

  get nicknameLength(): number {
    return this.form.controls.nickname.value?.length ?? 0;
  }

  get hasFieldError(): boolean {
    const control = this.form.controls.nickname;
    return (this.isSubmitted() || control.touched) && control.invalid;
  }

  get fieldErrorMessage(): string {
    const control = this.form.controls.nickname;
    if (!this.hasFieldError) {
      return '';
    }
    if (control.hasError('required')) {
      return 'Informe um apelido para continuar.';
    }
    return 'Use de 3 a 20 caracteres, apenas letras sem acento, números, hífen (-) ou sublinhado (_).';
  }

  get isSubmitDisabled(): boolean {
    if (this.store.isSaving()) {
      return true;
    }
    if (this.form.invalid) {
      return true;
    }
    const currentNickname = this.store.nickname() ?? '';
    const trimmedInput = this.form.controls.nickname.value.trim();
    if (this.form.pristine || trimmedInput === currentNickname) {
      return true;
    }
    return false;
  }

  async onSubmit(): Promise<void> {
    if (this.store.isSaving()) {
      return;
    }

    this.isSubmitted.set(true);

    if (this.form.invalid) {
      this.nicknameInput?.nativeElement?.focus();
      return;
    }

    const currentNickname = this.store.nickname() ?? '';
    const nicknameValue = this.form.controls.nickname.value.trim();
    if (this.form.pristine || nicknameValue === currentNickname) {
      return;
    }

    const success = await this.store.saveNickname(nicknameValue);

    if (success) {
      this.form.controls.nickname.markAsPristine();
      this.successMessage.set('Perfil atualizado.');
      setTimeout(() => {
        this.successMessage.set(null);
      }, 4000);
    } else {
      // D-16 / UI Consideration partial: o valor digitado permanece no formulário
    }
  }

  async onSaveFavorite(): Promise<void> {
    const organizationId = this.favoriteForm.controls.organizationId.value || null;
    if (!organizationId || this.store.isSavingFavorite()) return;
    if (await this.store.saveFavoriteOrganization(organizationId)) {
      this.favoriteForm.markAsPristine();
      this.successMessage.set('Organizacao favorita atualizada.');
    }
  }

  async onRemoveFavorite(): Promise<void> {
    const name = this.store.favoriteOrganizationName() ?? 'esta organizacao';
    if (
      this.store.isSavingFavorite() ||
      !window.confirm(`Remover ${name} dos favoritos? Voce podera escolher novamente depois.`)
    )
      return;
    if (await this.store.saveFavoriteOrganization(null)) {
      this.favoriteForm.reset({ organizationId: '' });
      this.favoriteForm.markAsPristine();
      this.successMessage.set('Organizacao favorita removida.');
    }
  }

  async copyIdentityId(): Promise<void> {
    const identityId = this.store.identityId();
    if (!identityId || identityId === '—') {
      return;
    }

    try {
      if (
        typeof navigator !== 'undefined' &&
        navigator.clipboard &&
        typeof navigator.clipboard.writeText === 'function'
      ) {
        await navigator.clipboard.writeText(identityId);
        this.copyStatus.set('copied');
        this.copyFeedbackMessage.set('Identificador copiado.');
      } else {
        throw new Error('Clipboard API indisponível');
      }
    } catch {
      this.copyStatus.set('error');
      this.copyFeedbackMessage.set(
        'Não foi possível copiar. Selecione o identificador e copie novamente.',
      );
      const element = this.identityValueRef?.nativeElement;
      if (element) {
        element.focus();
        if (typeof window !== 'undefined' && typeof document !== 'undefined') {
          const selection = window.getSelection();
          if (selection) {
            const range = document.createRange();
            range.selectNodeContents(element);
            selection.removeAllRanges();
            selection.addRange(range);
          }
        }
      }
    }

    setTimeout(() => {
      this.copyStatus.set('idle');
      this.copyFeedbackMessage.set(null);
    }, 4000);
  }

  async copyCorrelationId(correlationId: string): Promise<void> {
    if (!correlationId || correlationId === 'N/D') {
      return;
    }

    try {
      if (
        typeof navigator !== 'undefined' &&
        navigator.clipboard &&
        typeof navigator.clipboard.writeText === 'function'
      ) {
        await navigator.clipboard.writeText(correlationId);
        this.copyStatus.set('copied');
        this.copyFeedbackMessage.set('Código de referência copiado.');
      } else {
        throw new Error('Clipboard API indisponível');
      }
    } catch {
      this.copyStatus.set('error');
      this.copyFeedbackMessage.set(
        `Não foi possível copiar automaticamente. Selecione e copie o código: ${correlationId}`,
      );
    }

    setTimeout(() => {
      this.copyStatus.set('idle');
      this.copyFeedbackMessage.set(null);
    }, 4000);
  }
}
