import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';
import { SportsTabularImportPreview } from '../../../../api/generated/model/sports-tabular-import-preview';
import { SportsTabularImportResult } from '../../../../api/generated/model/sports-tabular-import-result';
import { SportsTabularImportApiAdapter, TabularImportFormat } from './sports-tabular-import-api.adapter';

export type SportsTabularImportStatus =
  | 'initial'
  | 'selected'
  | 'validating'
  | 'preview'
  | 'committing'
  | 'indeterminate'
  | 'completed'
  | 'error'
  | 'denied';

interface TabularImportState {
  status: SportsTabularImportStatus;
  file: File | null;
  fileName: string | null;
  format: TabularImportFormat | null;
  preview: SportsTabularImportPreview | null;
  result: SportsTabularImportResult | null;
  errorMessage: string | null;
  feedbackMessage: string | null;
  commitAttempted: boolean;
}

const INITIAL_STATE: TabularImportState = {
  status: 'initial',
  file: null,
  fileName: null,
  format: null,
  preview: null,
  result: null,
  errorMessage: null,
  feedbackMessage: null,
  commitAttempted: false,
};

@Injectable({ providedIn: 'root' })
export class SportsTabularImportStore {
  private readonly adapter = inject(SportsTabularImportApiAdapter);
  private readonly state = signal<TabularImportState>(INITIAL_STATE);

  readonly status = computed(() => this.state().status);
  readonly fileName = computed(() => this.state().fileName);
  readonly format = computed(() => this.state().format);
  readonly preview = computed(() => this.state().preview);
  readonly result = computed(() => this.state().result);
  readonly errorMessage = computed(() => this.state().errorMessage);
  readonly feedbackMessage = computed(() => this.state().feedbackMessage);
  readonly isLoading = computed(() => this.status() === 'validating' || this.status() === 'committing');
  readonly isDenied = computed(() => this.status() === 'denied');
  readonly isError = computed(() => this.status() === 'error');
  readonly isIndeterminate = computed(() => this.status() === 'indeterminate');
  readonly hasFile = computed(() => this.state().file !== null);
  readonly hasPreview = computed(() => this.preview() !== null);
  readonly canValidate = computed(() => this.hasFile() && !this.isLoading() && !this.isIndeterminate());
  readonly canCommit = computed(() => {
    const preview = this.preview();
    return preview?.readyToImport === true
      && typeof preview.importId === 'string'
      && !this.isLoading()
      && !this.isIndeterminate()
      && !this.state().commitAttempted;
  });
  readonly canCheckStatus = computed(() => {
    const importId = this.preview()?.importId ?? this.result()?.importId;
    return typeof importId === 'string' && (this.isIndeterminate() || this.status() === 'preview');
  });

  selectFile(file: File | null | undefined): boolean {
    const format = this.adapter.detectFormat(file);
    if (!file || !format) {
      this.state.set({
        ...INITIAL_STATE,
        status: 'error',
        errorMessage: 'Selecione um arquivo CSV UTF-8 ou XLSX OOXML dentro do limite de 5 MB.',
      });
      return false;
    }

    this.state.set({
      ...INITIAL_STATE,
      status: 'selected',
      file,
      fileName: file.name,
      format,
    });
    return true;
  }

  async validate(editionId: string): Promise<boolean> {
    const file = this.state().file;
    if (!file || !editionId || this.isIndeterminate()) return false;
    this.state.update((state) => ({
      ...state,
      status: 'validating',
      errorMessage: null,
      feedbackMessage: null,
      result: null,
      commitAttempted: false,
    }));
    try {
      const preview = await firstValueFrom(this.adapter.validate(editionId, file));
      this.state.update((state) => ({
        ...state,
        status: 'preview',
        preview,
        errorMessage: null,
        feedbackMessage: preview.readyToImport
          ? 'Arquivo validado. Revise o resumo antes de confirmar a importação.'
          : 'O arquivo contém erros. Corrija a planilha e selecione uma nova versão.',
      }));
      return true;
    } catch (error: unknown) {
      this.handleError(error);
      return false;
    }
  }

  async commit(editionId: string): Promise<boolean> {
    const importId = this.preview()?.importId;
    if (!importId || !editionId || !this.canCommit()) return false;
    this.state.update((state) => ({
      ...state,
      status: 'committing',
      errorMessage: null,
      feedbackMessage: null,
      commitAttempted: true,
    }));
    try {
      const result = await firstValueFrom(this.adapter.commit(editionId, importId));
      this.state.set({
        ...INITIAL_STATE,
        status: 'completed',
        result,
        feedbackMessage: 'Importação concluída. Os registros foram criados como rascunho.',
      });
      return true;
    } catch (error: unknown) {
      if (this.adapter.isUnauthorized(error)) {
        this.clearPrivateState('denied');
      } else if (this.isTransportFailure(error)) {
        this.state.update((state) => ({
          ...state,
          status: 'indeterminate',
          errorMessage: 'A resposta da confirmação não chegou. Não reenvie o arquivo; consulte o estado no servidor.',
        }));
      } else {
        this.state.update((state) => ({
          ...state,
          status: 'error',
          errorMessage: 'Não foi possível confirmar a importação. Consulte o estado antes de tentar qualquer ação.',
        }));
      }
      return false;
    }
  }

  async checkStatus(editionId: string): Promise<boolean> {
    const importId = this.preview()?.importId ?? this.result()?.importId;
    if (!importId || !editionId || this.isLoading()) return false;
    this.state.update((state) => ({ ...state, errorMessage: null, feedbackMessage: null }));
    try {
      const result = await firstValueFrom(this.adapter.checkStatus(editionId, importId));
      if (result.status === 'CONCLUIDO') {
        this.state.set({
          ...INITIAL_STATE,
          status: 'completed',
          result,
          feedbackMessage: 'O servidor confirmou a importação como concluída.',
        });
      } else {
        this.state.update((state) => ({
          ...state,
          status: 'indeterminate',
          result,
          feedbackMessage: 'A importação ainda aguarda confirmação. Nenhum reenvio foi feito.',
        }));
      }
      return true;
    } catch (error: unknown) {
      this.handleError(error);
      return false;
    }
  }

  reset(): void {
    this.state.set(INITIAL_STATE);
  }

  private handleError(error: unknown): void {
    if (this.adapter.isUnauthorized(error)) {
      this.clearPrivateState('denied');
      return;
    }
    this.state.update((state) => ({
      ...state,
      status: 'error',
      errorMessage: 'Não foi possível validar ou consultar a importação agora. Nenhum dado bruto foi exibido.',
    }));
  }

  private clearPrivateState(status: 'denied'): void {
    this.state.set({
      ...INITIAL_STATE,
      status,
      errorMessage: 'Seu acesso foi encerrado. O arquivo, o preview e o identificador privado foram removidos da memória.',
    });
  }

  private isTransportFailure(error: unknown): boolean {
    return error instanceof HttpErrorResponse && error.status === 0;
  }
}
