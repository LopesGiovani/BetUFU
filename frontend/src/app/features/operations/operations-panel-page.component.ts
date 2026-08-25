import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { NgClass } from '@angular/common';
import { OperationsPanelStore } from './operations-panel.store';

@Component({
  selector: 'app-operations-panel-page',
  standalone: true,
  imports: [NgClass],
  templateUrl: './operations-panel-page.component.html',
  styleUrl: './operations-panel-page.component.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class OperationsPanelPageComponent implements OnInit {
  readonly store = inject(OperationsPanelStore);

  readonly copyStatus = signal<'idle' | 'copied' | 'error'>('idle');
  readonly copyFeedbackMessage = signal<string | null>(null);

  ngOnInit(): void {
    void this.store.loadPanel();
  }

  async onRefresh(): Promise<void> {
    await this.store.loadPanel();
  }

  async copyCorrelationId(correlationId: string): Promise<void> {
    if (!correlationId || correlationId === 'N/D') {
      return;
    }

    try {
      if (typeof navigator !== 'undefined' && navigator.clipboard && navigator.clipboard.writeText) {
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
