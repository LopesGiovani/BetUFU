import {
  ChangeDetectionStrategy,
  Component,
  DestroyRef,
  ElementRef,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { CommonModule } from '@angular/common';
import {
  AbstractControl,
  FormControl,
  FormGroup,
  ReactiveFormsModule,
  ValidationErrors,
} from '@angular/forms';
import { EnrollmentAdminStore } from './enrollment-admin.store';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';

const UUID_REGEX = /^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$/;

function uuidValidator(control: AbstractControl): ValidationErrors | null {
  const value = control.value;
  if (!value || typeof value !== 'string' || value.trim() === '') {
    return { required: true };
  }
  if (!UUID_REGEX.test(value.trim())) {
    return { invalidUuid: true };
  }
  return null;
}

function matriculaValidator(control: AbstractControl): ValidationErrors | null {
  const value = control.value;
  if (!value || typeof value !== 'string' || value.trim() === '') {
    return { required: true };
  }
  const normalized = value
    .trim()
    .toUpperCase()
    .replace(/[\s\-./]+/g, '');
  if (!/^[A-Z0-9]{5,20}$/.test(normalized)) {
    return { invalidPattern: true };
  }
  return null;
}

function reasonValidator(control: AbstractControl): ValidationErrors | null {
  const value = control.value;
  if (!value || typeof value !== 'string' || value.trim() === '') {
    return { required: true };
  }
  if (value.trim().length < 10) {
    return { minLength: true };
  }
  return null;
}

@Component({
  selector: 'app-enrollment-admin-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ConfirmDialogComponent],
  templateUrl: './enrollment-admin-page.component.html',
  styleUrl: './enrollment-admin-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class EnrollmentAdminPageComponent {
  readonly store = inject(EnrollmentAdminStore);
  private readonly destroyRef = inject(DestroyRef);

  @ViewChild('targetIdentityInput')
  targetIdentityInputRef?: ElementRef<HTMLInputElement>;

  @ViewChild('matriculaInput')
  matriculaInputRef?: ElementRef<HTMLInputElement>;

  @ViewChild('reasonInput')
  reasonInputRef?: ElementRef<HTMLTextAreaElement>;

  readonly form = new FormGroup({
    targetIdentityId: new FormControl('', {
      nonNullable: true,
      validators: [uuidValidator],
    }),
    matricula: new FormControl('', {
      nonNullable: true,
      validators: [matriculaValidator],
    }),
    reason: new FormControl('', {
      nonNullable: true,
      validators: [reasonValidator],
    }),
  });

  readonly isFormSubmitted = signal<boolean>(false);

  // Estados do diálogo modal de confirmação
  readonly isConfirmDialogOpen = signal<boolean>(false);
  readonly confirmDialogTitle = signal<string>('');
  readonly confirmDialogBody = signal<string>('');
  dialogTriggerElement: HTMLElement | null = null;

  // Estados de cópia de código de referência
  readonly copyStatus = signal<'idle' | 'copied' | 'error'>('idle');
  readonly copyFeedbackMessage = signal<string | null>(null);

  constructor() {
    this.destroyRef.onDestroy(() => this.store.reset());

    this.form.controls.targetIdentityId.valueChanges
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe((value) => {
        this.store.setTargetIdentityId(value);
      });
  }

  get targetIdentityErrorMessage(): string {
    const storeError = this.store.fieldErrors().targetIdentityId;
    if (storeError) {
      return storeError;
    }
    const control = this.form.controls.targetIdentityId;
    if ((this.isFormSubmitted() || control.touched) && control.invalid) {
      return 'Este identificador não tem o formato esperado. Copie-o do perfil da pessoa.';
    }
    return '';
  }

  get matriculaErrorMessage(): string {
    const storeError = this.store.fieldErrors().matricula;
    if (storeError) {
      return storeError;
    }
    const control = this.form.controls.matricula;
    if ((this.isFormSubmitted() || control.touched) && control.invalid) {
      if (control.hasError('required')) {
        return 'Informe o número da matrícula.';
      }
      return 'Este número não corresponde ao formato de matrícula da UFU. Confira e digite novamente.';
    }
    return '';
  }

  get reasonErrorMessage(): string {
    const storeError = this.store.fieldErrors().reason;
    if (storeError) {
      return storeError;
    }
    const control = this.form.controls.reason;
    if ((this.isFormSubmitted() || control.touched) && control.invalid) {
      if (control.hasError('required')) {
        return 'Informe o motivo da correção.';
      }
      if (control.hasError('minLength')) {
        return 'Descreva o motivo com mais detalhe. Ele precisa fazer sentido para quem ler a auditoria depois.';
      }
    }
    return '';
  }

  get isSubmitDisabled(): boolean {
    if (this.store.isSubmitting()) {
      return true;
    }
    const resolved = this.store.resolvedTarget();
    if (!resolved) {
      return true;
    }
    const currentInput = this.form.controls.targetIdentityId.value;
    if (resolved.targetIdentityId !== currentInput) {
      return true;
    }
    if (this.form.invalid) {
      return true;
    }
    return false;
  }

  async onSearchTarget(event?: Event): Promise<void> {
    if (event) {
      event.preventDefault();
    }
    if (this.store.isResolving()) {
      return;
    }

    const rawId = this.form.controls.targetIdentityId.value;
    const success = await this.store.resolveTargetAccount(rawId);
    if (success) {
      const canonicalId = this.store.targetIdentityId();
      if (this.form.controls.targetIdentityId.value !== canonicalId) {
        this.form.controls.targetIdentityId.setValue(canonicalId, { emitEvent: false });
      }
    }
    if (!success && this.targetIdentityErrorMessage) {
      this.targetIdentityInputRef?.nativeElement.focus();
    }
  }

  openConfirmation(triggerBtn: HTMLElement): void {
    this.isFormSubmitted.set(true);

    const resolved = this.store.resolvedTarget();
    const currentTargetId = this.form.controls.targetIdentityId.value;

    if (!resolved || resolved.targetIdentityId !== currentTargetId) {
      this.targetIdentityInputRef?.nativeElement.focus();
      return;
    }

    if (this.form.controls.matricula.invalid) {
      this.matriculaInputRef?.nativeElement.focus();
      return;
    }

    if (this.form.controls.reason.invalid) {
      this.reasonInputRef?.nativeElement.focus();
      return;
    }

    const nickname = resolved.nickname || resolved.targetIdentityId || 'esta pessoa';
    this.dialogTriggerElement = triggerBtn;
    this.confirmDialogTitle.set(`Corrigir a matrícula de ${nickname}?`);
    this.confirmDialogBody.set(
      'A matrícula registrada hoje é liberada e substituída pela nova. Não há como desfazer sozinho — seria preciso outra correção. O motivo informado fica na auditoria.',
    );
    this.isConfirmDialogOpen.set(true);
  }

  async onDialogConfirmed(): Promise<void> {
    if (this.store.isSubmitting()) {
      return;
    }

    this.isConfirmDialogOpen.set(false);

    const targetId = this.form.controls.targetIdentityId.value;
    const resolved = this.store.resolvedTarget();
    if (!resolved || resolved.targetIdentityId !== targetId) {
      this.restoreTriggerFocus();
      return;
    }

    const matricula = this.form.controls.matricula.value;
    const reason = this.form.controls.reason.value;

    const success = await this.store.correctEnrollment(targetId, matricula, reason);

    if (success) {
      // Sucesso: limpa campos de mutação
      this.form.controls.matricula.reset();
      this.form.controls.reason.reset();
      this.isFormSubmitted.set(false);
    } else {
      // Recusa: mantém os valores digitados na memória do form/store
      this.restoreTriggerFocus();
    }
  }

  onDialogCancelled(): void {
    this.isConfirmDialogOpen.set(false);
    this.restoreTriggerFocus();
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
        throw new Error('Clipboard indisponível');
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

  private restoreTriggerFocus(): void {
    setTimeout(() => {
      if (
        this.dialogTriggerElement?.isConnected &&
        !this.dialogTriggerElement.hasAttribute('disabled')
      ) {
        this.dialogTriggerElement.focus();
      }
    }, 0);
  }
}
