import { HttpErrorResponse } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, defer, map } from 'rxjs';
import {
  CommitSportsTabularImportRequestParams,
  GetSportsTabularImportStatusRequestParams,
  SportsTabularImportService,
  ValidateSportsTabularImportRequestParams,
} from '../../../../api/generated/api/sports-tabular-import.service';
import { SportsTabularImportPreview } from '../../../../api/generated/model/sports-tabular-import-preview';
import { SportsTabularImportResult } from '../../../../api/generated/model/sports-tabular-import-result';

export type TabularImportFormat = 'CSV' | 'XLSX';

const MAX_FILE_BYTES = 5 * 1024 * 1024;
const isNonNegativeInteger = (value: unknown): value is number =>
  typeof value === 'number' && Number.isInteger(value) && value >= 0;
const SAFE_FIELDS = new Set([
  'arquivo',
  'categoria_slug',
  'equipe',
  'linha',
  'matricula',
  'modalidade_slug',
  'nome_atleta',
  'numero_camisa',
  'organizacao_slug',
  'posicao',
]);

@Injectable({ providedIn: 'root' })
export class SportsTabularImportApiAdapter {
  private readonly service = inject(SportsTabularImportService);

  detectFormat(file: File | null | undefined): TabularImportFormat | null {
    if (!file || file.size === 0 || file.size > MAX_FILE_BYTES) return null;
    const name = file.name.toLowerCase();
    if (name.endsWith('.csv')) return 'CSV';
    if (name.endsWith('.xlsx')) return 'XLSX';
    return null;
  }

  validate(editionId: string, file: File): Observable<SportsTabularImportPreview> {
    const request: ValidateSportsTabularImportRequestParams = { editionId, file };
    return defer(() => this.service.validateSportsTabularImport(request)).pipe(
      map((response) => this.requirePreview(response)),
    );
  }

  commit(editionId: string, importId: string): Observable<SportsTabularImportResult> {
    const request: CommitSportsTabularImportRequestParams = { editionId, importId };
    return defer(() => this.service.commitSportsTabularImport(request)).pipe(
      map((response) => this.requireResult(response)),
    );
  }

  checkStatus(editionId: string, importId: string): Observable<SportsTabularImportResult> {
    const request: GetSportsTabularImportStatusRequestParams = { editionId, importId };
    return defer(() => this.service.getSportsTabularImportStatus(request)).pipe(
      map((response) => this.requireResult(response)),
    );
  }

  isUnauthorized(error: unknown): boolean {
    return error instanceof HttpErrorResponse && (error.status === 401 || error.status === 403);
  }

  requirePreview(value: unknown): SportsTabularImportPreview {
    if (!value || typeof value !== 'object') {
      throw new Error('CONTRATO_IMPORTACAO_INVALIDO: preview ausente.');
    }
    const preview = value as Partial<SportsTabularImportPreview>;
    if (
      !(typeof preview.importId === 'string' || preview.importId === null) ||
      (preview.format !== 'CSV' && preview.format !== 'XLSX') ||
      !isNonNegativeInteger(preview.totalRows) ||
      !isNonNegativeInteger(preview.validRows) ||
      !isNonNegativeInteger(preview.errorCount) ||
      !Array.isArray(preview.errors) ||
      !Array.isArray(preview.summary) ||
      typeof preview.readyToImport !== 'boolean'
    ) {
      throw new Error('CONTRATO_IMPORTACAO_INVALIDO: preview incompleto.');
    }

    return {
      importId: preview.importId,
      format: preview.format,
      totalRows: preview.totalRows,
      validRows: preview.validRows,
      errorCount: preview.errorCount,
      errors: preview.errors.map((error) => this.safeError(error)),
      summary: preview.summary.map((item) => this.safeSummary(item)),
      readyToImport: preview.readyToImport,
    };
  }

  requireResult(value: unknown): SportsTabularImportResult {
    if (!value || typeof value !== 'object') {
      throw new Error('CONTRATO_IMPORTACAO_INVALIDO: resultado ausente.');
    }
    const result = value as Partial<SportsTabularImportResult>;
    if (
      typeof result.importId !== 'string' ||
      typeof result.status !== 'string' ||
      !isNonNegativeInteger(result.totalImportedAthletes) ||
      !isNonNegativeInteger(result.totalImportedRosters) ||
      typeof result.message !== 'string'
    ) {
      throw new Error('CONTRATO_IMPORTACAO_INVALIDO: resultado incompleto.');
    }
    return {
      importId: result.importId,
      status: result.status,
      totalImportedAthletes: result.totalImportedAthletes,
      totalImportedRosters: result.totalImportedRosters,
      message: this.safeResultMessage(result.status),
    };
  }

  private safeError(value: unknown): SportsTabularImportPreview['errors'][number] {
    if (!value || typeof value !== 'object') {
      throw new Error('CONTRATO_IMPORTACAO_INVALIDO: erro de preview ausente.');
    }
    const error = value as { line?: unknown; field?: unknown };
    if (
      !Number.isInteger(error.line) ||
      (error.line as number) < 1 ||
      typeof error.field !== 'string'
    ) {
      throw new Error('CONTRATO_IMPORTACAO_INVALIDO: erro de preview incompleto.');
    }
    const field = SAFE_FIELDS.has(error.field) ? error.field : 'arquivo';
    return {
      line: error.line as number,
      field,
      problem: this.safeProblem(field),
    };
  }

  private safeSummary(value: unknown): SportsTabularImportPreview['summary'][number] {
    if (!value || typeof value !== 'object') {
      throw new Error('CONTRATO_IMPORTACAO_INVALIDO: resumo ausente.');
    }
    const summary = value as Partial<SportsTabularImportPreview['summary'][number]>;
    if (
      typeof summary.organizationName !== 'string' ||
      typeof summary.teamName !== 'string' ||
      typeof summary.sportName !== 'string' ||
      typeof summary.categoryName !== 'string' ||
      !isNonNegativeInteger(summary.athleteCount)
    ) {
      throw new Error('CONTRATO_IMPORTACAO_INVALIDO: resumo incompleto.');
    }
    return {
      organizationName: summary.organizationName.trim(),
      teamName: summary.teamName.trim(),
      sportName: summary.sportName.trim(),
      categoryName: summary.categoryName.trim(),
      athleteCount: summary.athleteCount,
    };
  }

  private safeProblem(field: string): string {
    if (field === 'matricula') return 'Matrícula inválida conforme o template.';
    if (field === 'equipe' || field.endsWith('_slug'))
      return 'Referência não encontrada no escopo autorizado.';
    if (field === 'numero_camisa') return 'Número de camisa inválido.';
    if (field === 'posicao') return 'Posição inválida.';
    if (field === 'linha') return 'Linha duplicada.';
    return 'Valor inválido conforme o template.';
  }

  private safeResultMessage(status: string): string {
    if (status === 'CONCLUIDO') return 'Importação concluída como rascunho.';
    if (status === 'VALIDADO_AGUARDANDO_CONFIRMACAO')
      return 'Validação concluída. Confirmação pendente.';
    return 'Estado da importação consultado.';
  }
}
