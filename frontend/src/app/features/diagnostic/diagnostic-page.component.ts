import { Component, OnInit, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { DiagnosticStore } from './diagnostic.store';

@Component({
  selector: 'app-diagnostic-page',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './diagnostic-page.component.html',
  styleUrl: './diagnostic-page.component.scss',
})
export class DiagnosticPageComponent implements OnInit {
  readonly store = inject(DiagnosticStore);

  readonly copyStatus = signal<'idle' | 'copied' | 'error'>('idle');
  readonly copyFeedbackMessage = signal<string | null>(null);

  ngOnInit(): void {
    void this.store.loadDiagnostic();
  }

  async onRefresh(): Promise<void> {
    await this.store.loadDiagnostic();
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
