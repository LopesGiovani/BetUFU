import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnInit,
  ViewChild,
  computed,
  inject,
  signal,
} from '@angular/core';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
} from '@angular/forms';
import { EnrollmentStore } from './enrollment.store';

function matriculaValidator(control: AbstractControl): ValidationErrors | null {
  const value = control.value;
  if (!value || typeof value !== 'string' || value.trim() === '') {
    return { required: true };
  }
  const normalized = value.trim().toUpperCase().replace(/[\s\-./]+/g, '');
  if (!/^[A-Z0-9]{5,20}$/.test(normalized)) {
    return { invalidPattern: true };
  }
  return null;
}

@Component({
  selector: 'app-enrollment-page',
  standalone: true,
  imports: [ReactiveFormsModule],
  templateUrl: './enrollment-page.component.html',
  styleUrl: './enrollment-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EnrollmentPageComponent implements OnInit {
  readonly store = inject(EnrollmentStore);

  @ViewChild('matriculaInput') matriculaInput?: ElementRef<HTMLInputElement>;

  readonly form = new FormGroup({
    matricula: new FormControl('', {
      nonNullable: true,
      validators: [matriculaValidator],
    }),
  });

  readonly isSubmitted = signal<boolean>(false);
  readonly successMessage = signal<string | null>(null);
  readonly copyStatus = signal<'idle' | 'copied' | 'error'>('idle');
  readonly copyFeedbackMessage = signal<string | null>(null);

  readonly isMatriculaRegistrada = computed(() => {
    return this.store.isRegistered();
  });

  ngOnInit(): void {
    void this.store.loadEnrollment();
  }

  async onRetry(): Promise<void> {
    await this.store.loadEnrollment();
  }

  get hasFieldError(): boolean {
    const control = this.form.controls.matricula;
    return (this.isSubmitted() || control.touched) && control.invalid;
  }

  get fieldErrorMessage(): string {
    const control = this.form.controls.matricula;
    if (!this.hasFieldError) {
      return '';
    }
    if (control.hasError('required')) {
      return 'Informe o número da matrícula.';
    }
    return 'Este número não corresponde ao formato de matrícula da UFU. Confira e digite novamente.';
  }

  get isSubmitDisabled(): boolean {
    if (this.store.isSaving()) {
      return true;
    }
    if (this.form.invalid) {
      return true;
    }
    const raw = this.form.controls.matricula.value?.trim();
    if (this.form.pristine || !raw) {
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
      this.matriculaInput?.nativeElement?.focus();
      return;
    }

    const rawValue = this.form.controls.matricula.value.trim();
    if (this.form.pristine || !rawValue) {
      return;
    }

    const success = await this.store.registerMatricula(rawValue);

    if (success) {
      // D-16 / PRIV-01: Limpar o campo de matrícula no sucesso
      this.form.reset();
      this.form.controls.matricula.markAsPristine();
      this.isSubmitted.set(false);
      this.successMessage.set(
        'Matrícula registrada. O número não fica guardado nem aparece em nenhuma tela.',
      );
    } else {
      // D-16 / UI Consideration partial: o valor digitado permanece no formulário em recusa
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
