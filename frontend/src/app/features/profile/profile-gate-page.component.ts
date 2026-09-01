import {
  Component,
  ElementRef,
  AfterViewInit,
  OnInit,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';
import { ProfileStore } from './profile.store';

@Component({
  selector: 'app-profile-gate-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './profile-gate-page.component.html',
  styleUrl: './profile-gate-page.component.scss',
})
export class ProfileGatePageComponent implements OnInit, AfterViewInit {
  readonly store = inject(ProfileStore);
  private readonly router = inject(Router);

  @ViewChild('nicknameInput') nicknameInput?: ElementRef<HTMLInputElement>;

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

  ngOnInit(): void {
    void this.store.loadProfile();
  }

  ngAfterViewInit(): void {
    this.nicknameInput?.nativeElement.focus();
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

  async onSubmit(): Promise<void> {
    if (this.store.isSaving()) {
      return;
    }

    this.isSubmitted.set(true);

    if (this.form.invalid) {
      this.nicknameInput?.nativeElement?.focus();
      return;
    }

    const nicknameValue = this.form.controls.nickname.value.trim();
    const success = await this.store.saveNickname(nicknameValue);

    if (success) {
      this.successMessage.set('Apelido salvo. Bem-vindo à BETUFU.');
      void this.router.navigate(['/conta/perfil']);
    } else {
      // D-16 / UI Consideration partial: o valor digitado permanece no formulário
    }
  }

  async copyCorrelationId(correlationId: string): Promise<void> {
    if (!correlationId || correlationId === 'N/D') {
      return;
    }

    try {
      if (
        typeof navigator !== 'undefined' &&
        navigator.clipboard &&
        navigator.clipboard.writeText
      ) {
        await navigator.clipboard.writeText(correlationId);
        this.copyStatus.set('copied');
        this.copyFeedbackMessage.set('Código de referência copiado com sucesso.');
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
