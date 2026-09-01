import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  OnInit,
  ViewChild,
  inject,
  signal,
} from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { RoleAdminStore } from './role-admin.store';
import { ProfileStore } from '../profile/profile.store';
import { SessionStore } from '../../core/session/session.store';
import { ConfirmDialogComponent } from '../../shared/confirm-dialog/confirm-dialog.component';
import { OperationalRole } from '../../api/generated/model/operational-role';
import { RoleItemViewModel } from './operations.models';

@Component({
  selector: 'app-role-admin-page',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, ConfirmDialogComponent],
  templateUrl: './role-admin-page.component.html',
  styleUrl: './role-admin-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RoleAdminPageComponent implements OnInit {
  private readonly hostElement = inject<ElementRef<HTMLElement>>(ElementRef);

  readonly store = inject(RoleAdminStore);
  readonly profileStore = inject(ProfileStore);
  readonly sessionStore = inject(SessionStore);

  readonly searchControl = new FormControl('', { nonNullable: true });

  @ViewChild('targetIdentityInput')
  private searchInputRef?: ElementRef<HTMLInputElement>;

  // Estados do Diálogo Modal de Confirmação
  readonly isConfirmDialogOpen = signal<boolean>(false);
  readonly confirmDialogTitle = signal<string>('');
  readonly confirmDialogBody = signal<string>('');
  readonly confirmDialogActionLabel = signal<string>('Confirmar');
  readonly confirmDialogIsDestructive = signal<boolean>(false);
  readonly confirmActionType = signal<'grant' | 'revoke' | null>(null);
  readonly confirmActionTargetRole = signal<
    'ADMINISTRADOR_GERAL' | 'ADMINISTRADOR_COMPETICAO' | 'MODERADOR' | null
  >(null);
  dialogTriggerElement: HTMLElement | null = null;

  // Estados de cópia de código de referência
  readonly copyStatus = signal<'idle' | 'copied' | 'error'>('idle');
  readonly copyFeedbackMessage = signal<string | null>(null);

  async ngOnInit(): Promise<void> {
    // Sincroniza a identidade do ator logado para que as guardas D-48 funcionem no cliente
    let actorId = this.profileStore.identityId();
    if (!actorId) {
      await this.profileStore.loadProfile();
      actorId = this.profileStore.identityId();
    }
    this.store.setActorContext(actorId);
  }

  async onSearchSubmit(event?: Event): Promise<void> {
    if (event) {
      event.preventDefault();
    }
    if (this.store.isSearching()) {
      return;
    }
    const success = await this.store.searchAccount(this.searchControl.value);
    if (!success && this.store.fieldError()) {
      this.searchInputRef?.nativeElement.focus();
    }
  }

  openGrantConfirmation(roleItem: RoleItemViewModel, triggerBtn: HTMLElement): void {
    if (!roleItem.isActionable || roleItem.selfGrantBlocked) {
      return;
    }

    this.dialogTriggerElement = triggerBtn;
    const nickname = this.store.targetNickname() || this.store.targetIdentityId() || 'esta pessoa';

    this.confirmDialogTitle.set(`Conceder administrador geral a ${nickname}?`);
    this.confirmDialogBody.set(
      'A pessoa passa a ter esse papel imediatamente e todas as sessões dela são encerradas. A ação fica registrada na auditoria.',
    );
    this.confirmDialogActionLabel.set('Conceder papel');
    this.confirmDialogIsDestructive.set(false);
    this.confirmActionType.set('grant');
    this.confirmActionTargetRole.set(roleItem.role);
    this.isConfirmDialogOpen.set(true);
  }

  openRevokeConfirmation(roleItem: RoleItemViewModel, triggerBtn: HTMLElement): void {
    if (!roleItem.isActionable) {
      return;
    }

    this.dialogTriggerElement = triggerBtn;
    const actorId = this.store.actorIdentityId();
    const targetId = this.store.targetIdentityId();
    const isSelf = Boolean(actorId && actorId === targetId);

    if (isSelf) {
      this.confirmDialogTitle.set('Revogar seu papel de administrador geral?');
      this.confirmDialogBody.set(
        'Você perde o papel imediatamente e sua sessão é encerrada. Só outro administrador geral poderá devolvê-lo.',
      );
      this.confirmDialogActionLabel.set('Revogar meu papel');
    } else {
      const nickname = this.store.targetNickname() || targetId || 'esta pessoa';
      this.confirmDialogTitle.set(`Revogar administrador geral de ${nickname}?`);
      this.confirmDialogBody.set(
        'A pessoa perde esse papel imediatamente e todas as sessões dela são encerradas. A ação fica registrada na auditoria.',
      );
      this.confirmDialogActionLabel.set('Revogar papel');
    }

    this.confirmDialogIsDestructive.set(true);
    this.confirmActionType.set('revoke');
    this.confirmActionTargetRole.set(roleItem.role);
    this.isConfirmDialogOpen.set(true);
  }

  async onDialogConfirmed(): Promise<void> {
    if (this.store.isMutating()) {
      return;
    }

    this.isConfirmDialogOpen.set(false);
    const action = this.confirmActionType();
    const role = this.confirmActionTargetRole();

    if (!action || !role) {
      this.restoreActionFocus();
      return;
    }

    if (action === 'grant') {
      await this.store.grantRole(role);
    } else if (action === 'revoke') {
      const isSelf = Boolean(
        this.store.actorIdentityId() &&
        this.store.actorIdentityId() === this.store.targetIdentityId(),
      );
      const success = await this.store.revokeRole(role);
      if (success && isSelf) {
        // A revogação do próprio papel encerra a sessão do ator imediatamente
        await this.sessionStore.loadSession();
      }
    }

    this.confirmActionType.set(null);
    this.confirmActionTargetRole.set(null);
    this.restoreActionFocus();
  }

  onDialogCancelled(): void {
    this.isConfirmDialogOpen.set(false);
    this.confirmActionType.set(null);
    this.confirmActionTargetRole.set(null);
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

  private restoreActionFocus(): void {
    setTimeout(() => {
      const originalTrigger = this.dialogTriggerElement;
      if (originalTrigger?.isConnected && !originalTrigger.hasAttribute('disabled')) {
        originalTrigger.focus();
        return;
      }

      const replacement = this.hostElement.nativeElement.querySelector<HTMLButtonElement>(
        '.btn-grant:not(:disabled), .btn-revoke:not(:disabled)',
      );
      replacement?.focus();
    }, 0);
  }
}
