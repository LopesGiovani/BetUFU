import {
  ChangeDetectionStrategy,
  Component,
  OnInit,
  computed,
  inject,
  signal,
} from '@angular/core';
import { NgClass } from '@angular/common';
import { RouterLink } from '@angular/router';
import { firstValueFrom } from 'rxjs';
import { OperationalCapability } from '../../api/generated/model/operational-capability';
import { OperationsApiAdapter } from './operations-api.adapter';
import { OperationsPanelStore } from './operations-panel.store';

@Component({
  selector: 'app-operations-panel-page',
  standalone: true,
  imports: [NgClass, RouterLink],
  templateUrl: './operations-panel-page.component.html',
  styleUrl: './operations-panel-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OperationsPanelPageComponent implements OnInit {
  readonly chatModerationPath = '/operacao/comunidade/moderacao';
  readonly store = inject(OperationsPanelStore);
  private readonly adapter = inject(OperationsApiAdapter);

  readonly copyStatus = signal<'idle' | 'copied' | 'error'>('idle');
  readonly copyFeedbackMessage = signal<string | null>(null);
  readonly capabilities = signal<readonly string[]>([]);
  readonly canOpenSportsOperation = computed(
    () =>
      !this.store.isError() &&
      this.capabilities().includes(OperationalCapability.ManageSportsCatalog),
  );
  readonly canManagePauses = computed(() => this.capabilities().includes(OperationalCapability.ManageRoles));

  ngOnInit(): void {
    void this.store.loadPanel();
    if (!this.store.isError()) {
      void this.loadCapabilities();
    }
  }

  private async loadCapabilities(): Promise<void> {
    try {
      const capabilities = await firstValueFrom(this.adapter.fetchOperationalCapabilities());
      this.capabilities.set(capabilities);
    } catch {
      this.capabilities.set([]);
    }
  }

  async onRefresh(): Promise<void> {
    await this.store.loadPanel();
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

  formatTime(isoString: string | null | undefined): string {
    if (!isoString) {
      return 'N/D';
    }
    try {
      const date = new Date(isoString);
      if (Number.isNaN(date.getTime())) {
        return isoString;
      }
      return date.toLocaleTimeString('pt-BR', {
        hour: '2-digit',
        minute: '2-digit',
      });
    } catch {
      return isoString;
    }
  }

  formatDate(isoString: string | null | undefined): string {
    if (!isoString) {
      return 'N/D';
    }
    try {
      const date = new Date(isoString);
      if (Number.isNaN(date.getTime())) {
        return isoString;
      }
      return date.toLocaleString('pt-BR', {
        dateStyle: 'short',
        timeStyle: 'medium',
      });
    } catch {
      return isoString;
    }
  }
}
