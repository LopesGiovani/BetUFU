import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  EventEmitter,
  HostListener,
  Input,
  OnChanges,
  Output,
  SimpleChanges,
  ViewChild,
} from '@angular/core';
import { CommonModule } from '@angular/common';

/**
 * Diálogo modal acessível reutilizável (WCAG 2.2 AA, D-49, T-02-19-02, 02-UI-SPEC.md §Confirmações destrutivas).
 * Foco inicial obrigatório no botão Cancelar.
 * Focus trap dentro do modal.
 * Tecla Escape cancela e devolve o foco ao elemento disparador.
 */
@Component({
  selector: 'app-confirm-dialog',
  standalone: true,
  imports: [CommonModule],
  template: `
    @if (isOpen) {
      <div class="dialog-backdrop" (click)="onCancel()" aria-hidden="true"></div>
      <div
        #dialogContainer
        class="dialog-overlay"
        role="dialog"
        aria-modal="true"
        aria-labelledby="confirm-dialog-title"
        aria-describedby="confirm-dialog-body">
        <div class="dialog-card">
          <header class="dialog-header">
            <h2 id="confirm-dialog-title" class="dialog-title">{{ title }}</h2>
          </header>

          <div class="dialog-body">
            <p id="confirm-dialog-body" class="dialog-message">{{ body }}</p>
          </div>

          <footer class="dialog-actions">
            <button
              #cancelButton
              type="button"
              class="btn-dialog btn-cancel"
              (click)="onCancel()"
              [disabled]="isLoading">
              {{ cancelLabel }}
            </button>

            <button
              #confirmButton
              type="button"
              class="btn-dialog btn-confirm"
              [ngClass]="{ 'btn-destructive': isDestructive, 'btn-accent': !isDestructive }"
              (click)="onConfirm()"
              [disabled]="isLoading">
              @if (isLoading) {
                <span class="btn-spinner" aria-hidden="true"></span>
              }
              <span>{{ confirmLabel }}</span>
            </button>
          </footer>
        </div>
      </div>
    }
  `,
  styleUrl: './confirm-dialog.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ConfirmDialogComponent implements OnChanges {
  @Input() isOpen = false;
  @Input() title = '';
  @Input() body = '';
  @Input() confirmLabel = 'Confirmar';
  @Input() cancelLabel = 'Cancelar';
  @Input() isDestructive = false;
  @Input() isLoading = false;
  @Input() triggerElement: HTMLElement | null = null;

  @Output() readonly confirmed = new EventEmitter<void>();
  @Output() readonly cancelled = new EventEmitter<void>();

  @ViewChild('cancelButton') cancelButtonRef?: ElementRef<HTMLButtonElement>;
  @ViewChild('confirmButton') confirmButtonRef?: ElementRef<HTMLButtonElement>;
  @ViewChild('dialogContainer') dialogContainerRef?: ElementRef<HTMLDivElement>;

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['isOpen']) {
      if (this.isOpen) {
        this.focusInitialElement();
      }
    }
  }

  @HostListener('keydown', ['$event'])
  onKeyDown(event: KeyboardEvent): void {
    if (!this.isOpen) {
      return;
    }

    if (event.key === 'Escape' || event.key === 'Esc') {
      event.preventDefault();
      event.stopPropagation();
      this.onCancel();
      return;
    }

    if (event.key === 'Tab') {
      this.handleFocusTrap(event);
    }
  }

  onCancel(): void {
    this.cancelled.emit();
    this.restoreTriggerFocus();
  }

  onConfirm(): void {
    this.confirmed.emit();
  }

  private focusInitialElement(): void {
    // Foco inicial obrigatório no botão Cancelar para evitar cliques reflexivos acidentais (D-49)
    setTimeout(() => {
      if (this.cancelButtonRef?.nativeElement) {
        this.cancelButtonRef.nativeElement.focus();
      }
    }, 0);
  }

  private handleFocusTrap(event: KeyboardEvent): void {
    const cancelBtn = this.cancelButtonRef?.nativeElement;
    const confirmBtn = this.confirmButtonRef?.nativeElement;

    if (!cancelBtn || !confirmBtn) {
      return;
    }

    const focusableElements = [cancelBtn, confirmBtn].filter((el) => !el.disabled);

    if (focusableElements.length === 0) {
      return;
    }

    const firstElement = focusableElements[0];
    const lastElement = focusableElements[focusableElements.length - 1];

    if (event.shiftKey) {
      // Shift + Tab: se estiver no primeiro, pula para o último
      if (document.activeElement === firstElement) {
        event.preventDefault();
        lastElement.focus();
      }
    } else {
      // Tab: se estiver no último, pula para o primeiro
      if (document.activeElement === lastElement) {
        event.preventDefault();
        firstElement.focus();
      }
    }
  }

  private restoreTriggerFocus(): void {
    if (this.triggerElement && typeof this.triggerElement.focus === 'function') {
      setTimeout(() => {
        this.triggerElement?.focus();
      }, 0);
    }
  }
}
